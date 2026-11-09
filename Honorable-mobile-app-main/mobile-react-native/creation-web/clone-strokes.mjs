import {simplify} from 'points-on-curve';
import {cloneRepairPlan,CLONE_LIMITS} from './photo-clone-repair.mjs';
/** Admit a complete pointer stroke; never silently truncate it to satisfy processing limits. */
export function appendCloneStroke(width,height,existing,settings,raw){
 if(!Array.isArray(existing)||existing.length>=CLONE_LIMITS.strokes)throw Error('Undo a stroke before adding another. Up to 16 repair strokes fit.');
 if(!Array.isArray(raw)||!raw.length||raw.length>1024)throw Error('This stroke is too long. Split it into shorter strokes; nothing was added.');
 cloneRepairPlan(width,height,[{...settings,points:[raw[0]]}]);
 const points=raw.map(p=>{if(!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)||p[0]<0||p[1]<0||p[0]>width-1||p[1]>height-1)throw Error('Keep the whole repair stroke inside the photo. Nothing was added.');return[p[0],p[1]];});
 const reduced=points.length>2?simplify(points,.25):points;
 if(reduced.length>CLONE_LIMITS.pointsPerStroke)throw Error('This stroke is too detailed. Split it into shorter strokes; nothing was added.');
 return cloneRepairPlan(width,height,[...existing,{...settings,points:reduced}]).strokes;
}
