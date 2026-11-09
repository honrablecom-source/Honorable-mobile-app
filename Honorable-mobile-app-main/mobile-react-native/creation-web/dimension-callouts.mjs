const LIMIT=1e6,FONT=18;
export const DIMENSION_UNITS=Object.freeze({canvas:'units',px:'px',mm:'mm',cm:'cm',m:'m',in:'in',ft:'ft'});
const finite=value=>Number.isFinite(value)&&Math.abs(value)<=LIMIT;
const style={strokeColor:'#111111',backgroundColor:'transparent',strokeWidth:1.5,strokeStyle:'solid',roughness:0,opacity:100,angle:0,roundness:null};
function segment(x1,y1,x2,y2,arrows=false){return{...style,type:arrows?'arrow':'line',x:x1,y:y1,width:Math.abs(x2-x1),height:Math.abs(y2-y1),points:[[0,0],[x2-x1,y2-y1]],startArrowhead:arrows?'triangle':null,endArrowhead:arrows?'triangle':null,...(arrows?{elbowed:false}:{} )};}
function label(text,x,y,axis){return{...style,type:'text',text,originalText:text,x,y,width:Array.from(text).length*FONT*.9,height:FONT*1.25,fontSize:FONT,fontFamily:2,lineHeight:1.25,textAlign:'left',verticalAlign:'top',autoResize:true,customData:{nobleDimensionAxis:axis}};}
/** Static axis-aligned drawing annotations. Calibration is supplied by the user, not inferred. */
export function dimensionCalloutPlan(bounds,knownWidth,unit='canvas',precision=2,offset=48,existingCount=1){
 if(!Array.isArray(bounds)||bounds.length!==4||!bounds.every(finite))throw Error('Select artwork within the supported canvas coordinates.');
 const [left,top,right,bottom]=bounds,width=right-left,height=bottom-top;
 if(width<16||height<16||width>LIMIT||height>LIMIT)throw Error('Select artwork at least 16 drawing units wide and high.');
 if(!Number.isFinite(knownWidth)||knownWidth<.001||knownWidth>LIMIT)throw Error('Enter a known width from 0.001 to 1,000,000.');
 if(typeof unit!=='string'||!Object.prototype.hasOwnProperty.call(DIMENSION_UNITS,unit))throw Error('Choose a supported dimension unit.');
 if(!Number.isInteger(precision)||precision<0||precision>4)throw Error('Use 0–4 decimal places.');
 if(!Number.isFinite(offset)||offset<24||offset>160)throw Error('Place callouts 24–160 drawing units from the artwork.');
 if(!Number.isInteger(existingCount)||existingCount<1||existingCount>500)throw Error('The source object count is invalid.');
 if(existingCount+8>500)throw Error('These callouts need eight more objects. Remove some objects first.');
 const calibratedHeight=knownWidth*height/width;
 if(!Number.isFinite(calibratedHeight)||calibratedHeight>LIMIT)throw Error('The calibrated height exceeds 1,000,000. Use a smaller known width.');
 const formatted=value=>{const rounded=Number(value.toFixed(precision));if(rounded<=0)throw Error('Increase decimal places or the known width so both labels stay above zero.');return String(rounded);};
 const widthText=`W ${formatted(knownWidth)} ${DIMENSION_UNITS[unit]}`,heightText=`H ${formatted(calibratedHeight)} ${DIMENSION_UNITS[unit]}`,horizontalY=bottom+offset,verticalX=right+offset;
 const plan=[segment(left,bottom+8,left,horizontalY+8),segment(right,bottom+8,right,horizontalY+8),segment(left,horizontalY,right,horizontalY,true),label(widthText,(left+right)/2,horizontalY+12,'width'),segment(right+8,top,verticalX+8,top),segment(right+8,bottom,verticalX+8,bottom),segment(verticalX,top,verticalX,bottom,true),label(heightText,verticalX+12,(top+bottom)/2,'height')];
 const anchors={width:{x:(left+right)/2,y:horizontalY+12},height:{x:verticalX+12,y:(top+bottom)/2}};
 for(const element of plan){const x=element.type==='text'&&element.customData.nobleDimensionAxis==='width'?element.x-element.width/2:element.x;const y=element.type==='text'&&element.customData.nobleDimensionAxis==='height'?element.y-element.height/2:element.y;if(![x,y,x+element.width,y+element.height].every(finite))throw Error('Move the artwork away from the canvas coordinate limit before adding callouts.');}
 return{plan,bounds:[...bounds],drawingWidth:width,drawingHeight:height,knownWidth,calibratedHeight,unit,precision,offset,widthText,heightText,anchors};
}
/** The renderer measures actual text first; center labels using those measured dimensions. */
export function dimensionLabelPatches(elements,result){
 if(!Array.isArray(elements)||!result?.anchors)throw Error('Dimension label placement is unavailable.');
 const found=new Set(),patches=[];
 for(const element of elements){const axis=element.customData?.nobleDimensionAxis;if(!axis)continue;if(element.type!=='text'||!['width','height'].includes(axis)||found.has(axis)||![element.width,element.height].every(value=>Number.isFinite(value)&&value>0))throw Error('The dimension label could not be measured.');found.add(axis);const anchor=result.anchors[axis],x=axis==='width'?anchor.x-element.width/2:anchor.x,y=axis==='height'?anchor.y-element.height/2:anchor.y;if(![x,y,x+element.width,y+element.height].every(finite))throw Error('The measured label exceeds the canvas coordinate limit.');patches.push({id:element.id,x,y});}
 if(found.size!==2)throw Error('Both dimension labels must be retained.');return patches;
}
