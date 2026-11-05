const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { chromium } = require('../../Honorable-mobile-app-main/ui-previews/node_modules/playwright');
(async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'noble-chat-browser-'));
  process.env.NOBLE_AGENT_BOARD_DIR = dir;
  const { createChatServer } = require('./server.cjs');
  const server = createChatServer();
  await fs.writeFile(path.join(dir, 'state.json'), JSON.stringify({version:1,claims:[{id:'fixture-claim',agent:'noble-fast',at:new Date().toISOString(),message:'Synthetic browser test task',scopes:['src/example.ts']}],events:[{at:new Date().toISOString(),agent:'noble-main',kind:'say',message:'Synthetic verification <script>window.injected=true</script>'}]}));
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:960}}), errors=[],external=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/*',route=>{if(!route.request().url().startsWith(url)){external.push(route.request().url());return route.abort();}return route.continue();});
    await page.goto(url);
    await page.getByText('Connected · checks every 2 seconds',{exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>window.injected),undefined);
    await page.getByRole('button',{name:'Invite an agent'}).click();
    assert((await page.locator('#agent-prompt').innerText()).includes('Identify yourself as noble-fast'));
    await page.getByRole('button',{name:'Invite an agent'}).click();
    await page.getByLabel('Message the team as owner').fill('Synthetic owner message 🟢');
    await page.route('**/api/messages',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({error:'Test interruption'})}));
    await page.getByRole('button',{name:'Send message',exact:true}).click();
    await page.getByText('Test interruption Your draft is kept.',{exact:true}).waitFor();
    assert.equal(await page.getByLabel('Message the team as owner').inputValue(),'Synthetic owner message 🟢');
    await page.unroute('**/api/messages');
    await page.getByRole('button',{name:'Send message',exact:true}).click();
    await page.locator('.bubble').filter({hasText:'Synthetic owner message 🟢'}).waitFor();
    assert.equal(await page.getByLabel('Message the team as owner').inputValue(),'');
    const state=JSON.parse(await fs.readFile(path.join(dir,'state.json'),'utf8'));
    assert.equal(state.events.at(-1).agent,'owner');
    state.events.push({at:new Date().toISOString(),agent:'noble-fast',kind:'say',message:'Synthetic live update'});
    await fs.writeFile(path.join(dir,'state.json'),JSON.stringify(state));
    await page.getByText('Synthetic live update',{exact:true}).waitFor();
    for(const width of [320,390,768,1440]){
      await page.setViewportSize({width,height:844});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`overflow at ${width}`);
      if(width===320){
        await page.getByRole('button',{name:'1 active tasks',exact:true}).click();
        await page.locator('#mobile-claims').getByText('Synthetic browser test task',{exact:true}).waitFor();
        await page.getByRole('button',{name:'1 active tasks',exact:true}).click();
        await page.screenshot({path:path.resolve('.context/agent-chat/mobile-320.png')});
      }
    }
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    const live=await browser.newPage({viewport:{width:1440,height:960}});
    await live.goto('http://127.0.0.1:4180');await live.getByText('Connected · checks every 2 seconds',{exact:true}).waitFor();
    await live.screenshot({path:path.resolve('.context/agent-chat/live-desktop.png')});
    console.log('PASS: actual chat UI; owner save; failure preserves draft; live agent update; script text stays inert; prompt; active tasks on mobile; 320/390/768/1440px; no page errors/external requests. Isolated fixtures never modify the real board. Live read-only screenshot captured.');
  } finally {await browser.close();await new Promise(resolve=>server.close(resolve));await fs.rm(dir,{recursive:true,force:true});}
})().catch(error=>{console.error(error);process.exitCode=1;});
