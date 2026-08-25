import fs from 'node:fs/promises';
import path from 'node:path';
import { BraveWebDiscoveryCollector, GoogleWebDiscoveryCollector, SearXNGWebDiscoveryCollector } from './platforms.js';

const HEALTH_QUERY='"can\'t find an old photo"';
const COOLDOWN_MS=5*60*1000;
const CACHE_TTL_MS=15*60*1000;
const splitEngines=value=>String(value||'').split(',').map(x=>x.trim()).filter(Boolean);
const upstreamNames=payload=>[...new Set((payload.results||[]).flatMap(x=>x.engines||x.engine||[]).filter(Boolean))];
const blockedNames=payload=>(payload.unresponsive_engines||[]).map(x=>Array.isArray(x)?String(x[0]):String(x));

export class PublicSearchCache {
  constructor(file,{now=()=>Date.now(),ttlMs=CACHE_TTL_MS}={}){this.file=file;this.now=now;this.ttlMs=ttlMs;}
  async read(){try{return JSON.parse(await fs.readFile(this.file,'utf8'));}catch{return{entries:{},cooldowns:{},lastSuccessfulSearch:null};}}
  async write(state){await fs.mkdir(path.dirname(this.file),{recursive:true});const temporary=`${this.file}.tmp`;await fs.writeFile(temporary,JSON.stringify(state,null,2));await fs.rename(temporary,this.file);}
  async cooldown(engine){const state=await this.read();return Number(state.cooldowns?.[engine]||0)>this.now();}
  async markCooldown(engine){const state=await this.read();state.cooldowns={...state.cooldowns,[engine]:this.now()+COOLDOWN_MS};await this.write(state);}
  async save(query,provider,items){const state=await this.read();const timestamp=new Date(this.now()).toISOString();state.entries={...state.entries,[query]:{timestamp,provider,items:items.slice(0,20)}};state.lastSuccessfulSearch=timestamp;await this.write(state);}
  async get(query){const state=await this.read();const entry=state.entries?.[query];if(!entry)return null;const ageMs=this.now()-new Date(entry.timestamp).getTime();return ageMs<=this.ttlMs?{...entry,ageMs}:null;}
  async summary(){const state=await this.read();const entries=Object.values(state.entries||{});const current=entries.filter(x=>this.now()-new Date(x.timestamp).getTime()<=this.ttlMs);return{lastSuccessfulSearch:state.lastSuccessfulSearch||null,cachedResults:current.reduce((n,x)=>n+(x.items?.length||0),0)};}
}

