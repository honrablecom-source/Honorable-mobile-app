import { OpportunityAnalyzer, TrendAnalyzer } from './analysis.js';

const normalizeUrl=value=>{try{const url=new URL(value);url.hash='';['utm_source','utm_medium','utm_campaign','utm_content'].forEach(x=>url.searchParams.delete(x));return url.toString();}catch{return value||'';}};
const problemSignal=/(can(?:not|'t)|couldn(?:'t| not)|struggl|frustrat|looking for|trying to find|how (?:do|can) i find|is there an app|too many|thousands|camera roll|don't remember|forgot when|search (?:isn't|doesn't|not)|need (?:a|something) better|scrolling.{0,30}(hour|forever))/i;
const lowQuality=/(\btop \d+\b|\bbest (?:photo|video|gallery) apps?\b|product review|buy now|pricing|download (?:the )?app|press release|affiliate|sponsored|stock photo|camera lens|photography tips)/i;
const productPage=/(?:\/(?:product|products|pricing|download|apps?)\/|\b(?:buy now|add to cart|start free trial|download (?:the )?app)\b)/i;
const newsArticle=/(?:\b(?:breaking news|press release|announces?|launched?|according to)\b|\/(?:news|press|article)s?\/)/i;
const promotional=/(?:\b(?:sponsored|affiliate|limited time|special offer|sign up today)\b)/i;
const vendorAuthored=/(?:\b(?:i|we) (?:built|made|created|developed|launched|released) (?:an?|our|this|my) (?:app|product|tool|service)\b|show hn:)/i;
const nonQuestionContent=/(?:\brelease notes?\b|\bchangelog\b|\bannounc(?:e|ement|ing)\b)/i;
export function publicProblemRejection(item){
  if(item.sourceType!=='PUBLIC_WEB')return null;
  if(!item.sourceUrl||!item.text||item.text.trim().length<35)return 'INSUFFICIENT_SNIPPET';
  const content=`${item.topic||''} ${item.text}`;const page=`${item.sourceUrl} ${content}`;
  if(/^RSS_FORUM/.test(item.providerProvenance||'')&&/(?:\brclone\b|\bdirector(?:y|ies)\b|\bfile transfer\b|\bsync\b)/i.test(`${item.topic} ${item.feedTopic||''}`)&&!/(?:camera roll|photo library|photos? search|photo management|find (?:a |my )?(?:photo|picture|video|screenshot))/i.test(content))return 'IRRELEVANT_FEED_TOPIC';
  if(productPage.test(page))return 'PRODUCT_PAGE';
  if(newsArticle.test(page)&&!problemSignal.test(content))return 'NEWS_ARTICLE';
  if(promotional.test(content))return 'PROMOTIONAL_CONTENT';
  if(vendorAuthored.test(content))return 'PROMOTIONAL_CONTENT';
  if(nonQuestionContent.test(content)&&!problemSignal.test(content))return 'NEWS_ARTICLE';
  if(lowQuality.test(content))return 'SEO_CONTENT';
  if(/(?:log in|sign up) to (?:see|continue|view)/i.test(content)&&!problemSignal.test(content))return 'INSUFFICIENT_SNIPPET';
  if(!problemSignal.test(content))return 'NO_USER_PROBLEM';
  return null;
}
export function isQualityPublicProblem(item){return publicProblemRejection(item)===null;}

export function diagnoseDiscoveryResult(item,analyzer=new OpportunityAnalyzer(),minimumProductFit=80){
  const analyzed=analyzer.analyze(item);let rejectionReason=publicProblemRejection(item);
  if(!rejectionReason&&analyzed.entailment==='NO')rejectionReason=`PRODUCT_FIT_NO${analyzed.excludedProblem?`_${analyzed.excludedProblem}`:''}`;
  if(!rejectionReason&&analyzed.entailment==='UNCERTAIN')rejectionReason='PRODUCT_FIT_UNCERTAIN';
  if(!rejectionReason&&analyzed.productFitScore<minimumProductFit)rejectionReason='PRODUCT_FIT_BELOW_THRESHOLD';
  if(!rejectionReason&&analyzed.intent==='IRRELEVANT')rejectionReason='IRRELEVANT';
  if(!rejectionReason&&analyzed.relevanceScore<60)rejectionReason='LOW_RELEVANCE';
  if(!rejectionReason&&analyzed.intent==='LOW')rejectionReason='LOW_INTENT';
  const accepted=!rejectionReason;const borderline=!accepted&&['LOW_INTENT','LOW_RELEVANCE','PRODUCT_FIT_UNCERTAIN'].includes(rejectionReason)&&publicProblemRejection(item)===null;
  return {...analyzed,accepted,borderline,rejectionReason};
}

export class CustomerDiscoveryAgent {
  constructor({collectors,store,analyzer=new OpportunityAnalyzer(),responseGenerator}){this.collectors=collectors;this.store=store;this.analyzer=analyzer;this.responseGenerator=responseGenerator;}
  async discover({query}={}){
    const runToken=`discovery-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
    const results=await Promise.allSettled(this.collectors.map(collector=>collector.collectPublicOpportunities({query})));
    const errors=results.flatMap((result,index)=>result.status==='rejected'?[{platform:this.collectors[index].name,error:result.reason?.message||'Request failed'}]:[]);const providerResults=results.map((result,index)=>({platform:this.collectors[index].name,status:result.status==='rejected'?'FAILED':(this.collectors[index].report?.().status||'WORKING'),count:result.status==='fulfilled'?result.value.length:0,error:result.status==='rejected'?(result.reason?.message||'Request failed'):null}));
    const unique=new Map();const titles=new Set();const diagnostics=[];
    for(const raw of results.flatMap(x=>x.status==='fulfilled'?x.value:[])){const key=normalizeUrl(raw.sourceUrl)||raw.id;const title=String(raw.topic||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();if(unique.has(key)||(title&&titles.has(title))){diagnostics.push({...diagnoseDiscoveryResult(raw,this.analyzer),accepted:false,borderline:false,rejectionReason:'DUPLICATE'});}else{unique.set(key,raw);if(title)titles.add(title);diagnostics.push(diagnoseDiscoveryResult(raw,this.analyzer));}}
    const opportunities=diagnostics.filter(x=>x.accepted).map(x=>({...x,discoveryRunToken:runToken,suggestedResponse:this.responseGenerator.generateResponse(x),status:'NEW'})).sort((a,b)=>b.score-a.score);
    const borderline=diagnostics.filter(x=>x.borderline);
    if(this.store)await this.store.saveDiscovery(opportunities,{errors,query:query||null,runToken});
    return {opportunities,borderline,diagnostics,errors,providerResults,scannedSources:this.collectors.length,liveSources:this.collectors.filter(x=>x.configured).length};
  }
}

export function discoveryIntelligence(opportunities){
  const trends=new TrendAnalyzer().summarize(opportunities);const top=trends[0];
  return {trends,contentIdeas:trends.slice(0,5).map(x=>({problem:x.painPoint,count:x.count,idea:`Create a concrete demo for ${x.painPoint.toLowerCase().replaceAll('_',' ')} based on ${x.count} public signal${x.count===1?'':'s'}.`})),bestContentOpportunity:top?`Make a demo addressing ${top.painPoint.toLowerCase().replaceAll('_',' ')}; it is the strongest recurring signal (${top.share}%).`:'Run live discovery to identify a recurring problem.'};
}
