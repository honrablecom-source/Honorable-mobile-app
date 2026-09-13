import {test} from 'node:test';
import assert from 'node:assert/strict';
import {wikipediaWork,automaticIndex} from '../automatic.js';
test('Wikipedia article resolves to a verified novel, and cache avoids repeat network calls',async()=>{
 let store={},calls=0;globalThis.chrome={storage:{local:{get:async()=>store,set:async value=>Object.assign(store,value)}}};
 globalThis.fetch=async url=>{calls++;const action=url.searchParams.get('action');let data;
 if(url.hostname==='en.wikipedia.org')data={query:{pages:{1:{pageprops:{wikibase_item:'Q1'}},2:{pageprops:{wikibase_item:'Q99',disambiguation:''}}}}};
 else if(action==='wbsearchentities')data={search:[]};
 else if(url.searchParams.get('props')==='labels|descriptions|aliases')data={entities:{Q1:{id:'Q1',labels:{en:{value:'Example'}},descriptions:{en:{value:'fantasy novel'}}}}};
 else data={entities:{Q1:{id:'Q1',labels:{en:{value:'Example'}},claims:{}}}};
 return {ok:true,json:async()=>data};
 };
 assert.equal((await wikipediaWork('Example')).id,'Q1');
 const book=await automaticIndex(['Example']);assert.equal(book.id,'Q1');const count=calls;await automaticIndex('Example');assert.equal(calls,count);
});
