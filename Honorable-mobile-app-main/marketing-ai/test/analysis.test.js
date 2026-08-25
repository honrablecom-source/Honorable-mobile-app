import test from 'node:test';
import assert from 'node:assert/strict';
import { IntentClassifier, OpportunityAnalyzer } from '../src/core/analysis.js';
import { ContentGenerator, LocalRulesMarketingModel, MarketingResponseGenerator } from '../src/core/content.js';
import { createCollectors, ConnectorStatus, YouTubeMarketingCollector, GoogleWebDiscoveryCollector, SearXNGWebDiscoveryCollector } from '../src/core/platforms.js';
import { CustomerDiscoveryAgent, diagnoseDiscoveryResult, isQualityPublicProblem } from '../src/core/discovery.js';
import { AutoReplyEngine, AutoReplyMode } from '../src/core/autoreply.js';
import { createDiscoveryProviders } from '../src/core/discovery-providers.js';
import { DiscoveryQueryEngine, searchIntentTemplates } from '../src/core/query-intelligence.js';
import { webStatus } from '../scripts/web-status.js';
import { TestEmailService, ResendEmailService } from '../src/core/email.js';

test('intent examples classify as specified', () => {
  const classifier=new IntentClassifier();
  assert.equal(classifier.classify('I have 30,000 photos and can never find anything'),'VERY HIGH');
  assert.equal(classifier.classify("What's the best way to organize iPhone photos?"),'MEDIUM');
  assert.equal(classifier.classify('I love photography'),'LOW');
  assert.equal(classifier.classify('Best camera for landscape photography'),'IRRELEVANT');
  assert.equal(classifier.painPoint('I cannot find the old video'),'VIDEO_SEARCH');
  assert.equal(classifier.painPoint('AI search without uploading my library'),'PRIVACY_CONCERN');
});

test('customer discovery deduplicates, ranks, labels source, and creates transparent attributed responses',async()=>{
  let saved;const item={id:'one',platform:'Reddit/Public Web',topic:'Cannot find an old photo',text:"I don't remember the date and can't find it",publishedAt:'2026-08-13T00:00:00Z',sourceUrl:'https://example.com/post?utm_source=x',engagement:5,sourceType:'PUBLIC_WEB_SEARCH',dataType:'LIVE'};
  const agent=new CustomerDiscoveryAgent({collectors:[{configured:true,name:'Web',collectPublicOpportunities:async()=>[item,{...item,id:'two',sourceUrl:'https://example.com/post'}]}],store:{saveDiscovery:async items=>saved=items},analyzer:new OpportunityAnalyzer(undefined,()=>new Date('2026-08-14T00:00:00Z')),responseGenerator:new MarketingResponseGenerator('https://honorable.example/waitlist')});
  const result=await agent.discover();assert.equal(result.opportunities.length,1);assert.equal(saved.length,1);assert.equal(saved[0].sourceType,'PUBLIC_WEB_SEARCH');assert.match(saved[0].suggestedResponse,/I’m working on Honorable/);assert.match(saved[0].suggestedResponse,/utm_source=reddit-public-web/);
});

test('auto-reply defaults to review, blocks sensitive topics, and only simulates posting',async()=>{
  const state={autoReplySettings:{globalEnabled:false,killSwitch:false,platforms:{}},autoReplyAudit:[]};let saved;const audits=[];const store={snapshot:async()=>structuredClone(state),saveAutoReplyProposals:async x=>saved=x,addAutoReplyAudit:async x=>audits.push(x)};const responseGenerator=new MarketingResponseGenerator('https://honorable.example/');const engine=new AutoReplyEngine({store,responseGenerator,config:{dryRun:true,platforms:{reddit:{mode:AutoReplyMode.APPROVAL_REQUIRED,authenticated:false,apiSupport:'MANUAL_ASSIST'}}}});
  const base={id:'post-1',platform:'Reddit',topic:'Cannot find an old photo',text:"I have 30,000 photos and can't find one",publishedAt:'2026-08-13T00:00:00Z',sourceUrl:'https://reddit.example/post-1',sourceType:'DIRECT_API',dataType:'LIVE',intent:'VERY HIGH',relevanceScore:95,score:94,painPoint:'PHOTO_SEARCH'};
  const proposal=await engine.evaluate(base);assert.equal(proposal.eligible,false);assert.ok(proposal.reasons.includes('GLOBAL_DISABLED'));assert.ok(proposal.reasons.includes('ACCOUNT_NOT_AUTHENTICATED'));assert.match(proposal.reply,/I’m working on Honorable/);assert.match(proposal.reply,/utm_medium=auto_reply/);
  const sensitive=await engine.evaluate({...base,id:'post-2',sourceUrl:'https://reddit.example/post-2',text:'I am in a health crisis and cannot find a photo'});assert.ok(sensitive.reasons.includes('SENSITIVE_TOPIC'));
  const dry=await engine.dryRun([base]);assert.equal(dry.length,1);assert.equal(saved.length,1);assert.equal(audits[0].postingResult,'SIMULATED_NOT_POSTED');
});

