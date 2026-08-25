import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { ResilientWebDiscoveryCollector } from '../src/core/web-discovery.js';

const result=(url,engine)=>({title:'Cannot find an old photo',content:"I can't find an old photo because I do not remember the date.",url,engines:[engine],publishedDate:'2026-08-19'});
const settings=async extra=>({dataDir:await fs.mkdtemp(path.join(os.tmpdir(),'honorable-discovery-')),searxng:{baseUrl:'https://search.example',engines:'brave,duckduckgo'},webSearch:{},...extra});

test('SearXNG skips a failed engine and uses the next configured engine',async()=>{
  const calls=[];const fetchImpl=async url=>{calls.push(url.searchParams.get('engines'));const engine=url.searchParams.get('engines');return{ok:true,json:async()=>({results:engine==='duckduckgo'?[result('https://reddit.com/r/photos/1',engine)]:[],unresponsive_engines:engine==='brave'?[[engine,'rate limited']]:[]})};};
  const collector=new ResilientWebDiscoveryCollector(await settings(),fetchImpl);const items=await collector.collectPublicOpportunities({query:'problem query'});
  assert.equal(items.length,1);assert.equal(items[0].providerProvenance,'PUBLIC_WEB / SEARXNG');assert.deepEqual(calls,['brave','duckduckgo','duckduckgo']);
});

test('Google is used when every configured SearXNG engine is degraded',async()=>{
  const configured=await settings({webSearch:{apiKey:'key',engineId:'engine'}});const fetchImpl=async url=>url.pathname.includes('customsearch')?{ok:true,json:async()=>({items:[{title:'Cannot find photo',snippet:"I can't find an old photo and do not know the date.",link:'https://example.com/thread'}]})}:{ok:true,json:async()=>({results:[],unresponsive_engines:[[url.searchParams.get('engines'),'blocked']]})};
  const items=await new ResilientWebDiscoveryCollector(configured,fetchImpl).collectPublicOpportunities({query:'problem query'});
  assert.equal(items.length,1);assert.equal(items[0].providerProvenance,'PUBLIC_WEB / GOOGLE');
});

test('Brave Search API is used only when zero-cost mode is explicitly disabled',async()=>{
  const configured=await settings({zeroCostMode:false,webSearch:{apiKey:'key',engineId:'engine'},braveSearch:{apiKey:'brave-key'}});const calls=[];
  const fetchImpl=async(url,options={})=>{calls.push(url.hostname);if(url.hostname==='www.googleapis.com')return{ok:false,status:429};if(url.hostname==='api.search.brave.com'){assert.equal(options.headers['x-subscription-token'],'brave-key');return{ok:true,json:async()=>({web:{results:[{title:'Cannot find photo',description:"I can't find an old photo and do not know the date.",url:'https://example.com/thread'}]}})};}return{ok:true,json:async()=>({results:[],unresponsive_engines:[[url.searchParams.get('engines'),'blocked']]})};};
  const items=await new ResilientWebDiscoveryCollector(configured,fetchImpl).collectPublicOpportunities({query:'problem query'});
  assert.equal(items.length,1);assert.equal(items[0].providerProvenance,'PUBLIC_WEB / BRAVE_SEARCH');assert.ok(calls.indexOf('www.googleapis.com')<calls.indexOf('api.search.brave.com'));
});

test('missing Google engine id is reported without blocking Brave fallback outside zero-cost mode',async()=>{
  const configured=await settings({zeroCostMode:false,webSearch:{apiKey:'key'},braveSearch:{apiKey:'brave-key'}});const fetchImpl=async url=>url.hostname==='api.search.brave.com'?{ok:true,json:async()=>({web:{results:[{title:'Cannot find photo',description:"I can't find an old photo and do not know the date.",url:'https://example.com/thread'}]}})}:{ok:true,json:async()=>({results:[]})};
  const collector=new ResilientWebDiscoveryCollector(configured,fetchImpl);const items=await collector.collectPublicOpportunities({query:'problem query'});
  assert.equal(collector.lastStatus.google,'MISSING GOOGLE_SEARCH_ENGINE_ID');assert.equal(items[0].providerProvenance,'PUBLIC_WEB / BRAVE_SEARCH');
});

test('zero-cost mode skips a configured Brave key',async()=>{
  const configured=await settings({zeroCostMode:true,braveSearch:{apiKey:'must-not-be-used'}});let braveCalls=0;
  const fetchImpl=async url=>{if(url.hostname==='api.search.brave.com')braveCalls++;return{ok:true,json:async()=>({results:[]})};};
  const collector=new ResilientWebDiscoveryCollector(configured,fetchImpl);await collector.collectPublicOpportunities({query:'problem query'});
  assert.equal(braveCalls,0);assert.equal(collector.lastStatus.otherFallback,'SKIPPED_ZERO_COST_MODE');
});

test('a short-lived successful cache is labeled and used after an outage',async()=>{
  const configured=await settings();const success=async url=>({ok:true,json:async()=>({results:[result('https://reddit.com/r/photos/2',url.searchParams.get('engines'))]})});const first=new ResilientWebDiscoveryCollector(configured,success);await first.collectPublicOpportunities({query:'same query'});
  const outage=async()=>{throw new Error('temporary outage');};const cached=await new ResilientWebDiscoveryCollector(configured,outage).collectPublicOpportunities({query:'same query'});
  assert.equal(cached.length,1);assert.equal(cached[0].providerProvenance,'CACHED_PUBLIC_WEB');
});

test('stale cache is not treated as a live result',async()=>{
  let now=Date.parse('2026-08-20T00:00:00Z');const configured=await settings();const success=async url=>({ok:true,json:async()=>({results:[result('https://reddit.com/r/photos/3',url.searchParams.get('engines'))]})});const first=new ResilientWebDiscoveryCollector(configured,success,{now:()=>now});await first.collectPublicOpportunities({query:'same query'});
  now+=16*60*1000;const outage=async()=>{throw new Error('temporary outage');};const cached=await new ResilientWebDiscoveryCollector(configured,outage,{now:()=>now}).collectPublicOpportunities({query:'same query'});
  assert.deepEqual(cached,[]);
});
