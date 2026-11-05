import test from 'node:test';
import assert from 'node:assert/strict';
import { tablePlan } from '../table.mjs';
test('pipe table preserves headers and cells as editable bound-label specs',()=>{
 const result=tablePlan(' Compare ','Option | Price | Notes\nBasic | 10 | Local\nPro | 20 | More');
 assert.equal(result[0].text,'Compare');assert.equal(result.length,10);assert.equal(result.filter(e=>e.backgroundColor==='#eeeeee').length,3);assert.deepEqual(result.slice(1).map(e=>e.label.text),['Option','Price','Notes','Basic','10','Local','Pro','20','More']);
 assert(result.slice(1).every(e=>e.type==='rectangle'&&e.label.fontFamily===2&&e.label.textAlign==='left'));
});
test('pasted tabs and CRLF preserve empty trailing cells and literal formula-looking text',()=>{
 const result=tablePlan('Local','\r\nName\tValue\tNotes\r\nR&D\t=1+2\t\r\n');
 assert.equal(result.length,7);assert.equal(result[5].label.text,'=1+2');assert.equal(result[6].label,undefined);assert.equal(result[4].label.text,'R&D');
});
test('wide Unicode and long tokens receive row height without changing text',()=>{
 const value='作品🙂'.repeat(16);const shapes=tablePlan('🙂','Header|Other\n'+value+'|https://example.com');
 assert.equal(shapes[3].label.text,value);assert(shapes[3].height>64);assert.equal(shapes[3].height,shapes[4].height);assert.equal(shapes[3].y,shapes[1].y+shapes[1].height);assert.equal(shapes[4].label.text,'https://example.com');
 assert.throws(()=>tablePlan('Too long','A|B\n'+'🙂'.repeat(65)+'|C'),/64/);
});
test('maximum table remains below object budget even with separate renderer text objects',()=>{
 const row=Array(6).fill('Cell').join('|'),result=tablePlan('Maximum',Array(10).fill(row).join('\n'));
 assert.equal(result.length,61);assert.equal(result.filter(e=>e.label).length,60);assert.equal(result.length+60,121);
 assert(result.slice(1).every(e=>e.x>=0&&e.x+e.width<=1320&&Number.isFinite(e.y+e.height)));
});
test('ragged, oversized, empty and control-bearing input is refused',()=>{
 for(const input of ['', 'A|B','A|B\nC','A|B\nC|D|E','A\nB',Array(11).fill('A|B').join('\n'),'A|B\n|','A|B\nC\x00|D','A|B\nC\r|D','x'.repeat(4097),Array(7).fill('A').join('|')+'\n'+Array(7).fill('B').join('|')])assert.throws(()=>tablePlan('Table',input));
 for(const title of ['', ' ', 'x'.repeat(61),'Bad\ntitle'])assert.throws(()=>tablePlan(title,'A|B\nC|D'));
});
