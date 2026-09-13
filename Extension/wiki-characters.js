export async function wiki(params){
 const url=new URL('https://en.wikipedia.org/w/api.php');url.search=new URLSearchParams({action:'parse',format:'json',origin:'*',...params});
 const response=await fetch(url,{credentials:'omit',signal:AbortSignal.timeout(15000)});
 if(!response.ok)throw Error('Wikipedia character lookup unavailable.');
 const data=await response.json();if(data.error)throw Error('Wikipedia character section unavailable.');return data;
}
export function plain(text){return text.replace(/<ref\b[^>]*>[\s\S]*?<\/ref>|<ref\b[^>]*\/>/gi,'').replace(/\{\{[^{}]*\}\}/g,'').replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g,'$2').replace(/\[\[([^\]]+)\]\]/g,'$1').replace(/'{2,5}/g,'').replace(/<[^>]*>/g,'').replace(/&nbsp;/g,' ').trim();}
export function parseCharacterSection(wikitext,bookId,pageTitle){
 const entries=[];
 for(const line of wikitext.split('\n')){
  // Only explicit list/definition entries with a bold name or leading article link.
  const match=line.match(/^[*;#:]\s*(?:'''([^']{2,120})'''|\[\[([^\]]{2,150})\]\])\s*[:–—,-]?\s*(.*)$/);
  if(!match)continue;
  const name=plain(match[1]||`[[${match[2]}]]`).replace(/\s*\([^)]*\)\s*$/,'').trim();
  const description=plain(match[3]).slice(0,1800);
  if(name.length<3||!description||/^(main|minor|other|supporting) characters?$/i.test(name))continue;
  const source=`https://en.wikipedia.org/wiki/${encodeURIComponent(pageTitle.replaceAll(' ','_'))}#Characters`;
  entries.push({id:`wiki:${bookId}:${name.toLowerCase()}`,name,kind:'character',aliases:[],description:description.slice(0,220),source:null,wikipedia:source,localDetails:{biography:description,wikipedia:source,facts:[{label:'Source',value:'Wikipedia character-list entry'}]}});
 }
 return entries.filter((entry,index)=>entries.findIndex(other=>other.name.toLowerCase()===entry.name.toLowerCase())===index);
}
export async function wikipediaCharacters(pageTitle,bookId){
 if(!pageTitle)return [];
 const data=await wiki({page:pageTitle,prop:'sections|links'});
 const sections=(data.parse?.sections||[]).filter(section=>/^(?:main |major |minor |principal |supporting |other )?characters?(?: and .*)?$/i.test(plain(section.line)));
 // Fetch parent sections only: their subsections are already included.
 const parents=sections.filter(section=>!sections.some(other=>other!==section&&String(section.number).startsWith(other.number+'.'))).slice(0,6);
 const entries=[];
 for(const section of parents){const parsed=await wiki({page:pageTitle,prop:'wikitext',section:section.index});entries.push(...parseCharacterSection(parsed.parse?.wikitext?.['*']||'',bookId,pageTitle));}
 const normalize=value=>value.toLowerCase().replace(/\s*\(novel\)\s*$/,'').replace(/[^a-z0-9]+/g,' ').trim();
 const expected=normalize(`List of ${pageTitle} characters`);
 const lists=(data.parse?.links||[]).filter(link=>link.ns===0&&normalize(link['*'])===expected).slice(0,2);
 for(const list of lists){const parsed=await wiki({page:list['*'],prop:'wikitext'});entries.push(...parseCharacterSection((parsed.parse?.wikitext?.['*']||'').slice(0,500000),bookId,list['*']));}
 return entries.filter((entry,index)=>entries.findIndex(other=>other.name.toLowerCase()===entry.name.toLowerCase())===index);
}
