import test from 'node:test';
import assert from 'node:assert/strict';
import { findArtwork } from '../object-finder.mjs';
const elements=[{id:'a',type:'rectangle'},{id:'b',type:'text',text:'Café\nR&D 🙂'},{id:'deleted',type:'text',text:'Old',isDeleted:true},{id:'c',type:'image',locked:true},{id:'d',type:'text',text:'Wrapped\nlabel',originalText:'Original full label',opacity:0}];
test('finder normalizes case, whitespace and canonical Unicode without regex interpretation',()=>{
 assert.deepEqual(findArtwork(elements,'CAFE\u0301  R&D 🙂').map(r=>r.element.id),['b']);
 assert.equal(findArtwork(elements,'[.*]').length,0);assert.equal(findArtwork(elements,'full label')[0].element.id,'d');assert.equal(findArtwork(elements,'Wrapped').length,0);
});
test('type filters and stable numbers preserve original front-to-back order',()=>{
 assert.deepEqual(findArtwork(elements).map(r=>[r.element.id,r.number]),[['d',4],['c',3],['b',2],['a',1]]);
 assert.equal(findArtwork(elements,'photo','photos')[0].number,3);assert.deepEqual(findArtwork(elements,'','shapes').map(r=>r.element.id),['a']);assert.deepEqual(findArtwork(elements,'','text').map(r=>r.number),[4,2]);
});
test('locked/hidden metadata and original artwork stay unchanged with no asset or query leakage',()=>{
 const before=structuredClone(elements);const result=findArtwork(elements);assert.equal(result[0].hidden,true);assert.equal(result[1].element.locked,true);assert.deepEqual(elements,before);assert(!('searchable'in result[0]));assert.equal(result[1].label,'photo 3');
 const label=findArtwork([{id:'emoji',type:'text',text:'a'.repeat(39)+'🙂end'}])[0].label;assert.equal(Array.from(label).length,40);assert(label.endsWith('🙂'));
 assert.equal(findArtwork([{id:'hidden',type:'rectangle',customData:{nobleHidden:{opacity:100,locked:false}}}])[0].hidden,true);
});
test('bounded lists/queries and unsupported filters fail explicitly',()=>{
 assert.equal(findArtwork(Array.from({length:500},(_,i)=>({id:String(i),type:'rectangle'}))).length,500);
 assert.throws(()=>findArtwork(Array(501).fill(elements[0])));assert.throws(()=>findArtwork(elements,'x'.repeat(101)));assert.throws(()=>findArtwork(elements,'','remote'));assert.throws(()=>findArtwork([null]));assert.throws(()=>findArtwork(elements,null));
});
