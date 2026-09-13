import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chooseWork} from '../automatic.js';
test('selects an exact novel instead of its adaptation',()=>{
 const novel={id:'Q1',label:'Pride and Prejudice',description:'1813 novel by Jane Austen'};
 assert.equal(chooseWork([{id:'Q2',label:novel.label,description:'film adaptation'},novel],'pride-and-prejudice'),novel);
 assert.equal(chooseWork([novel,{...novel,id:'Q3'}],novel.label),null);
 assert.equal(chooseWork([novel],'Different book'),null);
});
test('accepts matched aliases and novel disambiguation, rejects an ambiguous title',()=>{
 const work={id:'Q10',label:'Example (novel)',description:'fantasy novel',match:{text:'Another Name'}};
 assert.equal(chooseWork([work],'Example'),work);
 assert.equal(chooseWork([work],'Another Name'),work);
 assert.equal(chooseWork([{...work,description:'film based on a novel'}],'Example'),null);
});
