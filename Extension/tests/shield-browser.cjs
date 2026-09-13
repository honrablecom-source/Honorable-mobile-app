const {chromium}=require('../../Honorable-mobile-app-main/ui-previews/node_modules/playwright');
const http=require('node:http');const path=require('node:path');const assert=require('node:assert/strict');
(async()=>{
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end('<h1>Reading test</h1><p>Loaded chapter.</p>');});await new Promise(resolve=>server.listen(0,'0.0.0.0',resolve));
 const port=server.address().port;let context;
 try{
 context=await chromium.launchPersistentContext('',{channel:'chromium',headless:true,args:['--no-sandbox',`--disable-extensions-except=${path.resolve(__dirname,'..')}`,`--load-extension=${path.resolve(__dirname,'..')}`]});
 const worker=context.serviceWorkers()[0]||await context.waitForEvent('serviceworker');
 const page=await context.newPage();await page.goto(`http://127.0.0.1:${port}/chapter`);
 const tabId=await worker.evaluate(async()=>{const tabs=await chrome.tabs.query({});return tabs.find(tab=>tab.url?.includes('127.0.0.1'))?.id;});
 // content_scripts permissions don't expose tab URLs; locate the active test tab if necessary.
 const target=tabId||await worker.evaluate(async()=>{const tabs=await chrome.tabs.query({});return tabs.at(-1).id;});
 const {shieldRule}=await import('../shield.js');
 await worker.evaluate(async({rule,target})=>{await chrome.declarativeNetRequest.updateSessionRules({addRules:[rule]});await chrome.storage.session.set({['shield-'+target]:{ruleId:rule.id,hostname:'127.0.0.1'}});},{rule:shieldRule(999,target,'127.0.0.1'),target});
 await page.goto(`http://127.0.0.1:${port}/next`);assert.match(await page.title(),/^$/);
 let blocked=false;try{await page.goto(`http://localhost:${port}/redirect`);}catch(error){blocked=/ERR_BLOCKED_BY_CLIENT/.test(error.message);}assert.ok(blocked,'Off-site navigation blocked by Chrome');
 await worker.evaluate(async()=>chrome.declarativeNetRequest.updateSessionRules({removeRuleIds:[999]}));
 await page.goto(`http://localhost:${port}/allowed`);assert.match(await page.locator('h1').innerText(),/Reading/);
 console.log('Native Chromium extension test passed: rule accepted, same-host navigation allowed, off-site navigation blocked, disabling restores navigation.');
 }finally{await context?.close();server.close();}
})().catch(error=>{console.error(error);process.exit(1)});
