import { SVG_LIMITS } from './svg-import-limits.mjs';
const NS = 'http://www.w3.org/2000/svg';
const numberPattern = /[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g;
const finite = value => Number.isFinite(value) && Math.abs(value) <= 1e6;
const identity = [1, 0, 0, 1, 0, 0];
export function svgNumbers(value, count) {
  if (typeof value !== 'string' || value.length > 16384) throw Error('The SVG contains an invalid number list.');
  const values = []; let end = 0;
  for (const match of value.matchAll(numberPattern)) {
    if (!/^[\s,]*$/.test(value.slice(end, match.index))) throw Error('The SVG contains invalid numeric syntax.');
    values.push(Number(match[0])); end = match.index + match[0].length;
    if (values.length > 2048) throw Error('The SVG has too many coordinates.');
  }
  if (!/^[\s,]*$/.test(value.slice(end)) || values.some(value => !finite(value)) || (count !== undefined && values.length !== count)) throw Error('The SVG contains incomplete or oversized numbers.');
  return values;
}
export function multiplySvgMatrices(left, right) {
  const [a, b, c, d, e, f] = left, [A, B, C, D, E, F] = right;
  const result = [a*A+c*B, b*A+d*B, a*C+c*D, b*C+d*D, a*E+c*F+e, b*E+d*F+f];
  if (!result.every(finite)) throw Error('The SVG transform is too large.');
  return result;
}
export function svgTransform(value = '') {
  if (typeof value !== 'string' || value.length > 2048) throw Error('The SVG transform is too long.');
  let result = [...identity], end = 0, count = 0;
  for (const match of value.matchAll(/([a-zA-Z]+)\s*\(([^()]*)\)/g)) {
    if (!/^[\s,]*$/.test(value.slice(end, match.index)) || ++count > 24) throw Error('The SVG transform is unsupported.');
    end = match.index + match[0].length;
    const p = svgNumbers(match[2]), radians = p[0] * Math.PI / 180; let next;
    switch (match[1]) {
      case 'matrix': if (p.length === 6) next = p; break;
      case 'translate': if (p.length === 1 || p.length === 2) next = [1,0,0,1,p[0],p[1] ?? 0]; break;
      case 'scale': if (p.length === 1 || p.length === 2) next = [p[0],0,0,p[1] ?? p[0],0,0]; break;
      case 'rotate':
        if (p.length === 1 || p.length === 3) {
          next = [Math.cos(radians),Math.sin(radians),-Math.sin(radians),Math.cos(radians),0,0];
          if (p.length === 3) next = multiplySvgMatrices(multiplySvgMatrices([1,0,0,1,p[1],p[2]],next),[1,0,0,1,-p[1],-p[2]]);
        }
        break;
      case 'skewX': if (p.length === 1) next = [1,0,Math.tan(radians),1,0,0]; break;
      case 'skewY': if (p.length === 1) next = [1,Math.tan(radians),0,1,0,0]; break;
    }
    if (!next?.every(finite)) throw Error('Use a supported finite SVG transform.');
    result = multiplySvgMatrices(result,next);
  }
  if (!/^[\s,]*$/.test(value.slice(end))) throw Error('The SVG transform could not be read.');
  return result;
}
function scalar(value, fallback, percentage = false) {
  if (value == null || value === '') return fallback;
  const text = String(value).trim(), percent = percentage && text.endsWith('%');
  const parsed = svgNumbers(percent ? text.slice(0,-1) : percentage ? text : text.replace(/px$/,''),1)[0];
  return percent ? parsed / 100 : parsed;
}
export function svgViewport(viewBox, width, height, aspect = 'xMidYMid meet') {
  const box = viewBox == null ? null : svgNumbers(viewBox,4);
  if (box && (box[2] <= 0 || box[3] <= 0)) throw Error('Use a positive SVG viewBox.');
  let w = scalar(width,null), h = scalar(height,null);
  if (w == null) w = h != null && box ? h*box[2]/box[3] : box?.[2];
  if (h == null) h = w != null && box ? w*box[3]/box[2] : box?.[3];
  if (![w,h].every(value=>finite(value)&&value>0)) throw Error('Give the SVG a viewBox or positive pixel dimensions.');
  const [x,y,bw,bh] = box || [0,0,w,h], size = 512/Math.max(w,h), viewport = {width:w*size,height:h*size};
  if (Math.min(viewport.width,viewport.height)<1) throw Error('Use an SVG canvas with a less extreme aspect ratio.');
  const parts=aspect.trim().split(/\s+/), align=parts[0], fit=parts[1]||'meet';
  if (align==='none'&&parts.length===1) return {...viewport,matrix:[w/bw*size,0,0,h/bh*size,-x*w/bw*size,-y*h/bh*size]};
  if (!/^x(?:Min|Mid|Max)Y(?:Min|Mid|Max)$/.test(align)||!['meet','slice'].includes(fit)||parts.length>2) throw Error('The SVG aspect-ratio setting is unsupported.');
  const scale=(fit==='slice'?Math.max(w/bw,h/bh):Math.min(w/bw,h/bh)), factor={Min:0,Mid:.5,Max:1};
  const dx=(w-bw*scale)*factor[align.slice(1,4)],dy=(h-bh*scale)*factor[align.slice(5,8)];
  return {...viewport,matrix:[scale*size,0,0,scale*size,(dx-x*scale)*size,(dy-y*scale)*size]};
}
export function resolveBrowserSvgPaint(value) {
  const context=globalThis.document?.createElement('canvas').getContext('2d');
  if(!context) throw Error('Color reading is unavailable in this workspace.');
  context.fillStyle='#010203';context.fillStyle=value;const first=context.fillStyle;
  context.fillStyle='#040506';context.fillStyle=value;
  if(context.fillStyle!==first) throw Error('The SVG color is unsupported.');
  if(/^#[\da-f]{6}$/i.test(first)) return first.toLowerCase();
  const match=/^rgba?\(([^)]+)\)$/.exec(first),parts=match?.[1].split(',').map(Number);
  if(!parts||![3,4].includes(parts.length)||parts.some(n=>!Number.isFinite(n))) throw Error('Use a solid RGB SVG color.');
  return '#'+parts.slice(0,3).map(n=>Math.round(n).toString(16).padStart(2,'0')).join('')+(parts.length===4?Math.round(parts[3]*255).toString(16).padStart(2,'0'):'');
}
export function svgPaint(value, resolve) {
  if (typeof value !== 'string') throw Error('The SVG paint is invalid.');
  const text=value.trim().toLowerCase();
  if (text==='none'||text==='transparent') return 'transparent';
  if (['currentcolor','inherit','initial','unset','revert','revert-layer'].includes(text)) throw Error('Resolve inherited SVG colors before importing.');
  if (/^#[\da-f]{3,4}$/.test(text)) return '#'+[...text.slice(1)].map(c=>c+c).join('');
  if (/^#(?:[\da-f]{6}|[\da-f]{8})$/.test(text)) return text;
  if (!/^(?:[a-z]+|rgba?\([\d\s.,%+-]+\))$/.test(text)||typeof resolve!=='function') throw Error('Use solid SVG colors; gradients and external paints are unsupported.');
  const color=resolve(text);
  if (typeof color!=='string'||!/^#[\da-f]{6}(?:[\da-f]{2})?$/.test(color)) throw Error('An SVG color could not be resolved.');
  return color;
}
const alphaPaint=(color,opacity)=>{
  if (color==='transparent') return color;
  const alpha=(color.length===9?parseInt(color.slice(7),16):255)*opacity;
  return color.slice(0,7)+Math.round(alpha).toString(16).padStart(2,'0');
};
const paints=['fill','stroke','stroke-width','fill-opacity','stroke-opacity','opacity','fill-rule','display','visibility','color','stroke-linecap','stroke-linejoin','stroke-miterlimit'];
const common=new Set([...paints,'transform','style','id','class','role']);
const geometry={path:['d'],rect:['x','y','width','height','rx','ry'],circle:['cx','cy','r'],ellipse:['cx','cy','rx','ry'],line:['x1','y1','x2','y2'],polygon:['points'],polyline:['points']};
function properties(element) {
  const values={};
  for (const name of paints) if(element.hasAttribute(name)) values[name]=element.getAttribute(name);
  if (element.hasAttribute('style')) for (const declaration of element.getAttribute('style').split(';')) {
    if (!declaration.trim()) continue;
    const index=declaration.indexOf(':'),key=declaration.slice(0,index).trim(),value=declaration.slice(index+1).trim();
    if(index<1||!paints.includes(key)||!value||/[!{}]/.test(value)) throw Error('Expand SVG styles, filters and dashed outlines before importing.');
    values[key]=value;
  }
  return values;
}
function primitive(element) {
  const name=element.localName,n=(key,fallback=0)=>scalar(element.getAttribute(key),fallback);
  if(name==='path') return element.getAttribute('d')?.trim()||null;
  if(name==='polygon'||name==='polyline') {
    const p=svgNumbers(element.getAttribute('points')||'');
    if(p.length<(name==='polygon'?6:4)||p.length%2) throw Error('The SVG polygon has incomplete points.');
    return 'M'+p.slice(0,2).join(' ')+' '+Array.from({length:p.length/2-1},(_,i)=>'L'+p.slice(i*2+2,i*2+4).join(' ')).join(' ')+(name==='polygon'?'Z':'');
  }
  if(name==='line') return `M${n('x1')} ${n('y1')}L${n('x2')} ${n('y2')}`;
  if(name==='rect') {
    const x=n('x'),y=n('y'),w=n('width'),h=n('height');
    if(w<0||h<0) throw Error('SVG rectangle dimensions must be positive.');
    if(!w||!h) return null;
    let rx=n('rx',null),ry=n('ry',null);rx??=ry??0;ry??=rx;
    if(rx<0||ry<0) throw Error('SVG corner radii must be positive.');rx=Math.min(rx,w/2);ry=Math.min(ry,h/2);
    if(!rx||!ry) return `M${x} ${y}H${x+w}V${y+h}H${x}Z`;
    return `M${x+rx} ${y}H${x+w-rx}A${rx} ${ry} 0 0 1 ${x+w} ${y+ry}V${y+h-ry}A${rx} ${ry} 0 0 1 ${x+w-rx} ${y+h}H${x+rx}A${rx} ${ry} 0 0 1 ${x} ${y+h-ry}V${y+ry}A${rx} ${ry} 0 0 1 ${x+rx} ${y}Z`;
  }
  const cx=n('cx'),cy=n('cy'),rx=n(name==='circle'?'r':'rx'),ry=name==='circle'?rx:n('ry');
  if(rx<0||ry<0) throw Error('SVG ellipse radii must be positive.');
  return rx&&ry?`M${cx+rx} ${cy}A${rx} ${ry} 0 1 1 ${cx-rx} ${cy}A${rx} ${ry} 0 1 1 ${cx+rx} ${cy}Z`:null;
}
export function readVectorSvg(raw,{existingCount=0,Parser=globalThis.DOMParser,resolvePaint=resolveBrowserSvgPaint}={}) {
  if(typeof raw!=='string'||new TextEncoder().encode(raw).length>SVG_LIMITS.bytes||!raw.trim()) throw Error('Choose a nonempty SVG file up to 64 KB.');
  const text=raw.replace(/^\s*<\?xml\s[^?]*\?>/,'');
  if(/<!DOCTYPE|<!ENTITY|<\?/i.test(text)) throw Error('SVG document types, entities and processing instructions are unsupported.');
  if(typeof Parser!=='function') throw Error('SVG reading is unavailable in this workspace.');
  const document=new Parser().parseFromString(text,'image/svg+xml'),root=document.documentElement;
  if(document.getElementsByTagNameNS('*','parsererror').length||root?.localName!=='svg'||root.namespaceURI!==NS) throw Error('The file is not a well-formed SVG document.');
  const nodes=[root,...root.querySelectorAll('*')];
  if(nodes.length>512) throw Error('Use an SVG with up to 512 document elements.');
  for(const element of nodes) {
    if(['script','foreignObject','image','use','style','link','iframe','animate','animateTransform','set'].includes(element.localName)) throw Error('Use static vector shapes without scripts, images, references or animation.');
    for(const attribute of element.attributes) if(/^on/i.test(attribute.localName)||['href','base'].includes(attribute.localName)||/url\s*\(/i.test(attribute.value)) throw Error('External resources, event handlers and linked SVG paints are unsupported.');
  }
  const viewport=svgViewport(root.getAttribute('viewBox'),root.getAttribute('width'),root.getAttribute('height'),root.getAttribute('preserveAspectRatio')||undefined);
  const items=[];let omittedHidden=0,approximateOutlines=false;
  const defaults={fill:'#000000',stroke:'transparent','stroke-width':'1','fill-opacity':'1','stroke-opacity':'1','fill-rule':'nonzero',visibility:'visible',color:'#000000','stroke-linecap':'butt','stroke-linejoin':'miter','stroke-miterlimit':'4',opacity:'1',display:'inline'};
  const visit=(element,parent,matrix,groupOpacity,displayed,depth)=>{
    if(depth>12) throw Error('The SVG groups are nested too deeply.');
    const name=element.localName;
    if(['title','desc','metadata'].includes(name)) return;
    if(element.namespaceURI!==NS||(!['svg','g'].includes(name)&&!geometry[name])||(name==='svg'&&element!==root)) throw Error('This SVG contains an unsupported element. Convert text and special effects to plain paths first.');
    const allowed=new Set([...common,...(geometry[name]||[]),...(element===root?['viewBox','width','height','version','preserveAspectRatio']:[])]);
    for(const attribute of element.attributes) if(attribute.namespaceURI!=='http://www.w3.org/2000/xmlns/'&&!attribute.name.startsWith('aria-')&&!allowed.has(attribute.name)) throw Error('This SVG uses unsupported attributes or effects. Expand them to plain paths first.');
    const props=properties(element),style={...parent};
    for(const key of paints) if(props[key]!=null&&props[key].trim().toLowerCase()!=='inherit') style[key]=props[key];
    if(props.opacity==null) style.opacity='1';if(props.display==null) style.display='inline';
    const opacity=scalar(style.opacity,1,true),fillOpacity=scalar(style['fill-opacity'],1,true),strokeOpacity=scalar(style['stroke-opacity'],1,true),strokeWidth=scalar(style['stroke-width'],1);
    if(![opacity,fillOpacity,strokeOpacity].every(n=>Number.isFinite(n)&&n>=0&&n<=1)||strokeWidth<0||strokeWidth>64||!['nonzero','evenodd'].includes(style['fill-rule'])||!['visible','hidden','collapse'].includes(style.visibility)||!['inline','none'].includes(style.display)) throw Error('Use supported SVG opacity, visibility, fill rules and outline widths.');
    const next=multiplySvgMatrices(matrix,svgTransform(element.getAttribute('transform')||'')),shown=displayed&&style.display!=='none'&&groupOpacity*opacity>0,before=items.length;
    if(['svg','g'].includes(name)) {
      for(const child of element.children) visit(child,style,next,groupOpacity*opacity,shown,depth+1);
      if(opacity<1&&items.length-before>1) throw Error('Expand partly transparent SVG groups before importing.');
      return;
    }
    if(!shown||style.visibility!=='visible'){omittedHidden++;return;}
    if(element.children.length) throw Error('SVG geometry must not contain child elements.');
    const d=primitive(element);if(!d) return;
    const color=svgPaint(style.color.trim().toLowerCase()==='currentcolor'?parent.color:style.color,resolvePaint);
    const fill=alphaPaint(svgPaint(name==='line'?'none':style.fill.trim().toLowerCase()==='currentcolor'?color:style.fill,resolvePaint),fillOpacity),stroke=alphaPaint(svgPaint(style.stroke.trim().toLowerCase()==='currentcolor'?color:style.stroke,resolvePaint),strokeOpacity);
    if(stroke!=='transparent'&&strokeWidth>0) approximateOutlines=true;
    items.push({d,matrix:next,fill,stroke,strokeWidth,opacity:groupOpacity*opacity*100,fillRule:style['fill-rule']});
    if(items.length>SVG_LIMITS.items) throw Error('Use up to 128 visible vector shapes.');
  };
  visit(root,defaults,viewport.matrix,1,true,0);
  if(!items.length) throw Error('The SVG has no supported visible artwork.');
  return {items,existingCount,viewport:{width:viewport.width,height:viewport.height},omittedHidden,approximateOutlines};
}
