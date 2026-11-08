// Detached XML/geometry integration only. Actual editor, native picker and device proof are separate.
const {chromium}=require('../../../ui-previews/node_modules/playwright');
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),output=process.env.NOBLE_SVG_PARSER_OUTPUT||path.resolve(root,'../../../.context/svg-import/parser-browser');
(async()=>{
 await fs.mkdir(output,{recursive:true});const bundle='/tmp/noble-svg-import-parser.js';
 const {build}=require(path.join(root,'node_modules/esbuild'));await build({stdin:{contents:"import {readVectorSvg} from './svg-import-source.mjs';import {vectorArtwork} from './svg-import-geometry.mjs';globalThis.svgImportTest={readVectorSvg,vectorArtwork};",resolveDir:root,sourcefile:'svg-import-parser-fixture.mjs'},bundle:true,target:'chrome90',format:'iife',outfile:bundle,logLevel:'silent'});
 const server=http.createServer(async(req,res)=>{if(req.url==='/'){res.writeHead(200,{'Content-Type':'text/html','Content-Security-Policy':"default-src 'self';script-src 'self';connect-src 'self';img-src 'self' data: blob:;object-src 'none';base-uri 'none'"}).end('<!doctype html><title>SVG reader test</title><script src="/fixture.js"></script>');}else if(req.url==='/fixture.js'){res.writeHead(200,{'Content-Type':'text/javascript'}).end(await fs.readFile(bundle));}else res.writeHead(404).end();});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;let browser;
 try{
  browser=await chromium.launch({args:['--no-sandbox']});const page=await browser.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',route=>{if(!route.request().url().startsWith(origin)){external.push(route.request().url());return route.abort();}return route.continue();});await page.goto(origin);await page.waitForFunction(()=>window.svgImportTest);
  const result=await page.evaluate(()=>{
   const {readVectorSvg,vectorArtwork}=window.svgImportTest,svg=body=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">${body}</svg>`;
   const check=(value,message)=>{if(!value)throw Error(message);};
   const raw=svg('<title>PRIVATE TITLE</title><metadata><private xmlns="urn:private">PRIVATE METADATA</private></metadata><g fill="red" transform="translate(10 5)"><rect id="PRIVATE ID" x="0" y="0" width="40" height="30"/><circle cx="60" cy="60" r="10" fill="blue"/></g><rect width="20" height="20" display="none" fill="lime"/>');
   const clean=readVectorSvg(raw),encoded=JSON.stringify(clean);check(clean.items.length===2&&clean.omittedHidden===1,'Visible shape count');check(!encoded.includes('PRIVATE')&&!/"(?:metadata|title|id)"\s*:/.test(encoded),'Metadata leaked into geometry');check(clean.items[0].fill==='#ff0000ff'&&clean.items[1].fill==='#0000ffff','Named paint normalization');const output=vectorArtwork(clean);check(output.plan.length===2,'Primitive conversion');
   const primitives=readVectorSvg(svg('<rect x="5" y="5" width="20" height="20" rx="4"/><ellipse cx="50" cy="20" rx="10" ry="5"/><polygon points="10,50 20,70 5,70"/><polyline points="40,50 50,70 60,50" fill="none" stroke="black"/><line x1="70" y1="20" x2="80" y2="30" stroke="black"/><path d="M70 70Q80 90 90 70Z"/>'));check(primitives.items.length===6,'Primitives missing');check(vectorArtwork(primitives).hasCurves,'Curves not disclosed');
   const inherited=readVectorSvg(svg('<g style="color:blue;fill:currentcolor;fill-opacity:50%"><g visibility="hidden"><rect width="10" height="10"/><rect width="20" height="20" visibility="visible"/></g></g>'));check(inherited.items.length===1&&inherited.omittedHidden===1,'Visibility override');check(inherited.items[0].fill==='#0000ff80','Inherited currentColor/alpha');
   const paints=readVectorSvg(svg('<rect width="10" height="10" fill="rgba(128,64,32,0.5)"/>'));check(paints.items[0].fill==='#80402080','RGBA exact color');
   const single=readVectorSvg(svg('<g opacity=".5"><path d="M0 0H80V80H0Z"/></g>'));check(single.items[0].opacity===50,'Single group opacity');
   const rejected=[];
   const bad=[
    '<!DOCTYPE svg [<!ENTITY x "resource">]>'+svg('<path d="M0 0H1V1Z"/>'),
    '<?xml-stylesheet href="https://invalid.test/style"?>'+svg(''),
    svg('<script>fetch("https://invalid.test/script")</script>'),svg('<image href="https://invalid.test/image"/>'),svg('<use href="#item"/>'),svg('<foreignObject><div xmlns="http://www.w3.org/1999/xhtml">x</div></foreignObject>'),svg('<rect width="10" height="10" onclick="alert(1)"/>'),svg('<rect width="10" height="10" fill="url(https://invalid.test/paint)"/>'),svg('<style>@import "https://invalid.test/style";</style>'),svg('<text x="10" y="10">Unsupported text</text>'),svg('<g opacity=".5"><rect width="10" height="10"/><rect x="20" width="10" height="10"/></g>'),svg('<rect width="10" height="10" style="filter:blur(1px)"/>'),svg('<rect width="10" height="10" fill-opacity="1px"/>'),svg('<rect width="10" height="10" clip-path="none"/>'),svg('<g>'.repeat(14)+'<rect width="10" height="10"/>'+'</g>'.repeat(14)),svg('<rect width="1" height="1"/>'.repeat(129)),svg('<g/>'.repeat(513)),svg('<path>'),'<svg viewBox="0 0 10 10"/>',svg('<rect width="10" height="10" fill="notacolor"/>')
   ];
   for(const raw of bad){let error;try{readVectorSvg(raw);}catch(e){error=e.message;}check(error,'Unsafe or unsupported SVG accepted');rejected.push(error);}
   check(!document.querySelector('svg,script:not([src]),image,iframe'),'Untrusted DOM inserted');
   return {visibleShapes:clean.items.length,omittedHidden:clean.omittedHidden,metadataStripped:true,primitiveCount:primitives.items.length,currentColorAndAlpha:true,rgbaExact:true,groupOpacity:true,rejected:rejected.length,reasons:rejected,scope:'Actual Chromium detached XML reader and geometry integration; no actual editor/worker/native file picker/device proof.'};
  });
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);await fs.writeFile(path.join(output,'results.json'),JSON.stringify({...result,errors,external},null,2));console.log('PASS Chromium SVG XML/paint/transform/visibility/primitive/metadata integration and20 unsafe/unsupported input cases;zero external/page errors. Editor/native integration remains pending.');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
