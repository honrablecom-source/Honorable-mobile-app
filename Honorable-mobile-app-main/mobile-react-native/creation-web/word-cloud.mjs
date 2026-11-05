import cloud from 'd3-cloud';
export const CLOUD_WIDTH=960,CLOUD_HEIGHT=640,MAX_CLOUD_WORDS=24;
export function cloudWords(input){
 if(typeof input!=='string'||input.length>2048)throw Error('Use up to 2,048 characters of word | weight rows.');
 const lines=input.replace(/\r\n/g,'\n').trim().split('\n');if(lines.length<2||lines.length>MAX_CLOUD_WORDS)throw Error('Use 2–24 word | weight rows.');
 const seen=new Set();const words=lines.map(line=>{const fields=line.split('|');if(fields.length!==2)throw Error('Put a word or short phrase and its weight on each row: Nature | 10.');
  const text=fields[0].trim(),raw=fields[1].trim();if(!text||Array.from(text).length>24||/[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(text))throw Error('Use visible words or short phrases up to 24 characters.');
  const key=text.normalize('NFKC').toLowerCase();if(seen.has(key))throw Error('Use each word or phrase once; combine duplicate weights.');seen.add(key);
  if(!/^\d{1,3}$/.test(raw)||Number(raw)<1||Number(raw)>100)throw Error('Use whole-number weights from 1 to 100.');return{text,value:Number(raw)};
 });
 const largest=Math.max(...words.map(word=>word.value));return words.map(word=>({...word,size:Math.round(18+42*Math.sqrt(word.value/largest))}));
}
export function cloudPlan(input,placed){
 const expected=cloudWords(input);if(!Array.isArray(placed)||placed.length!==expected.length)throw Error('Not every word fits. Shorten phrases or use fewer rows, then preview again.');
 const byText=new Map(expected.map(word=>[word.text,word]));const seen=new Set();return placed.map(word=>{
  const original=byText.get(word.text);if(!original||seen.has(word.text)||word.size!==original.size||![word.x,word.y,word.boxWidth,word.boxHeight].every(Number.isFinite)||word.boxWidth<=0||word.boxHeight<=0||word.x<0||word.y<0||word.x+word.boxWidth>CLOUD_WIDTH||word.y+word.boxHeight>CLOUD_HEIGHT)throw Error('The word layout is invalid. Preview again.');seen.add(word.text);
  return{type:'text',text:word.text,x:word.x,y:word.y,fontSize:word.size,fontFamily:2,strokeColor:'#111111',opacity:100,angle:0};
 });
}
// Reserve full rectangles rather than interlocking glyphs: text remains independently editable.
function rectangleCanvas(){const canvas=document.createElement('canvas'),context=canvas.getContext('2d',{willReadFrequently:true});if(!context)throw Error('Local text layout is unavailable.');
 context.fillText=(text,x,y)=>{const size=Number(/([\d.]+)px/.exec(context.font)?.[1]);if(!Number.isFinite(size))throw Error('The text font could not be measured.');context.fillRect(x-6,y-size-4,context.measureText(text).width+12,size*1.3+8);};context.strokeText=()=>{};return canvas;
}
export async function layoutCloud(input,requireCurrent=()=>{},signal){
 requireCurrent();const words=cloudWords(input);await document.fonts.ready;requireCurrent();if(signal?.aborted)throw Error('Word-cloud preview cancelled.');
 const measuring=document.createElement('canvas').getContext('2d');if(!measuring)throw Error('Local text layout is unavailable.');
 return new Promise((resolve,reject)=>{let finished=false,layout,timer;const finish=(failure,placed)=>{if(finished)return;finished=true;clearTimeout(timer);signal?.removeEventListener('abort',abort);layout?.stop();if(failure){reject(failure);return;}try{requireCurrent();resolve({words:placed,plan:cloudPlan(input,placed),width:CLOUD_WIDTH,height:CLOUD_HEIGHT});}catch(error){reject(error);}};const abort=()=>finish(Error('Word-cloud preview cancelled.'));
  signal?.addEventListener('abort',abort,{once:true});timer=setTimeout(()=>finish(Error('Layout took too long. Use fewer or shorter phrases.')),8000);
  let seed=123456789;const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
  try{layout=cloud().size([CLOUD_WIDTH,CLOUD_HEIGHT]).words(words.map(word=>({...word}))).font('Helvetica').fontSize(word=>word.size).rotate(0).padding(0).random(random).timeInterval(8).canvas(rectangleCanvas).on('word',()=>{try{requireCurrent();}catch(error){finish(error);}}).on('end',placed=>{
   const measured=placed.map(word=>{measuring.font=`${word.size+1}px Helvetica`;const width=measuring.measureText(word.text).width;return{text:word.text,size:word.size,x:word.x+CLOUD_WIDTH/2-Math.floor(width/2),y:word.y+CLOUD_HEIGHT/2-word.size,boxWidth:width,boxHeight:word.size*1.25};});finish(null,measured);
  });layout.start();}catch(error){finish(error);}
 });
}
