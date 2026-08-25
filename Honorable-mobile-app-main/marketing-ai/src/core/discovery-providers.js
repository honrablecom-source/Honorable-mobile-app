import { createCollectors } from './platforms.js';
import { ResilientWebDiscoveryCollector } from './web-discovery.js';
import { DiscoveryQueryEngine } from './query-intelligence.js';
import { HackerNewsCollector, PublicFeedCollector, RedditPublicFeedCollector } from './free-sources.js';

export class DiscoveryProvider {constructor(name){this.name=name;this.configured=false;}report(){return{platform:this.name,status:'NOT_CONFIGURED',dataType:'NONE',autoPublish:false};}async collectPublicOpportunities(){return[];}}
class SharedWebGateway {constructor(collector,queryEngine){this.collector=collector;this.queryEngine=queryEngine;this.cache=new Map();}search(query){const key=query||'default';const domains='(site:reddit.com OR site:x.com OR site:facebook.com OR site:instagram.com OR site:linkedin.com OR site:discussions.apple.com OR site:support.google.com OR inurl:forum)';const discoveryQuery=`${domains} (${this.queryEngine.combined(query)})`;if(!this.cache.has(key))this.cache.set(key,this.collector.collectPublicOpportunities({query:discoveryQuery}).then(items=>items.map(item=>({...item,discoveryQuery}))));return this.cache.get(key);}}
class CompositeProvider extends DiscoveryProvider {
  constructor(name,{direct,web,match,status}){super(name);this.direct=direct;this.web=web;this.match=match;this.status=status;this.configured=Boolean(direct?.configured||web?.collector?.configured);}
  report(){const direct=this.direct?.configured;const publicWeb=this.web?.collector?.configured;return{platform:this.name,status:direct?'CONNECTED':publicWeb?'PUBLIC_WEB':this.status||'NOT_CONFIGURED',dataType:direct?'DIRECT_API':publicWeb?'PUBLIC_WEB_SEARCH':'NONE',autoPublish:false};}
  async collectPublicOpportunities({query}={}){const tasks=[];if(this.direct?.configured)tasks.push(this.direct.collectPublicOpportunities({query}));if(this.web?.collector?.configured)tasks.push(this.web.search(query).then(items=>items.filter(this.match)));const settled=await Promise.allSettled(tasks);const failures=settled.filter(x=>x.status==='rejected');if(failures.length===settled.length&&failures.length)throw failures[0].reason;return settled.flatMap(x=>x.status==='fulfilled'?x.value:[]);}
}
const matchPlatform=pattern=>item=>pattern.test(item.platform)||pattern.test(item.sourceUrl||'');
export class RedditDiscoveryProvider extends CompositeProvider{constructor(options){super('Reddit',{...options,match:matchPlatform(/reddit/i),status:'MANUAL_ASSIST'});}}
export class XDiscoveryProvider extends CompositeProvider{constructor(options){super('X',{...options,match:matchPlatform(/(?:^X$|twitter|x\.com)/i),status:'MANUAL_ASSIST'});}}
export class FacebookPublicDiscoveryProvider extends CompositeProvider{constructor(options){super('Facebook',{...options,match:matchPlatform(/facebook/i),status:'MANUAL_ASSIST'});}}
export class InstagramPublicDiscoveryProvider extends CompositeProvider{constructor(options){super('Instagram',{...options,match:matchPlatform(/instagram/i),status:'MANUAL_ASSIST'});}}
export class ForumDiscoveryProvider extends CompositeProvider{constructor(options){super('Forums',{...options,match:matchPlatform(/forum|community|discussions|support/i),status:'MANUAL_ASSIST'});}}
export class LinkedInPublicDiscoveryProvider extends CompositeProvider{constructor(options){super('LinkedIn',{...options,match:matchPlatform(/linkedin/i),status:'MANUAL_ASSIST'});}}
export class WebDiscoveryProvider extends CompositeProvider{constructor(options){super('Web',{...options,match:item=>item.platform==='Public Web',status:'NOT_CONFIGURED'});}}
export class YouTubeDiscoveryProvider extends CompositeProvider{constructor(options){super('YouTube',{...options,match:matchPlatform(/youtube/i),status:'NOT_CONFIGURED'});this.role='CUSTOMER_DISCOVERY_SECONDARY';}}

export function createDiscoveryProviders(settings={},dependencies={}){const collectors=createCollectors(settings,dependencies);const direct=name=>collectors.find(x=>x.name===name);const webCollector=new ResilientWebDiscoveryCollector(settings,dependencies.fetchImpl);const web=new SharedWebGateway(webCollector,new DiscoveryQueryEngine({budget:Number(settings.discoveryQueryBudget||5)}));return[
  new RedditDiscoveryProvider({direct:direct('Reddit'),web}),new RedditPublicFeedCollector(settings.zeroCostMode!==false,dependencies.fetchImpl,{dataDir:settings.dataDir}),new XDiscoveryProvider({web}),new FacebookPublicDiscoveryProvider({web}),new InstagramPublicDiscoveryProvider({direct:direct('Instagram'),web}),new ForumDiscoveryProvider({web}),new HackerNewsCollector(dependencies.fetchImpl),new PublicFeedCollector({registryFile:settings.publicFeedsRegistry,urls:settings.rssFeedUrls},dependencies.fetchImpl),new LinkedInPublicDiscoveryProvider({web}),new WebDiscoveryProvider({web}),new YouTubeDiscoveryProvider({direct:settings.customerDiscoveryYoutubeEnabled?direct('YouTube'):null,web:null})
];}