test('multi-source provider architecture prioritizes social and keeps YouTube secondary',()=>{
  const providers=createDiscoveryProviders({credentials:{},webSearch:{}});assert.deepEqual(providers.map(x=>x.name),['Reddit','X','Facebook','Instagram','Forums','LinkedIn','Web','YouTube']);assert.equal(providers.at(-1).role,'CUSTOMER_DISCOVERY_SECONDARY');assert.equal(providers.find(x=>x.name==='X').report().status,'MANUAL_ASSIST');
});

test('query intelligence has at least 50 semantic templates and obeys its budget',()=>{
  assert.ok(searchIntentTemplates.length>=50);const engine=new DiscoveryQueryEngine({budget:5,seed:()=>3});assert.equal(engine.select().length,5);assert.equal(engine.select('custom problem')[0],'custom problem');
});

test('web status never attempts a request with a missing engine id',async()=>{let calls=0;const result=await webStatus({GOOGLE_SEARCH_API_KEY:'secret'},async()=>{calls++;});assert.equal(result.liveRequest,'NOT ATTEMPTED');assert.equal(result.reason,'MISSING_ENGINE_ID');assert.equal(calls,0);});

test('web status makes one minimal request when both credentials exist',async()=>{let request;const result=await webStatus({GOOGLE_SEARCH_API_KEY:'secret',GOOGLE_SEARCH_ENGINE_ID:'engine'},async url=>(request=url,{ok:true}));assert.equal(result.liveRequest,'PASS');assert.equal(request.searchParams.get('num'),'1');assert.equal(request.searchParams.get('q'),"\"can't find old photo\"");});

test('web status distinguishes healthy SearXNG from an unresponsive upstream engine',async()=>{const result=await webStatus({SEARXNG_BASE_URL:'http://127.0.0.1:8080'},async()=>({ok:true,status:200,json:async()=>({results:[],unresponsive_engines:[['brave','rate limited']]})}));assert.equal(result.searxng,'RUNNING');assert.equal(result.rawResults,0);assert.equal(result.honorableResults,0);assert.match(result.reason,/UPSTREAM_ENGINE_UNRESPONSIVE: brave/);});

test('public web results retain minimal metadata and map domains without direct-api labels',async()=>{const collector=new GoogleWebDiscoveryCollector({apiKey:'secret',engineId:'engine'},async()=>({ok:true,json:async()=>({items:[{title:'Cannot find an old photo',snippet:"I've been trying to find an old photo but don't remember the date.",link:'https://www.reddit.com/r/iphone/example'}]})}));const [item]=await collector.collectPublicOpportunities({query:'test'});assert.equal(item.platform,'Reddit/Public Web');assert.equal(item.sourceType,'PUBLIC_WEB');assert.equal(item.sourceDomain,'reddit.com');assert.ok(item.discoveredAt);assert.equal('channel' in item,false);});

test('public problem quality filter rejects SEO and accepts clear frustration',()=>{const base={sourceType:'PUBLIC_WEB',sourceUrl:'https://example.com/post',text:"I've been scrolling for an hour and can't find an old photo in my camera roll."};assert.equal(isQualityPublicProblem({...base,topic:'Please help'}),true);assert.equal(isQualityPublicProblem({...base,topic:'Top 10 best photo apps product review'}),false);assert.equal(isQualityPublicProblem({...base,text:'Photography tips and camera lens advice for beginners'}),false);});

test('discovery diagnostics preserve rejection reason and borderline status',()=>{const analyzer=new OpportunityAnalyzer(undefined,()=>new Date('2026-08-19T00:00:00Z'));const base={platform:'Forums/Public Web',sourceType:'PUBLIC_WEB',sourceUrl:'https://example.com/thread',topic:'Looking for help',publishedAt:'2026-08-18',text:'Looking for a specific photo buried in my camera roll from years ago, but the date is unknown.'};const result=diagnoseDiscoveryResult(base,analyzer);assert.equal(result.accepted,false);assert.equal(result.borderline,true);assert.equal(result.rejectionReason,'LOW_INTENT');assert.equal(diagnoseDiscoveryResult({...base,text:'A short snippet'},analyzer).rejectionReason,'INSUFFICIENT_SNIPPET');});

