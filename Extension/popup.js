import {searchBooks,loadBook} from './data.js';
const $ = id=>document.getElementById(id);
let tabId, selected, busy=false;
function status(text,error=false){$('status').textContent=text;$('status').classList.toggle('error',error);}
async function task(work){if(busy)return;busy=true;document.querySelectorAll('button').forEach(b=>b.disabled=true);try{await work();}catch(e){status(e.message,true);}finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);}}
async function page(message){if(!tabId)throw new Error('Open a regular webpage first.');try{await chrome.scripting.executeScript({target:{tabId},files:['content.js']});return await chrome.tabs.sendMessage(tabId,message);}catch{throw new Error('This page cannot be accessed. Open a regular reading webpage. For local files, enable “Allow access to file URLs” in extension settings.');}}
$('search').addEventListener('submit',e=>{e.preventDefault();task(async()=>{
  const title=$('title').value.trim();if(!title)return;
  selected=null;$('index').hidden=true;$('results').replaceChildren();status('Searching Wikidata…');
  const results=await searchBooks(title);
  status(results.length?'Choose the novel, rather than a film or adaptation.':'No matches. Try the original title or a shorter title.');
  for(const result of results){const b=document.createElement('button');b.className='result';b.textContent=result.label;const d=document.createElement('small');d.textContent=result.description||result.id;b.append(d);b.addEventListener('click',()=>task(async()=>{
    status('Loading characters and settings…');selected=await loadBook(result.id);$('book').textContent=selected.title;$('count').textContent=`${selected.entries.length} indexed names`;$('entries').replaceChildren();
    for(const entry of selected.entries){const row=document.createElement('div');row.className='entry';row.textContent=entry.name;const kind=document.createElement('small');kind.textContent=entry.kind;row.append(kind);$('entries').append(row);}
    $('coverage').textContent=selected.entries.length?'Only names linked to this work on Wikidata are included. Descriptions may contain spoilers.':'No linked characters or settings were found. Try another work or edition. This does not mean the novel has no characters.';
    $('index').hidden=false;status(selected.truncated?'Showing the first 300 linked items.':'Index loaded. Highlight the page when ready.');
  }));$('results').append(b);}
});});
$('highlight').addEventListener('click',()=>task(async()=>{if(!selected?.entries.length){status('Choose a work with indexed names first.');return;}const result=await page({type:'nc-highlight',entries:selected.entries});status(`${result.count} names highlighted. Click a name in the page for details.${result.capped?' Highlight limit reached.':''}`);}));
$('remove').addEventListener('click',()=>task(async()=>{await page({type:'nc-remove'});status('Highlights removed.');}));
$('clear').addEventListener('click',()=>task(async()=>{await chrome.storage.local.remove(['bookCache','detailCache']);status('Cached indexes cleared.');}));
try{const [tab]=await chrome.tabs.query({active:true,currentWindow:true});tabId=tab?.id;const result=await page({type:'nc-title'});$('title').value=result.title;status('Ready. Confirm the title to search.');}catch(e){status(e.message,true);}
