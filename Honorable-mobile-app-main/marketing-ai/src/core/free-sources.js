const decode=value=>String(value||'').replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g,'$1').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'").replace(/<[^>]+>/g,' ').replace(/&#32;/g,' ').replace(/\s+/g,' ').trim();
const tag=(body,name)=>decode(body.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${name}>`,'i'))?.[1]);
const linkOf=body=>tag(body,'link')||body.match(/<link[^>]+href=["']([^"']+)/i)?.[1]||'';
const entriesOf=xml=>[...xml.matchAll(/<(item|entry)(?:\s[^>]*)?>([\s\S]*?)<\/\1>/gi)].map(x=>x[2]);
const domainOf=value=>{try{return new URL(value).hostname.replace(/^www\./,'');}catch{return'unknown';}};
const formatOf=(xml,contentType='')=>/<feed(?:\s|>)/i.test(xml)?'ATOM':/<rss(?:\s|>)/i.test(xml)?'RSS':/atom/i.test(contentType)?'ATOM':/rss/i.test(contentType)?'RSS':'UNKNOWN';
export async function loadFeedRegistry(file){try{const entries=JSON.parse(await fs.readFile(file,'utf8'));return Array.isArray(entries)?entries.filter(x=>x.enabled&&x.url):[];}catch{return[];}}
export async function validateFeed(entry,fetchImpl=fetch){const domain=domainOf(entry.url);try{const response=await fetchImpl(entry.url,{headers:{accept:'application/rss+xml, application/atom+xml, application/xml, text/xml','user-agent':'HonorableMarketing/1.0 (public feed reader)'}});if(response.status===429)return{...entry,domain,httpStatus:429,format:'UNKNOWN',items:0,status:'RATE_LIMITED'};const body=await response.text();const format=formatOf(body,response.headers?.get?.('content-type')||'');const items=entriesOf(body).length;return{...entry,domain,httpStatus:response.status,format,items,status:response.ok&&format!=='UNKNOWN'?'WORKING':'FAILED',body};}catch(error){return{...entry,domain,httpStatus:'ERROR',format:'UNKNOWN',items:0,status:'FAILED',error:error.message};}}

class FreeCollector {
  constructor(name,configured,fetchImpl=fetch){this.name=name;this.configured=Boolean(configured);this.fetch=fetchImpl;this.lastError=null;}
  report(){return{platform:this.name,status:this.configured?'CONNECTED':'NOT_CONFIGURED',dataType:this.configured?'PUBLIC_FEED':'NONE',autoPublish:false};}
}

export class PublicFeedCollector extends FreeCollector {
  constructor({registryFile,urls=[]}={},fetchImpl=fetch){super('RSS/Forums',Boolean(registryFile||urls.length),fetchImpl);this.registryFile=registryFile;this.urls=urls;this.statuses=[];}
  async registry(){const configured=await loadFeedRegistry(this.registryFile);return[...configured,...this.urls.map((url,i)=>({name:`Custom feed ${i+1}`,platform:'FORUM',url,topic:'custom',enabled:true}))];}
  async collectPublicOpportunities(){const output=[];this.statuses=[];for(const entry of await this.registry()){const checked=await validateFeed(entry,this.fetch);this.statuses.push({...checked,body:undefined});if(checked.status!=='WORKING')continue;for(const [i,body] of entriesOf(checked.body).slice(0,30).entries()){const sourceUrl=linkOf(body);output.push({id:`feed-${Buffer.from(`${entry.url}-${sourceUrl||i}`).toString('base64url').slice(0,24)}`,platform:`${entry.platform}/Public Feed`,topic:tag(body,'title'),text:tag(body,'description')||tag(body,'summary')||tag(body,'content'),publishedAt:tag(body,'pubDate')||tag(body,'published')||tag(body,'updated')||null,sourceUrl,sourceDomain:domainOf(sourceUrl||entry.url),feedName:entry.name,feedTopic:entry.topic,feedType:checked.format,engagement:0,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false,providerProvenance:`RSS_FORUM / ${entry.name}`});}}return output;}
}

export class HackerNewsCollector extends FreeCollector {
  constructor(fetchImpl=fetch){super('Forums',true,fetchImpl);}
  async collectPublicOpportunities({query}={}){const url=new URL('https://hn.algolia.com/api/v1/search_by_date');url.search=new URLSearchParams({query:query||'find old photos photo search',tags:'story',hitsPerPage:'20'}).toString();const response=await this.fetch(url,{headers:{accept:'application/json'}});if(!response.ok)throw new Error(`Hacker News public API ${response.status}`);return ((await response.json()).hits||[]).map(x=>({id:`hn-${x.objectID}`,platform:'Hacker News/Public API',topic:x.title||x.story_title||'',text:x.story_text||x.comment_text||x.title||'',publishedAt:x.created_at,sourceUrl:`https://news.ycombinator.com/item?id=${x.objectID}`,sourceDomain:'news.ycombinator.com',engagement:Number(x.points||0)+Number(x.num_comments||0),audienceFit:.25,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false,providerProvenance:'HACKER_NEWS_PUBLIC_API'}));}
}

export class RedditPublicFeedCollector extends FreeCollector {
  constructor(enabled=true,fetchImpl=fetch,{dataDir='.'}={}){super('Reddit RSS',enabled,fetchImpl);this.cooldownFile=path.join(dataDir,'reddit-rss-cooldown.json');this.cooldownMs=30*60*1000;this.lastStatus='AVAILABLE';}
  async cooldown(){try{return Number(JSON.parse(await fs.readFile(this.cooldownFile,'utf8')).until)>Date.now();}catch{return false;}}
  async markRateLimited(){await fs.mkdir(path.dirname(this.cooldownFile),{recursive:true});await fs.writeFile(this.cooldownFile,JSON.stringify({until:Date.now()+this.cooldownMs}));}
  report(){return{...super.report(),status:this.lastStatus};}
  async collectPublicOpportunities({query}={}){if(!this.configured)return[];if(await this.cooldown()){this.lastStatus='RATE_LIMITED';return[];}const url=new URL('https://www.reddit.com/search.rss');url.search=new URLSearchParams({q:query||'"search camera roll" OR "find photo on my phone" OR "photo search not working" OR "organize my camera roll" OR "find old video"',sort:'new',t:'month'}).toString();const response=await this.fetch(url,{headers:{accept:'application/atom+xml','user-agent':'HonorableMarketing/1.0 (public RSS discovery)'}});if(response.status===429){await this.markRateLimited();this.lastStatus='RATE_LIMITED';return[];}if(!response.ok){this.lastStatus='FAILED';throw new Error(`Reddit public RSS ${response.status}`);}this.lastStatus='WORKING';const xml=await response.text();return entriesOf(xml).slice(0,25).map((body,i)=>{const sourceUrl=linkOf(body);return{id:`reddit-feed-${i}-${Buffer.from(sourceUrl).toString('base64url').slice(0,10)}`,platform:'Reddit/Public Feed',topic:tag(body,'title'),text:tag(body,'content')||tag(body,'summary'),publishedAt:tag(body,'updated'),sourceUrl,sourceDomain:'reddit.com',feedType:'ATOM',engagement:0,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false,providerProvenance:'REDDIT_PUBLIC_RSS'};});}
}
import fs from 'node:fs/promises';
import path from 'node:path';
