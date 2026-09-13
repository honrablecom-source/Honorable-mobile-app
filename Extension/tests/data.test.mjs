import {test} from 'node:test';
import assert from 'node:assert/strict';
import {api,linkedIds,loadBook,toEntry} from '../data.js';
test('ignores deprecated and unknown claims; deduplicates IDs',()=>{assert.deepEqual(linkedIds({claims:{P674:[{mainsnak:{datavalue:{value:{id:'Q1'}}}},{rank:'deprecated',mainsnak:{datavalue:{value:{id:'Q2'}}}},{mainsnak:{}},{mainsnak:{datavalue:{value:{id:'Q1'}}}}]}},'P674'),['Q1']);});
test('missing labels omitted and Wikipedia titles encoded',()=>{assert.equal(toEntry({id:'Q1'},'place'),null);assert.match(toEntry({id:'Q1',labels:{en:{value:'A'}},sitelinks:{enwiki:{title:'A & B'}}},'place').wikipedia,/A_%26_B/);});
test('rate limits have a useful error',async()=>{globalThis.fetch=async()=>({status:429,ok:false});await assert.rejects(api({}),/wait a minute/);});
test('book loads linked entities and cache prevents duplicate requests',async()=>{
 let store={},calls=0;globalThis.chrome={storage:{local:{get:async()=>store,set:async value=>{store={...store,...value};}}}};
 globalThis.fetch=async url=>{calls++;const id=url.searchParams.get('ids');return {ok:true,json:async()=>({entities:id==='Q10'?{Q10:{id:'Q10',labels:{en:{value:'Book'}},claims:{P674:[{mainsnak:{datavalue:{value:{id:'Q20'}}}}]}}}:{Q20:{id:'Q20',labels:{en:{value:'Person'}}}}})};};
 const first=await loadBook('Q10');assert.equal(first.entries[0].kind,'character');assert.equal(calls,2);assert.deepEqual(await loadBook('Q10'),first);assert.equal(calls,2);
});
