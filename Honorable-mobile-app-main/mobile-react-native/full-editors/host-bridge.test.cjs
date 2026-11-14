const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync(require('node:path').join(__dirname,'host-bridge.js'),'utf8');
function host({bytes=Buffer.alloc(70000,42),defer=false}={}){
 const events={},winEvents={},calls=[],notices=[];let release;
 class Element{constructor(url,name){this.href=url;this.download=name;}closest(){return this;}}
 const document={hidden:false,addEventListener:(n,f)=>events[n]=f,body:{appendChild:n=>notices.push(n)},createElement:()=>({style:{},setAttribute(){}})};
 const bridge={beginSave:(...a)=>(calls.push(['begin',...a]),true),appendSave:(...a)=>(calls.push(['append',...a]),true),finishSave:(...a)=>(calls.push(['finish',...a]),true),cancelSave:(...a)=>calls.push(['cancel',...a])};
 const window={NobleCreative:bridge,addEventListener:(n,f)=>winEvents[n]=f};
 const fetch=async()=>{if(defer)await new Promise(r=>release=r);return {ok:true,blob:async()=>new Blob([bytes])}};
 vm.runInNewContext(source,{window,document,Element,location:{href:'https://noble-editors.local/image/index.html',origin:'https://noble-editors.local'},URL,fetch,Uint8Array,Blob,btoa:s=>Buffer.from(s,'binary').toString('base64'),Math});
 return {calls,notices,document,events,window,release:()=>release(),click:(url='blob:https://noble-editors.local/copy',name='Edited.png')=>{let prevented=false;const promise=events.click({target:new Element(url,name),preventDefault(){prevented=true}});return {promise,prevented}}};
}
test('explicit local blob export streams exact bounded chunks and requests share once',async()=>{
 const h=host();const click=h.click();assert.equal(click.prevented,true);await click.promise;
 const chunks=h.calls.filter(c=>c[0]==='append').map(c=>Buffer.from(c[2],'base64'));
 assert.ok(chunks.every(c=>c.length<=32768));assert.deepEqual(Buffer.concat(chunks),Buffer.alloc(70000,42));
 assert.equal(h.calls.filter(c=>c[0]==='finish').length,1);assert.equal(h.calls.filter(c=>c[0]==='cancel').length,0);
 assert.match(h.notices[0].textContent,/does not confirm it was saved/);
});
test('remote links and foreign blob origins never invoke native save',async()=>{
 const h=host();for(const url of ['https://example.com/private.png','blob:https://example.com/copy']){const c=h.click(url);assert.equal(c.prevented,false);await c.promise;}assert.equal(h.calls.length,0);
});
test('background cancels ownership before the delayed blob can reach native',async()=>{
 const h=host({defer:true});const c=h.click();h.document.hidden=true;h.events.visibilitychange();h.release();await c.promise;
 assert.equal(h.calls.filter(c=>c[0]==='begin'||c[0]==='append'||c[0]==='finish').length,0);
});
test('replayed click cannot start a second export while first is in flight',async()=>{
 const h=host({defer:true});const first=h.click();await h.click().promise;h.release();await first.promise;
 assert.equal(h.calls.filter(c=>c[0]==='begin').length,1);
});
test('invalid export filename leaves no native output',async()=>{
 const h=host();await h.click(undefined,'../private.png').promise;assert.equal(h.calls.length,0);
});