test('self-hosted SearXNG is topic based and returns only public result metadata',async()=>{let requested;const collector=new SearXNGWebDiscoveryCollector({baseUrl:'https://search.example',engines:'compliant-engine'},async url=>(requested=url,{ok:true,json:async()=>({results:[{title:'How can I find an old picture?',content:"I can't remember the date and have thousands of photos.",url:'https://x.com/example/status/1',publishedDate:'2026-08-18'}]})}));const [item]=await collector.collectPublicOpportunities({query:'"can\'t find old photo"'});assert.equal(requested.pathname,'/search');assert.equal(requested.searchParams.get('format'),'json');assert.equal(item.platform,'X/Public Web');assert.equal(item.sourceType,'PUBLIC_WEB');assert.deepEqual(Object.keys(item).filter(x=>['topic','text','sourceDomain','discoveredAt','sourceUrl'].includes(x)).sort(),['discoveredAt','sourceDomain','sourceUrl','text','topic']);});

test('opportunity score is bounded and exposes every score component', () => {
  const analyzer=new OpportunityAnalyzer(undefined,()=>new Date('2026-08-14T00:00:00Z'));
  const result=analyzer.analyze({platform:'Reddit',topic:'Cannot find photo',text:'I can never find one old photo',publishedAt:'2026-08-13T00:00:00Z',engagement:100,audienceFit:.9});
  assert.ok(result.score>=0&&result.score<=100);
  assert.deepEqual(Object.keys(result.scoring),['problemRelevance','intent','recency','engagement','platform','audienceFit']);
});

test('local content generator creates an approval-required draft without fake claims', async () => {
  const draft=await new ContentGenerator(new LocalRulesMarketingModel()).generate({format:'TikTok',opportunity:{painPoint:'SEARCH'}});
  assert.equal(draft.status,'DRAFT');assert.match(draft.cta,/You remember the moment/);assert.match(draft.claims,/No testimonial/);
});

test('platforms report NOT CONFIGURED without official credentials', () => {
  const reports=createCollectors({}).map(x=>x.report());
  assert.equal(reports.find(x=>x.platform==='YouTube').status,ConnectorStatus.NOT_CONFIGURED);
  assert.equal(reports.find(x=>x.platform==='Instagram').status,ConnectorStatus.CONFIGURATION_REQUIRED);
  assert.equal(reports.find(x=>x.platform==='Reddit').status,ConnectorStatus.APPROVAL_REQUIRED);
  assert.equal(reports.find(x=>x.platform==='TikTok').status,ConnectorStatus.OFFICIAL_UNAVAILABLE);
  assert.ok(reports.every(x=>x.autoPublish===false));
});

test('configured official collector labels returned public metadata LIVE', async () => {
  const responses=[{items:[{id:{videoId:'abc'},snippet:{title:'Find old photo',description:'cannot find it',channelTitle:'Channel',publishedAt:'2026-08-13T00:00:00Z'}}]},{items:[{id:'abc',statistics:{viewCount:'42'}}]}];
  const collector=new YouTubeMarketingCollector('key',async()=>({ok:true,json:async()=>responses.shift()}));
  const items=await collector.collectPublicOpportunities();assert.equal(items[0].dataType,'LIVE');assert.equal(items[0].engagement,42);assert.match(items[0].sourceUrl,/youtube/);
});

test('official adapter failure is surfaced without a fallback request', async () => {
  const collector=new YouTubeMarketingCollector('key',async()=>({ok:false,status:403}));
  await assert.rejects(()=>collector.collectPublicOpportunities(),/YouTube API 403/);
});

test('email abstraction has a non-sending test mode and Resend request seam', async () => {
  const testEmail=new TestEmailService();const simulated=await testEmail.sendWelcome({to:'person@example.com'});assert.equal(simulated.status,'simulated');assert.equal(testEmail.deliveries.length,1);
  let request;const resend=new ResendEmailService({apiKey:'test-key',from:'Honorable <hello@example.com>',fetchImpl:async(url,options)=>(request={url,options},{ok:true,json:async()=>({id:'email-1'})})});const sent=await resend.sendWelcome({to:'person@example.com',unsubscribeUrl:'https://example.com/unsubscribe?token=x'});assert.equal(sent.status,'success');assert.match(request.options.body,/unsubscribe/);
});
