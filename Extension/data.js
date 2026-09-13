import {wikipediaCharacters} from './wiki-characters.js';
const ENDPOINT = 'https://www.wikidata.org/w/api.php';
export async function api(params) {
  const url = new URL(ENDPOINT);
  url.search = new URLSearchParams({format:'json', origin:'*', ...params});
  let response;
  try { response = await fetch(url, {signal:AbortSignal.timeout(15000), credentials:'omit'}); }
  catch { throw new Error('Could not reach Wikidata. Check your connection and try again.'); }
  if (response.status === 429 || response.status === 503) throw new Error('Wikidata is busy. Please wait a minute before trying again.');
  if (!response.ok) throw new Error(`Wikidata returned an error (${response.status}). Try again later.`);
  const data = await response.json();
  if (data.error) throw new Error('Wikidata could not complete this request. Try again later.');
  return data;
}
export async function searchBooks(title) {
  const data = await api({action:'wbsearchentities', search:title.slice(0,160), language:'en', uselang:'en', type:'item', limit:'8'});
  return data.search || [];
}
export function linkedIds(entity, property) {
  return [...new Set((entity.claims?.[property] || []).filter(c => c.rank !== 'deprecated').map(c => c.mainsnak?.datavalue?.value?.id).filter(id => /^Q\d+$/.test(id)))];
}
export function toEntry(entity, kind) {
  const name = entity.labels?.en?.value;
  if (!name) return null;
  return {id:entity.id, name, kind, aliases:(entity.aliases?.en || []).map(a=>a.value).filter(a=>a.length>=3).slice(0,12), description:entity.descriptions?.en?.value || 'No English description is available.', source:`https://www.wikidata.org/wiki/${entity.id}`, wikipedia:entity.sitelinks?.enwiki?.title ? `https://en.wikipedia.org/wiki/${encodeURIComponent(entity.sitelinks.enwiki.title.replaceAll(' ','_'))}` : null};
}
async function entities(ids, props) {
  const data = await api({action:'wbgetentities', ids:ids.join('|'), props, languages:'en'});
  return data.entities || {};
}
export async function loadBook(id) {
  if (!/^Q\d+$/.test(id)) throw new Error('Invalid book identifier.');
  const {bookCache={}} = await chrome.storage.local.get('bookCache');
  if (bookCache[id]?.version===2 && Date.now()-bookCache[id].time < 7*86400000) return bookCache[id].value;
  const book = (await entities([id], 'labels|claims|sitelinks'))[id];
  if (!book || book.missing !== undefined) throw new Error('This work is no longer available.');
  const characters = linkedIds(book,'P674'), places = linkedIds(book,'P840');
  const allIds = [...new Set([...characters,...places])];
  const selected = allIds.slice(0,1500), entries = [];
  for (let i=0;i<selected.length;i+=50) {
    const batch = await entities(selected.slice(i,i+50),'labels|descriptions|aliases|sitelinks');
    for (const entity of Object.values(batch)) {
      const entry = toEntry(entity, characters.includes(entity.id) ? 'character' : 'place');
      if (entry) entries.push(entry);
    }
  }
  let coverageWarning=null;
  try{
    const extra=await wikipediaCharacters(book.sitelinks?.enwiki?.title,id);
    const existing=new Set(entries.flatMap(entry=>[entry.name,...entry.aliases]).map(name=>name.toLowerCase()));
    for(const entry of extra){if(!existing.has(entry.name.toLowerCase())){entries.push(entry);existing.add(entry.name.toLowerCase());}}
  }catch{coverageWarning='Wikipedia character sections could not be checked; showing available Wikidata names.';}
  const value = {id, title:book.labels?.en?.value || id, entries, coverageWarning, truncated:allIds.length>1500};
  // Re-read after network calls to preserve other popup writes where possible.
  const latest = (await chrome.storage.local.get('bookCache')).bookCache || {};
  latest[id] = {time:coverageWarning?Date.now()-7*86400000+5*60000:Date.now(),version:2, value};
  const bounded = Object.fromEntries(Object.entries(latest).sort((a,b)=>b[1].time-a[1].time).slice(0,20));
  await chrome.storage.local.set({bookCache:bounded});
  return value;
}
