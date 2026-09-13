const {chromium}=require('../ui-previews/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
const browser=await chromium.launch({args:['--no-sandbox']});
for(const viewport of [{width:1366,height:768},{width:768,height:600},{width:360,height:640}]){
 const context=await browser.newContext({viewport,hasTouch:true});const page=await context.newPage();
 await page.goto('http://localhost:4174');await require('./test-browser-login.cjs')(page);await page.waitForSelector('.honorable-dock');await page.waitForTimeout(800);
 for(const tab of ['home','memories','terms','activity','settings']){
  await page.locator(`.honorable-dock [data-htab=${tab}]`).click();
  const scroll=page.locator('.page');const dock=await page.locator('.honorable-dock').boundingBox();
  const sizes=await scroll.evaluate(x=>({h:x.clientHeight,sh:x.scrollHeight}));
  if(sizes.sh>sizes.h){await scroll.hover();await page.mouse.wheel(0,650);await page.waitForTimeout(200);assert(await scroll.evaluate(x=>x.scrollTop>0),`${tab}: wheel must scroll`)}
  assert.deepEqual(await page.locator('.honorable-dock').boundingBox(),dock,`${tab}: dock moved`);
  assert(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight),'outer page scrolls');
  const tinted=await page.evaluate(()=>[...document.querySelectorAll('.screen *')].filter(el=>!el.closest('#google-signin')).flatMap(el=>['color','backgroundColor','borderTopColor'].flatMap(property=>{const value=getComputedStyle(el)[property];const m=value.match(/^rgba?\((\d+), (\d+), (\d+)(?:, ([\d.]+))?\)$/);return m&&m[4]!=='0'&&(m[1]!==m[2]||m[2]!==m[3])?[`${el.className}:${property}:${value}`]:[]})));
  assert.deepEqual(tinted,[],`${tab}: tinted UI colors`);
 }
 await page.locator('.honorable-dock [data-htab=settings]').click();
 const rect=await page.locator('.page').boundingBox();const cdp=await context.newCDPSession(page);const x=rect.x+rect.width/2,y=rect.y+rect.height*.8;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
 for(let n=1;n<=8;n++){await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x,y:y-n*25}]});await page.waitForTimeout(20)}
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await page.waitForTimeout(200);
 assert(await page.locator('.page').evaluate(el=>el.scrollTop>0),'touch swipe must scroll');
 await page.screenshot({path:`/tmp/honorable-fixed-${viewport.width}.png`});await context.close();
}
await browser.close();console.log('PASS: wheel and touch scrolling, fixed dock, all five Android tabs, neutral UI colors, three viewport sizes');
})().catch(error=>{console.error(error);process.exit(1)});
