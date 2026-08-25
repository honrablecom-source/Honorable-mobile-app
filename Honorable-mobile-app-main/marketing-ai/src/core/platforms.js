export const ConnectorStatus=Object.freeze({CONNECTED:'CONNECTED',NOT_CONFIGURED:'NOT CONFIGURED',APPROVAL_REQUIRED:'APPROVAL REQUIRED',CONFIGURATION_REQUIRED:'CONFIGURATION REQUIRED',MANUAL_ONLY:'MANUAL ONLY',OFFICIAL_UNAVAILABLE:'OFFICIAL API UNAVAILABLE'});
export const discoveryQueries=["can't find an old photo without the date",'search camera roll by description','too many photos on iPhone','too many photos on Android','find an old video by what happens','cannot find screenshot receipt','photo organization app huge library','Google Photos search not finding photo','iPhone Photos search not working','AI photo finder private'];

class OfficialCollector {
  constructor(name,configured=false){this.name=name;this.configured=Boolean(configured);}
  report(){return {platform:this.name,status:this.configured?ConnectorStatus.CONNECTED:ConnectorStatus.NOT_CONFIGURED,dataType:this.configured?'LIVE':'NONE',autoPublish:false};}
}

export class YouTubeMarketingCollector extends OfficialCollector {
  constructor(apiKey,fetchImpl=fetch){super('YouTube',apiKey);this.apiKey=apiKey;this.fetch=fetchImpl;}
  async collectPublicOpportunities(){
    if(!this.apiKey)return[]; const search=new URL('https://www.googleapis.com/youtube/v3/search');
    search.search=new URLSearchParams({part:'snippet',type:'video',maxResults:'25',order:'date',q:discoveryQueries.slice(0,5).join('|'),key:this.apiKey}).toString();
    const found=await this.fetch(search);if(!found.ok)throw new Error(`YouTube API ${found.status}`);const payload=await found.json();
    const ids=payload.items.map(x=>x.id.videoId).filter(Boolean);let stats=new Map();
    if(ids.length){const videos=new URL('https://www.googleapis.com/youtube/v3/videos');videos.search=new URLSearchParams({part:'statistics',id:ids.join(','),key:this.apiKey}).toString();const response=await this.fetch(videos);if(!response.ok)throw new Error(`YouTube statistics API ${response.status}`);stats=new Map((await response.json()).items.map(x=>[x.id,x.statistics]));}
    return payload.items.map(x=>({id:`youtube-${x.id.videoId}`,platform:'YouTube',topic:x.snippet.title,text:x.snippet.description||'',publishedAt:x.snippet.publishedAt,sourceUrl:`https://www.youtube.com/watch?v=${x.id.videoId}`,engagement:Number(stats.get(x.id.videoId)?.viewCount||0),metrics:stats.get(x.id.videoId)||{},sourceType:'DIRECT_API',dataType:'LIVE',mock:false}));
  }
}

export class InstagramMarketingCollector extends OfficialCollector {
  constructor(credentials,fetchImpl=fetch){super('Instagram',credentials);this.credentials=credentials;this.fetch=fetchImpl;}
  report(){return {platform:'Instagram',status:this.configured?ConnectorStatus.CONNECTED:ConnectorStatus.CONFIGURATION_REQUIRED,dataType:this.configured?'LIVE':'NONE',autoPublish:false,requirements:'Professional Business/Creator account; instagram_basic, pages_read_engagement; hashtag search subject to Meta app review and current API eligibility.'};}
  async collectPublicOpportunities(){
    if(!this.credentials)return[];const results=[];const tags=['photoorganization','cameraroll','photosearch','aiphotos'];
    for(const tag of tags){const lookup=new URL('https://graph.facebook.com/v23.0/ig_hashtag_search');lookup.search=new URLSearchParams({user_id:this.credentials.accountId,q:tag,access_token:this.credentials.token}).toString();const response=await this.fetch(lookup);if(!response.ok)throw new Error(`Instagram Graph API ${response.status}`);const id=(await response.json()).data?.[0]?.id;if(!id)continue;
      const media=new URL(`https://graph.facebook.com/v23.0/${id}/recent_media`);media.search=new URLSearchParams({user_id:this.credentials.accountId,fields:'id,caption,media_type,permalink,timestamp,like_count,comments_count',limit:'10',access_token:this.credentials.token}).toString();const feed=await this.fetch(media);if(!feed.ok)throw new Error(`Instagram Graph API ${feed.status}`);for(const x of (await feed.json()).data||[])results.push({id:`instagram-${x.id}`,platform:'Instagram',topic:`#${tag}`,text:x.caption||'',publishedAt:x.timestamp,sourceUrl:x.permalink,engagement:Number(x.like_count||0)+Number(x.comments_count||0),dataType:'LIVE',mock:false});}
    return results;
  }
}

