import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadDetails} from '../details.js';
test('loads biography, image source and first work without inventing a chapter; caches result',async()=>{
 let store={},calls=0;
 globalThis.chrome={storage:{local:{get:async()=>store,set:async value=>Object.assign(store,value)}}};
 globalThis.fetch=async url=>{calls++;return{ok:true,json:async()=>url.hostname==='en.wikipedia.org'?{query:{pages:{1:{title:'Test character',extract:'A detailed biography.',thumbnail:{source:'https://upload.wikimedia.org/example.jpg'},pageimage:'Example.jpg'}}}}:url.searchParams.get('ids')==='Q1'?{entities:{Q1:{claims:{P4584:[{mainsnak:{datavalue:{value:{id:'Q2'}}}}]},sitelinks:{enwiki:{title:'Test character'}}}}}:{entities:{Q2:{labels:{en:{value:'First book'}}}}}};};
 const result=await loadDetails('Q1');assert.equal(result.chapter,null);assert.equal(result.facts[0].value,'First book');assert.equal(result.biography,'A detailed biography.');assert.match(result.image.source,/File:Example.jpg/);await loadDetails('Q1');assert.equal(calls,3);
 await assert.rejects(loadDetails('https://evil.example'),/Invalid/);
});
