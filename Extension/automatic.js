import {api,searchBooks,loadBook} from './data.js';
const normalize=s=>s.toLowerCase().replace(/\s*\((?:novel|book|novella)\)\s*$/i,'').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
export function chooseWork(results,title){
 const exact=results.filter(item=>[item.label,...(item.aliases||[]),item.match?.text].filter(Boolean).some(label=>normalize(label)===normalize(title))&&/\b(novels?|novella|books?|literary|webnovel|light novel)\b/i.test(item.description||'')&&!/\b(film|movie|adaptation|television)\b/i.test(item.description||''));
 const unique=[...new Map(exact.map(item=>[item.id,item])).values()];
 return unique.length===1?unique[0]:null;
}
export async function wikipediaWork(title){
 const url=new URL('https://en.wikipedia.org/w/api.php');
 url.search=new URLSearchParams({action:'query',format:'json',origin:'*',titles:`${title}|${title} (novel)`,redirects:'1',prop:'pageprops',ppprop:'wikibase_item|disambiguation'});
 let response;try{response=await fetch(url,{credentials:'omit',signal:AbortSignal.timeout(15000)});}catch{throw Error('Wikipedia is temporarily unavailable.');}
 if(!response.ok)throw Error('Wikipedia is busy. Please try again later.');
 const data=await response.json();if(data.error)throw Error('Wikipedia lookup failed.');
 const ids=[...new Set(Object.values(data.query?.pages||{}).filter(page=>page.pageprops?.disambiguation===undefined).map(page=>page.pageprops?.wikibase_item).filter(id=>/^Q\d+$/.test(id)))];
 if(!ids.length)return null;
 const entities=(await api({action:'wbgetentities',ids:ids.join('|'),props:'labels|descriptions|aliases',languages:'en'})).entities||{};
 return chooseWork(Object.values(entities).map(item=>({id:item.id,label:item.labels?.en?.value||'',description:item.descriptions?.en?.value||'',aliases:(item.aliases?.en||[]).map(a=>a.value)})),title);
}
const pending=new Map();
export async function automaticIndex(input){
 const candidates=[...new Set((Array.isArray(input)?input:[input]).filter(title=>typeof title==='string'&&title.length>=3&&title.length<=160).map(title=>title.trim()))].slice(0,3);
 for(const title of candidates){const book=await resolveTitle(title);if(book)return book;}
 return null;
}
async function resolveTitle(title){
 const key=normalize(title);
 if(pending.has(key))return pending.get(key);
 const job=(async()=>{
  const {autoCache={}}=await chrome.storage.local.get('autoCache');
  let match=autoCache[key];
  if(!match||match.version!==2||Date.now()-match.time>86400000){
   const results=await searchBooks(title);
   let work=chooseWork(results,title);
   // Wikipedia redirects and article disambiguation can resolve alternate titles.
   if(!work)work=await wikipediaWork(title);
   match={id:work?.id||null,time:Date.now(),version:2};
   const latest=(await chrome.storage.local.get('autoCache')).autoCache||{};latest[key]=match;
   await chrome.storage.local.set({autoCache:Object.fromEntries(Object.entries(latest).sort((a,b)=>b[1].time-a[1].time).slice(0,40))});
  }
  if(!match.id)return null;
  const book=await loadBook(match.id);return {...book,entries:book.entries.map(entry=>({...entry,bookId:book.id}))};
 })().finally(()=>pending.delete(key));pending.set(key,job);return job;
}
