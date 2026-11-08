import test from 'node:test';
import assert from 'node:assert/strict';
import { svgNumbers, multiplySvgMatrices, svgTransform, svgViewport, svgPaint, readVectorSvg } from '../svg-import-source.mjs';
const near=(a,b)=>assert(Math.abs(a-b)<1e-8,`${a} differs from ${b}`);
test('SVG numeric lists preserve signs/exponents and reject units, junk and overflow',()=>{
  assert.deepEqual(svgNumbers('10-2,+.5 1e2'),[10,-2,.5,100]);
  for(const value of ['1px','1e999','1,2!', 'NaN','Infinity','1000001']) assert.throws(()=>svgNumbers(value));
  assert.throws(()=>svgNumbers('1 2',3));
});
test('SVG transform order and pivot rotations follow affine matrix composition',()=>{
  assert.deepEqual(svgTransform('translate(10 20) scale(2)'),[2,0,0,2,10,20]);
  const rotation=svgTransform('rotate(90 10 20)');[0,1,-1,0,30,10].forEach((v,i)=>near(rotation[i],v));
  assert.deepEqual(multiplySvgMatrices([1,0,0,1,5,6],[2,0,0,3,0,0]),[2,0,0,3,5,6]);
  near(svgTransform('skewX(45)')[2],1);near(svgTransform('skewY(45)')[1],1);
  for(const value of ['scale()','matrix(1 2)','perspective(2)','translate(1) garbage','rotate(1 2)','scale(1e999)','skewX(90)']) assert.throws(()=>svgTransform(value));
});
test('viewport defaults, meet, slice and nonuniform mapping preserve target proportions',()=>{
  assert.deepEqual(svgViewport('10 20 100 200',null,null),{width:256,height:512,matrix:[2.56,0,0,2.56,-25.6,-51.2]});
  assert.deepEqual(svgViewport('0 0 100 100','200px','100px'),{width:512,height:256,matrix:[2.56,0,0,2.56,128,0]});
  assert.deepEqual(svgViewport('0 0 100 100','200','100','none').matrix,[5.12,0,0,2.56,-0,-0]);
  assert.deepEqual(svgViewport('0 0 100 100','200','100','xMaxYMax slice').matrix,[5.12,0,0,5.12,0,-256]);
  for(const args of [[null,null,null],['0 0 0 10',null,null],['0 0 10 10','100%','10'],['0 0 10 10','-1','10'],['0 0 10 10','1','10000'],['0 0 10 10',null,null,'banana']]) assert.throws(()=>svgViewport(...args));
});
test('paint normalization retains explicit alpha and refuses resource-dependent colors',()=>{
  assert.equal(svgPaint('#aBc'),'#aabbcc');assert.equal(svgPaint('#AbC8'),'#aabbcc88');assert.equal(svgPaint('none'),'transparent');assert.equal(svgPaint('red',()=> '#ff0000'),'#ff0000');
  let calls=0;for(const value of ['url(#paint)','var(--paint)','currentColor','color(display-p3 1 0 0)']) assert.throws(()=>svgPaint(value,()=>{calls++;return '#ff0000';}));
  // currentColor is resolved against inherited SVG color by the reader, not by this helper.
  assert.equal(calls,0);assert.throws(()=>svgPaint('notacolor',()=> 'notacolor'));
});
test('document preflight rejects empty, oversized, DTD, entities and non-XML declarations',()=>{
  for(const text of ['', 'x'.repeat(65537),'<!DOCTYPE svg><svg/>','<!ENTITY x "a">','<?xml-stylesheet href="https://invalid.test/x"?><svg/>']) assert.throws(()=>readVectorSvg(text));
  assert.throws(()=>readVectorSvg('<svg/>',{Parser:null}),/unavailable/);
});
