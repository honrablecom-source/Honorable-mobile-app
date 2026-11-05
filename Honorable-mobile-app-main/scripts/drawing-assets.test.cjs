const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {MANIFEST,collectDrawingInputs,recordDrawingAssets,verifyDrawingAssets} = require('./drawing-assets.cjs');
function put(root,name,value) { const file=path.join(root,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,value); }
function fixture(t) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'noble-drawing-assets-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const source=path.join(root,'source'),assets=path.join(root,'assets');
  for(const name of ['editor.jsx','honorable.css','build.mjs','package.json','package-lock.json','helper.mjs'])put(source,name,'source '+name);
  put(source,'THIRD_PARTY/Notice.txt','Synthetic notice');
  for(const name of ['editor.js','editor.css','index.html','config.js','fonts/font.woff2','data/chunk.js'])put(assets,name,'asset '+name);
  put(assets,'THIRD_PARTY/Notice.txt','Synthetic notice');
  const before=collectDrawingInputs(source);recordDrawingAssets(source,assets,before);
  return {root,source,assets,before};
}
const python = process.platform === 'win32' ? 'python' : 'python3';
const verifier = path.join(__dirname,'verify-drawing-apk.py');
function zipFixture(assets,apk,mutation='') {
  const result=spawnSync(python,['-c',`import sys,zipfile,warnings\nfrom pathlib import Path\nwarnings.simplefilter('ignore',UserWarning)\nroot=Path(sys.argv[1]);mode=sys.argv[3]\nwith zipfile.ZipFile(sys.argv[2],'w') as package:\n for file in sorted(root.rglob('*')):\n  if file.is_file():\n   name='assets/drawing/'+file.relative_to(root).as_posix()\n   if mode=='missing' and file.name=='editor.css':continue\n   data=file.read_bytes()\n   if mode=='changed' and file.name=='editor.js':data=b'X'+data[1:]\n   if mode=='manifest' and file.name=='noble-drawing-manifest.json':data=b'{}'\n   package.writestr(name,data)\n if mode=='duplicate':package.writestr('assets/drawing/editor.js',b'duplicate')\n if mode=='extra':package.writestr('assets/drawing/unexpected.js',b'extra')`,assets,apk,mutation],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
}
function checkApk(assets,apk) { return spawnSync(python,[verifier,'--assets',assets,apk],{encoding:'utf8'}); }
test('generated manifest binds current source,helper modules,notices and exact output bytes',t=>{
 const {source,assets}=fixture(t),manifest=verifyDrawingAssets(source,assets);assert.equal(manifest.version,1);assert(manifest.source.files['helper.mjs']);assert(manifest.assets['fonts/font.woff2']);assert(!manifest.assets[MANIFEST]);assert(Object.keys(manifest.source.files).every(name=>!path.isAbsolute(name)));assert.equal(JSON.stringify(manifest).includes(source),false);
});
test('source additions/changes/removal and changes during generation are refused',t=>{
 for(const mode of ['change','add','remove']){const {source,assets}=fixture(t);if(mode==='change')put(source,'helper.mjs','X'+fs.readFileSync(path.join(source,'helper.mjs'),'utf8').slice(1));if(mode==='add')put(source,'new-helper.jsx','new');if(mode==='remove')fs.unlinkSync(path.join(source,'helper.mjs'));assert.throws(()=>verifyDrawingAssets(source,assets),/sources changed/);}
 const {source,assets,before}=fixture(t);put(source,'editor.jsx','changed during compilation');assert.throws(()=>recordDrawingAssets(source,assets,before),/during generation/);
});
test('changed,missing,extra or empty core assets and missing notices are refused',t=>{
 for(const mode of ['change','remove','extra','empty']){const {source,assets}=fixture(t);if(mode==='change')put(assets,'editor.js','changed');if(mode==='remove')fs.unlinkSync(path.join(assets,'editor.css'));if(mode==='extra')put(assets,'stale.js','stale');if(mode==='empty')put(assets,'editor.js','');assert.throws(()=>verifyDrawingAssets(source,assets));}
 const {source,assets}=fixture(t);fs.unlinkSync(path.join(assets,'THIRD_PARTY/Notice.txt'));assert.throws(()=>recordDrawingAssets(source,assets,collectDrawingInputs(source)),/notice/);
});
test('private dotfiles/dependencies are excluded from source and links are rejected',t=>{
 const {root,source,assets}=fixture(t);put(source,'.env.json','SYNTHETIC_PRIVATE_VALUE');put(source,'node_modules/private-package/index.js','SYNTHETIC_PRIVATE_VALUE');assert.doesNotThrow(()=>verifyDrawingAssets(source,assets));assert(!collectDrawingInputs(source).files['.env.json']);
 const target=path.join(root,'outside.js');fs.writeFileSync(target,'outside');try{fs.symlinkSync(target,path.join(source,'linked.mjs'));}catch(error){if(['EPERM','EACCES'].includes(error.code)){t.skip('Symlink creation unavailable');return;}throw error;}assert.throws(()=>collectDrawingInputs(source),/links/);fs.unlinkSync(path.join(source,'linked.mjs'));fs.symlinkSync(target,path.join(assets,'linked.js'));assert.throws(()=>verifyDrawingAssets(source,assets),/links/);
});
test('missing,future,corrupt and linked manifests fail closed',t=>{
 for(const raw of [null,'bad',JSON.stringify({version:2,source:{},assets:{}})]){const {source,assets}=fixture(t);if(raw===null)fs.unlinkSync(path.join(assets,MANIFEST));else put(assets,MANIFEST,raw);assert.throws(()=>verifyDrawingAssets(source,assets));}
 const {root,source,assets}=fixture(t);const outside=path.join(root,'manifest.json');fs.renameSync(path.join(assets,MANIFEST),outside);try{fs.symlinkSync(outside,path.join(assets,MANIFEST));}catch(error){if(['EPERM','EACCES'].includes(error.code)){t.skip('Symlink creation unavailable');return;}throw error;}assert.throws(()=>verifyDrawingAssets(source,assets),/regular/);
});
test('APK-shaped ZIP verifier accepts exact drawing bytes but does not certify an Android package',t=>{
 const {root,assets}=fixture(t),apk=path.join(root,'synthetic.apk');zipFixture(assets,apk);const result=checkApk(assets,apk);assert.equal(result.status,0,result.stderr);assert.match(result.stdout,/signing and device acceptance are separate/);
});
test('APK-shaped ZIP corruption,omissions,extra files,manifest mismatch and duplicates fail',t=>{
 const {root,assets}=fixture(t);for(const mode of ['changed','missing','manifest','duplicate','extra']){const apk=path.join(root,mode+'.apk');zipFixture(assets,apk,mode);const result=checkApk(assets,apk);assert.notEqual(result.status,0,mode+' must fail');}
 const emptyAssets=path.join(root,'empty-assets');put(emptyAssets,MANIFEST,JSON.stringify({version:1,assets:{}}));const fake=path.join(root,'manifest-only.zip');zipFixture(emptyAssets,fake);assert.notEqual(checkApk(emptyAssets,fake).status,0);
});
test('APK scanner rejects an empty directory and the Node CLI rejects stale sources',t=>{
 const {root,source,assets}=fixture(t);const empty=path.join(root,'empty');fs.mkdirSync(empty);assert.notEqual(spawnSync(python,[verifier,'--assets',assets,'--scan',empty],{encoding:'utf8'}).status,0);
 put(source,'editor.jsx','new source');const result=spawnSync(process.execPath,[path.join(__dirname,'drawing-assets.cjs'),source,assets],{encoding:'utf8'});assert.notEqual(result.status,0);assert.match(result.stderr,/sources changed/);
});
