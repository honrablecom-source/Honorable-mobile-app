import {api, linkedIds} from './data.js';
const pending = new Map();
export async function loadDetails(id) {
  if (!/^Q\d+$/.test(id)) throw new Error('Invalid character identifier.');
  if (pending.has(id)) return pending.get(id);
  const job = fetchDetails(id).finally(()=>pending.delete(id));pending.set(id,job);return job;
}
async function fetchDetails(id) {
  const {detailCache={}}=await chrome.storage.local.get('detailCache');
  if(detailCache[id] && Date.now()-detailCache[id].time<7*86400000)return detailCache[id].value;
  const data=await api({action:'wbgetentities',ids:id,props:'claims|sitelinks',languages:'en'});
  const entity=data.entities?.[id];if(!entity)throw new Error('No character details found.');
  const facts=[];
  const properties=[['P4584','First appearance (work)'],['P21','Gender'],['P106','Occupation'],['P1080','Fictional universe'],['P22','Father'],['P25','Mother'],['P26','Spouse'],['P3373','Sibling']];
  const ids=[...new Set(properties.flatMap(([p])=>linkedIds(entity,p).slice(0,5)))];
  let labels={};if(ids.length)labels=(await api({action:'wbgetentities',ids:ids.join('|'),props:'labels',languages:'en'})).entities||{};
  for(const [property,label] of properties){const names=linkedIds(entity,property).slice(0,5).map(id=>labels[id]?.labels?.en?.value).filter(Boolean);if(names.length)facts.push({label,value:names.join(', ')});}
  const title=entity.sitelinks?.enwiki?.title;
  const result={facts,biography:'',image:null,wikipedia:null,chapter:null};
  if(title){
    const url=new URL('https://en.wikipedia.org/w/api.php');url.search=new URLSearchParams({action:'query',format:'json',origin:'*',prop:'extracts|pageimages',titles:title,redirects:'1',explaintext:'1',exchars:'12000',piprop:'thumbnail|name',pithumbsize:'600'});
    try{const response=await fetch(url,{credentials:'omit',signal:AbortSignal.timeout(15000)});if(!response.ok)throw Error();const json=await response.json();if(json.error)throw Error();const page=Object.values(json.query?.pages||{})[0];
      result.biography=page?.extract||'';result.wikipedia=`https://en.wikipedia.org/wiki/${encodeURIComponent((page?.title||title).replaceAll(' ','_'))}`;
      if(page?.thumbnail?.source?.startsWith('https://upload.wikimedia.org/'))result.image={url:page.thumbnail.source,source:`https://en.wikipedia.org/wiki/File:${encodeURIComponent(page.pageimage||'')}`};
    }catch{result.warning='Wikipedia details could not be loaded. Close and reopen the sidebar to try again.';}
  }
  if(!result.warning){const latest=(await chrome.storage.local.get('detailCache')).detailCache||{};latest[id]={time:Date.now(),value:result};await chrome.storage.local.set({detailCache:Object.fromEntries(Object.entries(latest).sort((a,b)=>b[1].time-a[1].time).slice(0,40))});}
  return result;
}
