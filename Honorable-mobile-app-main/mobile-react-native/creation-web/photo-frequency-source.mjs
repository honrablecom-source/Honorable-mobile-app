import{replaceablePhoto}from'./photo-replace.mjs';
/** Read generated layer groups without moving/replacing/ungrouping their members. */
export function frequencySourcePhoto(elements,id){
 if(!Array.isArray(elements))throw Error('The frequency source objects are unavailable.');
 const photo=elements.find(e=>e?.id===id&&!e.isDeleted);
 if(!photo?.groupIds?.length)return replaceablePhoto(elements,id);
 const groups=photo.groupIds,recipe=photo.customData?.nobleFrequency;
 if(!Array.isArray(groups)||!groups.every(g=>typeof g==='string'&&g)||!['base','detail'].includes(recipe?.role)||recipe?.convention!=='noble-frequency-v1')throw Error('Ungroup ordinary photos before using them as frequency sources.');
 const members=elements.filter(e=>e&&!e.isDeleted&&Array.isArray(e.groupIds)&&e.groupIds.some(g=>groups.includes(g)));
 if(members.some(e=>e.locked||e.opacity===0||e.customData?.nobleHidden||e.frameId||e.containerId||e.boundElements?.length))throw Error('Show, unlock and detach the complete frequency group before reading its layers.');
 // Reuse all existing scene/photo checks on a private eligibility view only.
 replaceablePhoto(elements.map(e=>e===photo?{...e,groupIds:[]}:e),id);
 return photo;
}
