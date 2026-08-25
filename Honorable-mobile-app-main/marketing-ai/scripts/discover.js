import { config } from '../src/config.js';
import { createDiscoveryProviders } from '../src/core/discovery-providers.js';
import { CustomerDiscoveryAgent } from '../src/core/discovery.js';
import { MarketingResponseGenerator } from '../src/core/content.js';
import { JsonStore } from '../src/store.js';

const args=process.argv.slice(2);const queryIndex=args.indexOf('--query');const query=queryIndex>=0?args[queryIndex+1]:undefined;
const settings=config();const collectors=createDiscoveryProviders(settings);const agent=new CustomerDiscoveryAgent({collectors,store:new JsonStore(settings.dataDir),responseGenerator:new MarketingResponseGenerator(settings.waitlistUrl)});
const configured=collectors.filter(x=>x.configured);
if(!configured.length){console.log('NO LIVE SOURCE CONFIGURED');console.log('Configure Google Custom Search or an approved official platform API. No opportunities were fabricated.');process.exit(0);}
const result=await agent.discover({query});
for(const provider of result.providerResults){const active=['CONNECTED','PUBLIC_WEB'].includes(provider.status);console.log(`${provider.platform.toUpperCase()}: ${provider.status==='FAILED'?`FAILED — ${provider.error}`:active?provider.count:provider.status}`);}
for(const [index,item] of result.diagnostics.slice(0,20).entries()){
  console.log(`\nRAW RESULT ${index+1}`);console.log(`Provider: ${item.providerProvenance||'PUBLIC_WEB'}`);console.log(`Platform: ${item.platform.replace('/Public Web','')}`);console.log(`Domain: ${item.sourceDomain||'unknown'}`);console.log(`Title: ${item.topic}`);console.log(`Snippet: ${item.text}`);console.log(`URL: ${item.sourceUrl}`);console.log(`Query: ${item.discoveryQuery||query||'default query rotation'}`);console.log(`→ CLASSIFICATION: ${item.painPoint}`);console.log(`→ INTENT: ${item.intent.replace('VERY HIGH','VERY_HIGH')}`);console.log(`→ PRODUCT_FIT_SCORE: ${item.productFitScore}`);console.log(`→ ENTAILMENT: ${item.entailment}`);console.log(`→ USER PROBLEM: ${item.userProblem}`);console.log(`→ HONORABLE FEATURE THAT SOLVES IT: ${item.honorableFeature||'NONE'}`);console.log(`→ WHY IT FITS: ${item.whyItFits}`);console.log(`→ SCORES: relevance=${item.relevanceScore} opportunity=${item.overallOpportunityScore}`);console.log(`→ ${item.accepted?'ACCEPT':item.borderline?'BORDERLINE':'REJECT'}`);console.log(`→ REASON: ${item.rejectionReason||'ACCEPTED'}`);
}
const counts=result.diagnostics.reduce((all,item)=>((all[item.rejectionReason||'ACCEPTED']=(all[item.rejectionReason||'ACCEPTED']||0)+1),all),{});
console.log(`\nRAW RESULTS: ${result.diagnostics.length}`);console.log(`ACCEPTED: ${result.opportunities.length}`);console.log(`BORDERLINE: ${result.borderline.length}`);for(const [reason,count] of Object.entries(counts).sort((a,b)=>b[1]-a[1]))console.log(`${reason}: ${count}`);
console.log(`VERY HIGH: ${result.opportunities.filter(x=>x.intent==='VERY HIGH').length}`);console.log(`HIGH: ${result.opportunities.filter(x=>x.intent==='HIGH').length}`);
for(const error of result.errors)console.error(`SOURCE ERROR — ${error.platform}: ${error.error}`);
if(!result.opportunities.length)console.log('\nNo live opportunities were returned by the configured sources.');
