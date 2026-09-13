import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseCharacterSection,wikipediaCharacters} from '../wiki-characters.js';
import {shieldRule,toggleShield,clearShield} from '../shield.js';
test('extracts explicit character entries, unlinked names and descriptions safely',()=>{
 const entries=parseCharacterSection("* '''[[Elizabeth Bennet]]''' – The protagonist.\n* '''Mrs. Bennet''' — Her mother.<ref>citation</ref>\nOrdinary paragraph mentioning London.\n* [[Charles Bingley|Bingley]]: A friend.\n* '''Mrs. Bennet''' — duplicate",'Q1','Example');
 assert.deepEqual(entries.map(e=>e.name),['Elizabeth Bennet','Mrs. Bennet','Bingley']);assert.equal(entries[1].description,'Her mother.');assert.ok(entries.every(e=>e.localDetails.wikipedia.startsWith('https://en.wikipedia.org/')));
});
test('reads character sections but excludes unrelated sections',async()=>{
 const seen=[];globalThis.fetch=async url=>{seen.push(url.searchParams.get('section'));return{ok:true,json:async()=>url.searchParams.get('prop')==='sections|links'?{parse:{sections:[{line:'Characters',index:'2',number:'2'},{line:'Minor characters',index:'3',number:'2.1'},{line:'Reception',index:'4',number:'3'}]}}:{parse:{wikitext:{'*':"* '''Alice''' — A fictional character."}}}};};
 assert.equal((await wikipediaCharacters('Example','Q1')).length,1);assert.deepEqual(seen,[null,'2']);
});
test('strict shield is scoped to one tab and cleaned up on toggle/close',async()=>{
 assert.deepEqual(shieldRule(1,7,'reader.example').condition,{tabIds:[7],excludedRequestDomains:['reader.example'],resourceTypes:['main_frame','sub_frame']});
 let store={},rules=[];globalThis.chrome={storage:{session:{get:async()=>store,set:async value=>Object.assign(store,value),remove:async key=>delete store[key]}},action:{setBadgeText:async()=>{},setBadgeBackgroundColor:async()=>{}},declarativeNetRequest:{getSessionRules:async()=>rules,updateSessionRules:async change=>{rules=rules.filter(rule=>!change.removeRuleIds?.includes(rule.id)).concat(change.addRules||[]);}}};
 await toggleShield({id:7,url:'https://reader.example/chapter-1'});assert.equal(rules.length,1);await toggleShield({id:7,url:'https://reader.example/chapter-1'});assert.equal(rules.length,0);
 await toggleShield({id:7,url:'https://reader.example/chapter-1'});await clearShield(7);assert.equal(rules.length,0);
});