export class ResilientWebDiscoveryCollector {
  constructor(settings={},fetchImpl=fetch,{now=()=>Date.now()}={}){
    this.name='Web discovery';this.fetch=fetchImpl;this.settings=settings;this.zeroCostMode=settings.zeroCostMode!==false;this.engines=splitEngines(settings.searxng?.engines);this.searxng=new SearXNGWebDiscoveryCollector(settings.searxng,fetchImpl);this.google=new GoogleWebDiscoveryCollector(settings.webSearch,fetchImpl);this.brave=new BraveWebDiscoveryCollector(this.zeroCostMode?{}:settings.braveSearch,fetchImpl);this.cache=new PublicSearchCache(path.join(settings.dataDir||'.','discovery-web-cache.json'),{now});this.configured=Boolean(this.searxng.configured||this.google.configured||this.brave.configured);this.lastStatus=null;
  }
  report(){return{platform:this.name,status:this.configured?'PUBLIC_WEB':'MANUAL_ASSIST',dataType:this.configured?'PUBLIC_WEB_SEARCH':'NONE',autoPublish:false,...this.lastStatus};}
  async requestSearx(query,engine){
    const config={...this.settings.searxng,...(engine?{engines:engine}:{})};const collector=new SearXNGWebDiscoveryCollector(config,this.fetch);return collector.collectPublicOpportunities({query});
  }
  async health(){
    if(!this.searxng.configured)return{searxng:'DOWN',workingUpstreams:[],blockedUpstreams:[]};
    const candidates=this.engines.length?this.engines:[null];const working=[];const blocked=[];let reachable=false;
    for(const engine of candidates){if(engine&&await this.cache.cooldown(engine)){blocked.push(engine);continue;}try{const items=await this.requestSearx(HEALTH_QUERY,engine);reachable=true;if(items.length){const reported=items.flatMap(x=>x.searchEngines||[]);working.push(...(engine?[engine]:reported.length?reported:['default']));}else if(engine){blocked.push(engine);await this.cache.markCooldown(engine);}}catch{if(engine){blocked.push(engine);await this.cache.markCooldown(engine);}}
    }
    return{searxng:!reachable?'DOWN':working.length?'HEALTHY':'DEGRADED',workingUpstreams:[...new Set(working)],blockedUpstreams:[...new Set(blocked)]};
  }
  googleStatus(){if(this.google.configured)return'CONNECTED';if(this.settings.webSearch?.apiKey&&!this.settings.webSearch?.engineId)return'MISSING GOOGLE_SEARCH_ENGINE_ID';if(!this.settings.webSearch?.apiKey&&this.settings.webSearch?.engineId)return'MISSING GOOGLE_SEARCH_API_KEY';return'MISSING';}
  async googleHealth(){const configured=this.googleStatus();if(configured!=='CONNECTED')return configured;try{await this.google.collectPublicOpportunities({query:HEALTH_QUERY});return'CONNECTED';}catch{return'FAILED';}}
  async braveHealth(){if(!this.brave.configured)return'MISSING';try{await this.brave.collectPublicOpportunities({query:HEALTH_QUERY});return'CONNECTED';}catch{return'FAILED';}}
  async collectPublicOpportunities({query}={}){
    let items=[];let activeProvider='NONE';let google=this.googleStatus();
    if(this.google.configured){try{items=await this.google.collectPublicOpportunities({query});google='CONNECTED';if(items.length)activeProvider='GOOGLE';}catch{google='FAILED';}}
    const health=await this.health();
    if(!items.length&&health.searxng==='HEALTHY'){
      const candidates=this.engines.length?health.workingUpstreams:[null];for(const engine of candidates){try{items=await this.requestSearx(query,engine);if(items.length){activeProvider='SEARXNG';break;}}catch{if(engine)await this.cache.markCooldown(engine);}}
    }
    let otherFallback=this.zeroCostMode?'SKIPPED_ZERO_COST_MODE':this.brave.configured?'CONNECTED':'MISSING';if(!items.length&&!this.zeroCostMode&&this.brave.configured){try{items=await this.brave.collectPublicOpportunities({query});otherFallback='CONNECTED';if(items.length)activeProvider='BRAVE_SEARCH';}catch{otherFallback='FAILED';}}
    if(items.length){items=items.map(x=>({...x,providerProvenance:`PUBLIC_WEB / ${activeProvider}`}));await this.cache.save(query||'default',activeProvider,items);}
    else{const cached=await this.cache.get(query||'default');if(cached){items=cached.items.map(x=>({...x,providerProvenance:'CACHED_PUBLIC_WEB',cachedAt:cached.timestamp}));activeProvider='CACHE';}else activeProvider='MANUAL_ASSIST';}
    this.lastStatus={...health,google,otherFallback,activeProvider};return items;
  }
  async status(){const google=await this.googleHealth();const health=await this.health();const otherFallback=this.zeroCostMode?'SKIPPED_ZERO_COST_MODE':await this.braveHealth();const cache=await this.cache.summary();return{...health,google,otherFallback,activeProvider:google==='CONNECTED'?'GOOGLE':health.searxng==='HEALTHY'?'SEARXNG':otherFallback==='CONNECTED'?'BRAVE_SEARCH':cache.cachedResults?'CACHE':'MANUAL_ASSIST',...cache};}
}
