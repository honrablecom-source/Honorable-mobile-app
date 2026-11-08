import React from 'react';
const groups=[
 {title:'Source and framing',actions:[['replace','Replace photo','Replace photo'],['framing','Suggest crop','Suggest crop for photo'],['rectify','Straighten photo','Straighten photo']]},
 {title:'Cut out and trace',actions:[['outline','Outline cutout','Outline cutout from photo'],['wand','Remove connected color','Remove connected color from photo'],['trace','Trace connected outline','Trace connected outline from photo']]},
 {title:'Create from this photo',actions:[['surface','Surface mockup','Surface mockup on photo'],['mosaic','Photo mosaic','Make mosaic from photo']]}
];
/** Native disclosure keeps keyboard/assistive-technology behavior and photo actions in one row. */
export function PhotoObjectTools({number,disabled,onAction}){
 return <details className="photo-object-tools" data-photo-number={number}>
  <summary aria-label={`Photo tools for photo ${number}`}>Photo tools</summary>
  <div className="photo-tool-groups">
   {disabled&&<p>Show and unlock this photo to use its editing tools.</p>}
   {groups.map(group=><section className="photo-tool-group" key={group.title} aria-label={`${group.title} for photo ${number}`}><h4>{group.title}</h4><div className="tool-grid">{group.actions.map(([action,label,aria])=><button key={action} disabled={disabled} aria-label={`${aria} ${number}`} onClick={()=>onAction(action)}>{label}</button>)}</div></section>)}
  </div>
 </details>;
}
