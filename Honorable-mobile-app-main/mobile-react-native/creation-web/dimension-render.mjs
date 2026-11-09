import {convertToExcalidrawElements,newElementWith,exportToBlob} from '@excalidraw/excalidraw';
import {dimensionLabelPatches} from './dimension-callouts.mjs';
import {filesForElements} from './image-assets.mjs';
import {validateDrawingElements} from './scene.mjs';
export function prepareDimensionElements(result){
 const created=convertToExcalidrawElements(result.plan);
 if(created.length!==8)throw Error('The complete dimension group could not be prepared.');
 const labelled=created.map((element,i)=>result.plan[i].customData?{...element,customData:{...result.plan[i].customData}}:element);
 const patches=new Map(dimensionLabelPatches(labelled,result).map(patch=>[patch.id,patch]));
 const positioned=labelled.map(element=>patches.has(element.id)?newElementWith(element,patches.get(element.id),true):element);
 validateDrawingElements(positioned);return positioned;
}
/** Render the actual captured artwork and the same measured elements later inserted. */
export async function renderDimensionPreview(source,result,requireCurrent){
 requireCurrent();const elements=prepareDimensionElements(result),files=filesForElements(source.elements,source.files);
 const blob=await exportToBlob({elements:[...source.elements,...elements],files,appState:{exportBackground:true,viewBackgroundColor:'#ffffff',exportWithDarkMode:false,exportEmbedScene:false},mimeType:'image/png',maxWidthOrHeight:1024});
 requireCurrent();if(!blob||blob.type!=='image/png'||!blob.size)throw Error('The local dimension preview could not be rendered.');
 return{elements,blob,result,source};
}
