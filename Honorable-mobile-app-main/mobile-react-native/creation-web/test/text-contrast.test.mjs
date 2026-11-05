import test from 'node:test';
import assert from 'node:assert/strict';
import { textColorRatio, reviewTextColors, formatContrastRatio } from '../text-contrast.mjs';
test('W3C sRGB reference pairs give expected ratios and preserve symmetry for opaque colors',()=>{
 assert.equal(textColorRatio('#000','#fff'),21);assert.equal(textColorRatio('#fff','#000'),21);assert.equal(textColorRatio('#123456','#123456'),1);
 assert(Math.abs(textColorRatio('#777777','#ffffff')-4.478089453577214)<1e-12);assert(Math.abs(textColorRatio('#767676','#ffffff')-4.542224959605253)<1e-12);
 assert(Math.abs(textColorRatio('#f00','#fff')-3.9984767707539985)<1e-12);
});
test('source alpha and object opacity composite against the chosen background without channel rounding',()=>{
 assert.equal(textColorRatio('transparent','#fff'),1);assert(Math.abs(textColorRatio('#000','#fff',50)-3.976653024912438)<1e-12);
 assert.equal(textColorRatio('#0008','#fff'),textColorRatio('#00000088','#ffffff'));assert(textColorRatio('#00000080','#fff',50)<textColorRatio('#00000080','#fff'));
 assert.equal(textColorRatio('#ffffff00','#000'),1);
});
test('threshold classification uses raw ratios and does not promote rounded borderline values',()=>{
 const rows=reviewTextColors([{id:'a',type:'text',text:'Below',strokeColor:'#777'},{id:'b',type:'text',text:'Above',strokeColor:'#767676'}],'#fff');assert.equal(rows[0].meetsReference,false);assert.equal(rows[1].meetsReference,true);assert.equal(formatContrastRatio(4.499),'<4.50');assert.equal(formatContrastRatio(4.5),'4.50');
});
test('review excludes hidden/deleted text,keeps stable IDs/numbers and never changes source',()=>{
 const source=[{id:'shape',type:'rectangle'},{id:'a',type:'text',text:'Wrapped',originalText:'Original 🙂',strokeColor:'#fff',locked:true},{id:'h',type:'text',text:'Hidden',opacity:0},{id:'d',type:'text',text:'Deleted',isDeleted:true},{id:'n',type:'text',text:'Hidden',customData:{nobleHidden:{opacity:100,locked:false}}}];const before=structuredClone(source),rows=reviewTextColors(source,'#000');assert.equal(rows.length,1);assert.equal(rows[0].number,2);assert.equal(rows[0].label,'Original 🙂');assert.equal(rows[0].ratio,21);assert.deepEqual(source,before);
});
test('invalid backgrounds/opacity/bounds fail explicitly and unsupported text colors are reported',()=>{
 for(const background of ['transparent','#fff0','#ffffff80','red',''])assert.throws(()=>reviewTextColors([],background));for(const opacity of [-1,101,NaN])assert.throws(()=>textColorRatio('#000','#fff',opacity));
 assert.throws(()=>reviewTextColors(Array(501).fill({type:'text'}),'#fff'));assert.throws(()=>reviewTextColors([null],'#fff'));assert.equal(reviewTextColors([{id:'bad',type:'text',strokeColor:'remote'}],'#fff')[0].ratio,null);
});
