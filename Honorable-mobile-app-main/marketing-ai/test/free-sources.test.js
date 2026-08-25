import test from 'node:test';
import assert from 'node:assert/strict';
import { PublicFeedCollector, RedditPublicFeedCollector, validateFeed } from '../src/core/free-sources.js';
import { ProductFitClassifier } from '../src/core/analysis.js';
import { MarketingResponseGenerator } from '../src/core/content.js';

const atom='<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Cannot find a photo</title><link href="https://forum.example/t/1"/><updated>2026-08-20T00:00:00Z</updated><summary>I cannot find a photo in my library without the date.</summary></entry></feed>';
test('validates and ingests a configured public Atom feed with provenance',async()=>{
  const fetchImpl=async()=>({ok:true,status:200,headers:{get:()=> 'application/atom+xml'},text:async()=>atom});
  const checked=await validateFeed({name:'Test',url:'https://forum.example/feed.atom'},fetchImpl);assert.equal(checked.status,'WORKING');assert.equal(checked.format,'ATOM');assert.equal(checked.items,1);
  const collector=new PublicFeedCollector({urls:['https://forum.example/feed.atom']},fetchImpl);const items=await collector.collectPublicOpportunities();assert.equal(items[0].sourceDomain,'forum.example');assert.match(items[0].providerProvenance,/RSS_FORUM/);
});
test('product entailment rejects technical photo false positives and accepts description search',()=>{
  const fit=new ProductFitClassifier();
  for(const text of ['digiKam cannot find photos after upgrading to a larger hard drive','rclone temp-dir vs workdir error','total size of all photos between two dates','where are original photos in Samsung Gallery stored']){const result=fit.classify(text);assert.equal(result.entailment,'NO',text);assert.ok(result.productFitScore<80);}
  const good=fit.classify("I remember what is in the photo but don't remember when I took it");assert.equal(good.entailment,'YES');assert.ok(good.productFitScore>=80);assert.ok(good.honorableFeature);
});
test('local waitlist URLs are blocked and never appear in reply copy',()=>{
  const generator=new MarketingResponseGenerator('http://127.0.0.1:4173/');const reply=generator.generateResponse({platform:'Reddit',painPoint:'DATE_UNKNOWN',supportedSearchNeed:'UNKNOWN_DATE_SEARCH'});assert.equal(generator.outreachStatus(),'BLOCKED_NO_PUBLIC_WAITLIST_URL');assert.doesNotMatch(reply,/127\.0\.0\.1|localhost/);assert.match(reply,/LINK NOT PUBLISHABLE/);
});
test('Reddit 429 enters cooldown and does not retry',async()=>{
  let calls=0;const fetchImpl=async()=>{calls++;return{ok:false,status:429};};const collector=new RedditPublicFeedCollector(true,fetchImpl,{dataDir:await import('node:fs/promises').then(async fs=>fs.mkdtemp('/tmp/honorable-reddit-'))});
  assert.deepEqual(await collector.collectPublicOpportunities(),[]);assert.deepEqual(await collector.collectPublicOpportunities(),[]);assert.equal(calls,1);assert.equal(collector.report().status,'RATE_LIMITED');
});
