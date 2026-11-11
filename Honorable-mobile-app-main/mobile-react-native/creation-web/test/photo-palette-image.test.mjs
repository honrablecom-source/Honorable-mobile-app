import test from 'node:test';
import assert from 'node:assert/strict';
import {parseImagePalette,paletteImageSettings,paletteImagePixels,paletteImagePixelsAsync} from '../photo-palette-image.mjs';
const solid=(color,width=8,height=8)=>new Uint8ClampedArray(Array.from({length:width*height},()=>color).flat());
const hex=(data,at)=>'#'+Array.from(data.slice(at,at+3),value=>value.toString(16).padStart(2,'0')).join('');
const invariant=result=>{for(let at=0;at<result.data.length;at+=4){assert(result.colors.includes(hex(result.data,at)));assert.equal(result.data[at+3],255);}assert.equal(result.used.reduce((a,b)=>a+b,0),result.width*result.height);};
test('palette parser preserves order, normalizes case and refuses labels, duplicates and arbitrary color expressions',()=>{
 const parsed=parseImagePalette(' #FF0000, #00ff00\n#0000FF ');assert.deepEqual(parsed.map(color=>color.hex),['#ff0000','#00ff00','#0000ff']);assert.deepEqual(parsed[0].rgb,[255,0,0]);for(const input of['','#fff #000','#ffffff','#ffffff #FFFFFF','#000000 rgba(0,0,0,1)','#000000 #ffffff00','Color #000000 #ffffff',Array(17).fill('#000000').join(' '),'x'.repeat(513)])assert.throws(()=>parseImagePalette(input));
});
test('nearest RGB mapping uses only supplied colors and resolves exact ties by input order',()=>{
 const source=solid([128,0,0,255]),a=paletteImagePixels(source,8,8,'#010000 #ff0000',false),b=paletteImagePixels(source,8,8,'#ff0000 #010000',false);assert.equal(hex(a.data,0),'#010000');assert.equal(hex(b.data,0),'#ff0000');invariant(a);invariant(b);
});
test('serpentine error diffusion creates palette dots that approximate intermediate gray without extra colors',()=>{
 const source=solid([128,128,128,255],16,16),plain=paletteImagePixels(source,16,16,'#000000 #ffffff',false),dithered=paletteImagePixels(source,16,16,'#000000 #ffffff',true);assert.equal(plain.used[1],256);assert(dithered.used[0]>100&&dithered.used[1]>100);assert(Math.abs(dithered.used[1]*255/256-128)<8);assert.notDeepEqual(dithered.data,plain.data);invariant(dithered);
});
test('transparency composites onto the first palette color and output is explicitly opaque',()=>{
 const source=solid([255,0,0,0]);source.set([255,0,0,128],4);const result=paletteImagePixels(source,8,8,'#000000 #ff0000',false);assert.deepEqual(Array.from(result.data.slice(0,8)),[0,0,0,255,255,0,0,255]);assert.equal(result.changed,64);invariant(result);
});
test('bounded diffusion preserves caller pixels and palette membership even for extreme out-of-gamut colors',()=>{
 const source=solid([255,255,255,255],64,32),before=new Uint8ClampedArray(source);const result=paletteImagePixels(source,64,32,'#100000 #ff0000',true);invariant(result);assert.deepEqual(source,before);assert.notEqual(source.buffer,result.data.buffer);assert(result.data.every(Number.isFinite));
});
test('already matching opaque colors refuse duplicate output, while alpha changes remain meaningful',()=>{
 assert.throws(()=>paletteImagePixels(solid([0,0,0,255]),8,8,'#000000 #ffffff',true),/already uses/);const result=paletteImagePixels(solid([0,0,0,128]),8,8,'#000000 #ffffff',true);assert.equal(result.changed,64);assert.equal(result.data[3],255);
});
test('invalid dimensions, dithering modes and incomplete pixels refuse before processing',()=>{
 for(const size of[7,1025,8.5,Infinity])assert.throws(()=>paletteImageSettings(size,8,'#000000 #ffffff'));assert.throws(()=>paletteImageSettings(8,8,'#000000 #ffffff','yes'));for(const value of[null,[],new Uint8Array(4)])assert.throws(()=>paletteImagePixels(value,8,8,'#000000 #ffffff'));
});
test('sixteen-color bounds and deterministic generated input preserve strict palette and histogram invariants',()=>{
 const palette=Array.from({length:16},(_,i)=>'#'+[i*17,255-i*17,i*13].map(value=>value.toString(16).padStart(2,'0')).join('')).join('\n'),source=solid([0,0,0,255],19,13);let seed=91;for(let at=0;at<source.length;at++){seed=(Math.imul(seed,1664525)+1013904223)>>>0;source[at]=seed>>>24;}const a=paletteImagePixels(source,19,13,palette,true),b=paletteImagePixels(source,19,13,palette,true);assert.deepEqual(a,b);invariant(a);assert.equal(a.colors.length,16);
});
test('async row boundaries keep diffusion state, snapshot caller and match sync with complete progress',async()=>{
 const source=solid([128,128,128,255],8,24),expected=paletteImagePixels(source,8,24,'#000000 #ffffff',true),progress=[];let turns=0;const result=await paletteImagePixelsAsync(source,8,24,'#000000 #ffffff',true,()=>{},value=>progress.push(value),async()=>{source.fill(0);turns++;});assert.deepEqual(result,expected);assert.equal(turns,3);assert.deepEqual(progress,[1/3,2/3,1]);
});
test('stale ownership and failed progress do not return partial palette pixels',async()=>{
 const source=solid([128,128,128,255],8,24),before=new Uint8ClampedArray(source);let valid=true;await assert.rejects(paletteImagePixelsAsync(source,8,24,'#000000 #ffffff',true,()=>{if(!valid)throw Error('stale');},()=>{},async()=>{valid=false;}),/stale/);await assert.rejects(paletteImagePixelsAsync(source,8,24,'#000000 #ffffff',true,()=>{},()=>{throw Error('closed');},async()=>{}),/closed/);assert.deepEqual(source,before);
});