export class RedditMarketingCollector extends OfficialCollector {
  constructor(credentials,approved=false,fetchImpl=fetch){super('Reddit',credentials&&approved);this.credentials=credentials;this.approved=approved;this.fetch=fetchImpl;}
  report(){return {platform:'Reddit',status:this.approved?(this.credentials?ConnectorStatus.CONNECTED:ConnectorStatus.NOT_CONFIGURED):ConnectorStatus.APPROVAL_REQUIRED,dataType:this.configured?'LIVE':'NONE',autoPublish:false,requirements:'Express Reddit approval for this commercial intelligence use, registered OAuth app, client credentials, and a descriptive User-Agent.'};}
  async collectPublicOpportunities(){
    if(!this.configured)return[];const basic=Buffer.from(`${this.credentials.clientId}:${this.credentials.clientSecret}`).toString('base64');const auth=await this.fetch('https://www.reddit.com/api/v1/access_token',{method:'POST',headers:{authorization:`Basic ${basic}`,'user-agent':this.credentials.userAgent||'HonorableMarketing/1.0'},body:new URLSearchParams({grant_type:'client_credentials'})});if(!auth.ok)throw new Error(`Reddit OAuth ${auth.status}`);const token=(await auth.json()).access_token;
    const url=new URL('https://oauth.reddit.com/search');url.search=new URLSearchParams({q:'photo organization OR "find old photos" OR "too many photos"',sort:'new',t:'month',type:'link',limit:'25',restrict_sr:'false'}).toString();const response=await this.fetch(url,{headers:{authorization:`Bearer ${token}`,'user-agent':this.credentials.userAgent||'HonorableMarketing/1.0'}});if(!response.ok)throw new Error(`Reddit API ${response.status}`);
    return ((await response.json()).data?.children||[]).map(({data:x})=>({id:`reddit-${x.id}`,platform:'Reddit',topic:x.title,text:x.selftext||'',publishedAt:new Date(x.created_utc*1000).toISOString(),sourceUrl:`https://www.reddit.com${x.permalink}`,engagement:Number(x.score||0)+Number(x.num_comments||0),dataType:'LIVE',mock:false}));
  }
}

export class TikTokManualCollector extends OfficialCollector {
  constructor(){super('TikTok',false);}
  report(){return {platform:'TikTok',status:ConnectorStatus.OFFICIAL_UNAVAILABLE,dataType:'MANUAL',autoPublish:false,requirements:'No general official commercial public-content discovery API is configured. Research API is not used. Import only observations/exports obtained through approved business tools.'};}
  async collectPublicOpportunities(){return[];}
}

