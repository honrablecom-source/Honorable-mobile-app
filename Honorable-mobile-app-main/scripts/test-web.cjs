const {chromium}=require('../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});const page=await browser.newPage({viewport:{width:1366,height:768}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto('http://localhost:4174');await require('./test-browser-login.cjs')(page);await page.waitForSelector('[data-test-screen=pass]');
for(const tab of ['home','memories','terms','activity','settings']){await page.locator(`.honorable-dock [data-htab=${tab}]`).click();assert.equal(await page.locator(`.honorable-dock [data-htab=${tab}].active`).count(),1)}
await page.locator('[data-test-screen=pass]').click();await page.waitForSelector('#sign-out');
await page.locator('[data-purchase=normal]').click();await page.waitForFunction(()=>accountState?.balance===50);await page.reload();await page.waitForFunction(()=>accountState?.balance===50);
await page.locator('[data-test-screen=usage]').click();
await page.locator('#media-input').setInputFiles({name:'web-ui-test.jpg',mimeType:'image/jpeg',buffer:require('node:fs').readFileSync(require('node:path').resolve(__dirname,'../android-app/test-lab/web-test-shell/storage/real-expanded/real-01.jpg'))});
await page.waitForFunction(()=>!webBusy,{},{timeout:180000});assert.equal(await page.evaluate(()=>webNotice),'');assert(await page.evaluate(()=>state.media.some(x=>x.name.includes('web-ui-test'))));
await page.evaluate(async()=>{const blob=await(await fetch('/media/video-expanded/video-01.mp4')).blob();const transfer=new DataTransfer();transfer.items.add(new File([blob],'web-ui-video.mp4',{type:'video/mp4'}));document.querySelector('#drop-media').dispatchEvent(new DragEvent('drop',{bubbles:true,dataTransfer:transfer}))});
await page.waitForFunction(()=>!webBusy,{},{timeout:180000});assert.equal(await page.evaluate(()=>webNotice),'');assert(await page.evaluate(()=>state.media.some(x=>x.name.includes('web-ui-video'))));
const realSearch=await page.evaluate(async()=>{state.query='beach';await runSearch();return state.results});assert(realSearch.results.some(x=>x.type==='IMAGE'));assert(realSearch.results.some(x=>x.type==='VIDEO'));
await page.locator('[data-go=gallery]').click();await page.locator('[data-open]').first().click();await page.waitForSelector('.viewer-media');
// Deterministic UI-only result fixture verifies filtered selection and timestamp preservation.
await page.evaluate(()=>{state.app='honorable';state.honorableTab='memories';state.resultFilter='Videos';const photo=state.media.find(x=>x.type==='IMAGE');const video=state.media.find(x=>x.type==='VIDEO');if(!video)throw Error('Missing video fixture');state.results={confident:true,results:[{...photo,score:.9},{...video,timestamp:1000,score:.8}]};render()});
await page.locator('[data-a-result="0"]').first().click();assert((await page.locator('.viewer-media').getAttribute('src')).includes('#t=1'));
for(const viewport of [{width:1366,height:768},{width:768,height:600},{width:360,height:640}]){await page.setViewportSize(viewport);await page.waitForTimeout(100);assert(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight));const phone=await page.locator('#phone').boundingBox();assert(phone.y>=25 && phone.y+phone.height<=viewport.height+1)}
assert.deepEqual(errors,[]);await browser.close();console.log('PASS: five Android tabs, account, simulated purchase, session reload, import/index, viewer, filtered video timestamp, Chromebook scaling');
})().catch(e=>{console.error(e);process.exit(1)});
