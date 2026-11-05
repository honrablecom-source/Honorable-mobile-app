import test from'node:test';import assert from'node:assert/strict';import path from'node:path';import{fileURLToPath}from'node:url';import vm from'node:vm';import{buildSync}from'esbuild';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
test('scoped browser compiler settings prevent native strict-emit startup failure in actual perspective dependency',()=>{
 const options={stdin:{contents:"import perspective from 'perspective-transform';window.result=perspective([0,0,10,0,10,10,0,10],[0,0,20,0,20,20,0,20]).transform(3,4);",resolveDir:root,sourcefile:'geometry-fixture.jsx'},bundle:true,write:false,format:'iife',platform:'browser',target:'chrome90'};
 const evaluate=code=>{const context={};context.window=context;vm.runInNewContext(code,context);return Array.from(context.result);};
 const inherited=buildSync({...options,tsconfigRaw:{compilerOptions:{strict:true,jsx:'react-native'}}}).outputFiles[0].text;
 assert.throws(()=>evaluate(inherited),/Cannot set properties of undefined.*numeric/);
 const scoped=buildSync({...options,tsconfig:path.join(root,'tsconfig.json')}).outputFiles[0].text;
 assert.deepEqual(evaluate(scoped),[6,8]);
});