export class GoogleWebDiscoveryCollector extends OfficialCollector {
  constructor(config={},fetchImpl=fetch){super('Web discovery',config.apiKey&&config.engineId);this.config=config;this.fetch=fetchImpl;}
  async collectPublicOpportunities({query}={}){
    if(!this.config.apiKey||!this.config.engineId)return[];
    const url=new URL('https://www.googleapis.com/customsearch/v1');url.search=new URLSearchParams({key:this.config.apiKey,cx:this.config.engineId,q:query||discoveryQueries.slice(0,5).map(x=>`"${x}"`).join(' OR '),num:'10',dateRestrict:'m6'}).toString();const response=await this.fetch(url);if(!response.ok)throw new Error(`Google Custom Search API ${response.status}`);
    return ((await response.json()).items||[]).map((x,i)=>{let platform='Forums/Public Web',sourceDomain='';try{sourceDomain=new URL(x.link).hostname.toLowerCase().replace(/^www\./,'');if(sourceDomain==='reddit.com'||sourceDomain.endsWith('.reddit.com'))platform='Reddit/Public Web';else if(sourceDomain==='x.com'||sourceDomain.endsWith('.x.com')||sourceDomain.endsWith('twitter.com'))platform='X/Public Web';else if(sourceDomain==='facebook.com'||sourceDomain.endsWith('.facebook.com'))platform='Facebook/Public Web';else if(sourceDomain==='instagram.com'||sourceDomain.endsWith('.instagram.com'))platform='Instagram/Public Web';else if(sourceDomain==='linkedin.com'||sourceDomain.endsWith('.linkedin.com'))platform='LinkedIn/Public Web';}catch{}
      return{id:`web-${x.cacheId||i}-${Buffer.from(x.link).toString('base64url').slice(0,12)}`,platform,topic:x.title||'',text:x.snippet||'',sourceDomain,publishedAt:x.pagemap?.metatags?.[0]?.['article:published_time']||null,discoveredAt:new Date().toISOString(),sourceUrl:x.link,engagement:0,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false};});
  }
}

export class BraveWebDiscoveryCollector extends OfficialCollector {
  constructor(config={},fetchImpl=fetch){super('Brave Search',config.apiKey);this.config=config;this.fetch=fetchImpl;}
  async collectPublicOpportunities({query}={}){
    if(!this.config.apiKey)return[];
    const url=new URL('https://api.search.brave.com/res/v1/web/search');url.search=new URLSearchParams({q:query||discoveryQueries.slice(0,5).map(x=>`"${x}"`).join(' OR '),count:'10',search_lang:'en',safesearch:'moderate',freshness:'pm'}).toString();
    const response=await this.fetch(url,{headers:{accept:'application/json','x-subscription-token':this.config.apiKey}});if(!response.ok)throw new Error(`Brave Search API ${response.status}`);
    return ((await response.json()).web?.results||[]).map((x,i)=>{let platform='Forums/Public Web',sourceDomain='';try{sourceDomain=new URL(x.url).hostname.toLowerCase().replace(/^www\./,'');if(sourceDomain==='reddit.com'||sourceDomain.endsWith('.reddit.com'))platform='Reddit/Public Web';else if(sourceDomain==='x.com'||sourceDomain.endsWith('.x.com')||sourceDomain.endsWith('twitter.com'))platform='X/Public Web';else if(sourceDomain==='facebook.com'||sourceDomain.endsWith('.facebook.com'))platform='Facebook/Public Web';else if(sourceDomain==='instagram.com'||sourceDomain.endsWith('.instagram.com'))platform='Instagram/Public Web';else if(sourceDomain==='linkedin.com'||sourceDomain.endsWith('.linkedin.com'))platform='LinkedIn/Public Web';}catch{}
      return{id:`brave-${i}-${Buffer.from(x.url||'').toString('base64url').slice(0,12)}`,platform,topic:x.title||'',text:x.description||'',sourceDomain,publishedAt:x.page_age||null,discoveredAt:new Date().toISOString(),sourceUrl:x.url,engagement:0,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false};});
  }
}

