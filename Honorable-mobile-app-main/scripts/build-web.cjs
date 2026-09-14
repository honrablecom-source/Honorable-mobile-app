const fs=require('node:fs');const path=require('node:path');const {Script}=require('node:vm');
require('./generate-design.cjs');
const source=path.resolve(__dirname,'../android-app/test-lab/web-test-shell');
const output=path.resolve(__dirname,'../android-app/test-lab/build/web-shell');
fs.mkdirSync(output,{recursive:true});
for(const name of ['beta.js','telemetry.js','studio.js','studio.css','project.js','index.html','auth-ui.js','auth-ui.css','android-icons.js','android-ui.js','Roboto.ttf','Roboto-400.ttf','Roboto-500.ttf','Roboto-600.ttf','Roboto-700.ttf','Roboto-800.ttf','Roboto-900.ttf','Roboto-LICENSE.txt','Material-Icons-LICENSE.txt','phone.js','web-test.js','design-tokens.css','honorable.css','prompt_beach.png','prompt_birthday.png','prompt_red_car.png']){if(name.endsWith('.js'))new Script(fs.readFileSync(path.join(source,name),'utf8'),{filename:name});fs.copyFileSync(path.join(source,name),path.join(output,name))}
console.log('Web shell built (static assets; runtime services launched with npm run dev:test).');
