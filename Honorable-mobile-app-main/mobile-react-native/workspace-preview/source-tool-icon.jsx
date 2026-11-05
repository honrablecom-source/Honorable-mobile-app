import React from 'react';
// Geometry mirrors the relevant EditorChrome.ToolIcon branches; native raster proof remains separate.
export function SourceToolIcon({label}){
 const key=label.toLowerCase();let content;
 if(key.includes('undo')||key.includes('redo'))content=<g transform={key.includes('redo')?'translate(24 0) scale(-1 1)':undefined}><path d="M6.75 16.5A7.5 7 0 1 0 16.5 6.75M4 5V11H10"/></g>;
 else if(key.includes('previous')||key.includes('next'))content=<path d={key.includes('previous')?'M17 5L7 12L17 19':'M7 5L17 12L7 19'}/>;
 else if(key.includes('pause'))content=<path d="M9 5V19M15 5V19"/>;
 else if(key.includes('play')||key.includes('preview'))content=<path d="M8 4L20 12L8 20Z"/>;
 else if(key.includes('split'))content=<><circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M8 8L20 20M8 16L20 4"/></>;
 else if(['crop','aspect','format','transform','mirror'].some(word=>key.includes(word)))content=<path d="M7 3V17M3 7H17M7 17H21M17 7V21"/>;
 else if(key.includes('text'))content=<path d="M5 5H19M12 5V20M8 20H16"/>;
 else if(key.includes('ai')||key.includes('person'))content=<path d="M12 3L14.5 9.5L21 12L14.5 14.5L12 21L9.5 14.5L3 12L9.5 9.5Z"/>;
 else if(key.includes('light')||key.includes('exposure')||key.includes('brightness'))content=<><circle cx="12" cy="12" r="4"/>{Array.from({length:8},(_,index)=>{const a=index*Math.PI/4;return <line key={index} x1={12+7*Math.cos(a)} y1={12+7*Math.sin(a)} x2={12+9*Math.cos(a)} y2={12+9*Math.sin(a)}/>;})}</>;
 else if(key.includes('curve'))content=<path d="M4 4V20H20M5 18C16 18 8 6 20 4"/>;
 else content=<><path d="M5 7H19M5 17H19"/><circle cx="9" cy="7" r="2"/><circle cx="15" cy="17" r="2"/></>;
 return <svg viewBox="0 0 24 24" fill="none" stroke="#000" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{content}</svg>;
}