export class SearXNGWebDiscoveryCollector extends GoogleWebDiscoveryCollector {
  constructor(config={},fetchImpl=fetch){super({},fetchImpl);this.name='Web discovery';this.config=config;this.configured=Boolean(config.baseUrl);}
  async collectPublicOpportunities({query}={}){
    if(!this.config.baseUrl)return[];const url=new URL('/search',this.config.baseUrl);url.search=new URLSearchParams({q:query||discoveryQueries[0],format:'json',language:'en',safesearch:'1',time_range:'month',...(this.config.engines?{engines:this.config.engines}:{})}).toString();const response=await this.fetch(url,{headers:{accept:'application/json'}});if(!response.ok)throw new Error(`SearXNG API ${response.status}`);const payload=await response.json();
    return (payload.results||[]).slice(0,20).map((x,i)=>{let platform='Forums/Public Web',sourceDomain='';try{sourceDomain=new URL(x.url).hostname.toLowerCase().replace(/^www\./,'');if(sourceDomain==='reddit.com'||sourceDomain.endsWith('.reddit.com'))platform='Reddit/Public Web';else if(sourceDomain==='x.com'||sourceDomain.endsWith('.x.com')||sourceDomain.endsWith('twitter.com'))platform='X/Public Web';else if(sourceDomain==='facebook.com'||sourceDomain.endsWith('.facebook.com'))platform='Facebook/Public Web';else if(sourceDomain==='instagram.com'||sourceDomain.endsWith('.instagram.com'))platform='Instagram/Public Web';else if(sourceDomain==='linkedin.com'||sourceDomain.endsWith('.linkedin.com'))platform='LinkedIn/Public Web';}catch{}
      return{id:`searxng-${i}-${Buffer.from(x.url||'').toString('base64url').slice(0,12)}`,platform,topic:x.title||'',text:x.content||'',sourceDomain,searchEngines:[...new Set([...(x.engines||[]),...(x.engine?[x.engine]:[])])],publishedAt:x.publishedDate||null,discoveredAt:new Date().toISOString(),sourceUrl:x.url,engagement:0,sourceType:'PUBLIC_WEB',dataType:'LIVE',mock:false};});
  }
}

export function createCollectors(settings={},dependencies={}){const credentials=settings.credentials||settings;const fetchImpl=dependencies.fetchImpl||fetch;return[new YouTubeMarketingCollector(credentials.youtube,fetchImpl),new InstagramMarketingCollector(credentials.instagram,fetchImpl),new RedditMarketingCollector(credentials.reddit,credentials.redditApproval,fetchImpl),new TikTokManualCollector(),new GoogleWebDiscoveryCollector(settings.webSearch,fetchImpl)];}

export const mockPublicConversations=[
  {id:'mock-1',platform:'Reddit',topic:'I have 30,000 photos and can never find anything',text:'I have 30,000 photos and can never find the old beach photo I remember.',sourceUrl:null,publishedAt:'2026-08-11T12:00:00Z',engagement:420,audienceFit:.96,mock:true,dataType:'MOCK'},
  {id:'mock-2',platform:'YouTube',topic:'Searching years of family videos',text:'How do people find one moment in years of videos without remembering the date?',sourceUrl:null,publishedAt:'2026-08-09T16:30:00Z',engagement:176,audienceFit:.91,mock:true,dataType:'MOCK'},
  {id:'mock-3',platform:'TikTok',topic:'Endless screenshot scrolling',text:'POV you know the screenshot is on your phone but search cannot find the words.',sourceUrl:null,publishedAt:'2026-08-12T08:20:00Z',engagement:880,audienceFit:.87,mock:true,dataType:'MOCK'},
  {id:'mock-4',platform:'Instagram',topic:'Photo organization advice',text:'What is the best way to organize iPhone photos when albums become another chore?',sourceUrl:null,publishedAt:'2026-08-07T10:10:00Z',engagement:95,audienceFit:.72,mock:true,dataType:'MOCK'},
  {id:'mock-5',platform:'Reddit',topic:'Private AI photo search',text:'Is there an AI photo search that stays on device and does not upload my library?',sourceUrl:null,publishedAt:'2026-08-13T09:00:00Z',engagement:244,audienceFit:.98,mock:true,dataType:'MOCK'}
];
