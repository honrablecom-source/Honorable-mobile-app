const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
(async()=>{
 const{selectiveKernel,DEFAULT_SELECTIVE}=await import('./selective-color.mjs');
 const kernel=selectiveKernel({...DEFAULT_SELECTIVE,enabled:true,hue:0,radius:20,softness:0,shift:120});
 assert.equal(kernel(0x7fff0000),0x7f00ff00);assert.equal(kernel(0xff0000ff)|0,0xff0000ff|0);assert.equal(kernel(0xff777777)|0,0xff777777|0);assert.equal(kernel(0x00123456),0x00123456);
 assert.throws(()=>selectiveKernel({...DEFAULT_SELECTIVE,hue:NaN}));
 const file=path.resolve(__dirname,'../../../.context/noble-editor-preview/region-models.js');
 if(fs.existsSync(file)){
  const scope={window:{}};vm.runInNewContext(fs.readFileSync(file,'utf8'),scope);const models=scope.window.NOBLE_REGION_MODELS,expected=models.find(value=>value.name==='Selective red range shifted to green');assert(expected,'Generate the current Kotlin selective-color review fixtures first.');
  const selected=selectiveKernel({...DEFAULT_SELECTIVE,enabled:true,hue:0,radius:35,softness:.5,shift:120});let differing=0,maxDifference=0;
  models[0].pixels.forEach((pixel,index)=>{const actual=selected(pixel),wanted=expected.pixels[index];assert.equal(actual>>>24,wanted>>>24);for(const shift of[16,8,0]){const d=Math.abs((actual>>>shift&255)-(wanted>>>shift&255));if(d)differing++;maxDifference=Math.max(maxDifference,d);}});
  assert.equal(maxDifference,0,`Browser/Kotlin pixel mismatch; ${differing} channels differed`);
  console.log(`PASS: browser selective-color kernel matches all ${expected.pixels.length} production Kotlin sample pixels exactly, including alpha; canonical neutral/outside/transparent inputs and invalid values checked. Android bitmap/color management still pending.`);
 }else console.log('PASS: canonical browser selective-color cases; Kotlin fixture comparison unavailable until native model review generation.');
})().catch(error=>{console.error(error);process.exitCode=1;});
