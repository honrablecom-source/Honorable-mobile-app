const INK='#111111';
// Each internal cut is stored once. Neighbors refer to its exact reversed traversal.
const PROFILE=[[0,0],[.30,0],[.40,0],[.42,.03],[.38,.07],[.36,.15],[.39,.21],[.45,.24],[.55,.24],[.61,.21],[.64,.15],[.62,.07],[.58,.03],[.60,0],[.70,0],[1,0]];
const round=n=>Math.round(n*1e8)/1e8;
export function jigsawOptions(input){
 if(!input||![input.width,input.height].every(n=>Number.isInteger(n)&&n>=240&&n<=1440))throw Error('Use whole drawing dimensions from 240 to 1440 pixels.');
 if(![input.columns,input.rows].every(n=>Number.isInteger(n)&&n>=2&&n<=12)||input.width/input.columns<24||input.height/input.rows<24)throw Error('Use 2–12 rows and columns with cells at least 24 pixels wide and high.');
 if(!Number.isInteger(input.seed)||input.seed<1||input.seed>2147483647)throw Error('Use a whole seed from 1 to 2147483647.');
 if(typeof input.title!=='string'||!input.title.trim()||Array.from(input.title).length>48||/[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(input.title))throw Error('Use a title of 1–48 visible characters on one line.');
 return{title:input.title.trim(),width:input.width,height:input.height,columns:input.columns,rows:input.rows,seed:input.seed};
}
export function jigsawGeometry(input){
 const options=jigsawOptions(input),{width,height,columns,rows}=options,cw=width/columns,ch=height/rows,depth=Math.min(cw,ch),seams=[],pieces=Array.from({length:columns*rows},(_,i)=>({row:Math.floor(i/columns),column:i%columns,edges:{}}));let random=options.seed;
 const next=()=>{random^=random<<13;random^=random>>>17;random^=random<<5;return(random>>>0)&1?1:-1;};
 const add=(axis,row,column)=>{const sign=next(),id=`${axis}:${row}:${column}`,points=PROFILE.map(([t,n])=>axis==='h'?[round((column+t)*cw),round(row*ch+sign*n*depth)]:[round(column*cw+sign*n*depth),round((row+t)*ch)]),owners=axis==='h'?[(row-1)*columns+column,row*columns+column]:[row*columns+column-1,row*columns+column];seams.push({id,axis,row,column,sign,points,owners});if(axis==='h'){pieces[owners[0]].edges.bottom={id,reversed:true};pieces[owners[1]].edges.top={id,reversed:false};}else{pieces[owners[0]].edges.right={id,reversed:false};pieces[owners[1]].edges.left={id,reversed:true};}};
 for(let row=1;row<rows;row++)for(let column=0;column<columns;column++)add('h',row,column);
 for(let row=0;row<rows;row++)for(let column=1;column<columns;column++)add('v',row,column);
 return{options,seams,pieces,cellWidth:cw,cellHeight:ch};
}
const text=(value,x,y,size)=>({type:'text',text:value,x,y,fontSize:size,fontFamily:2,strokeColor:INK,opacity:100,angle:0});
export function jigsawArtwork(input){
 const geometry=jigsawGeometry(input),{options}=geometry,letters=Array.from(options.title),title=[];while(letters.length)title.push(letters.splice(0,24).join(''));const top=title.length*30+52,left=16,plan=[text(title.join('\n'),left,0,24),text(`${options.columns} × ${options.rows} = ${options.columns*options.rows} pieces · seed ${options.seed}`,left,title.length*30,14)];
 plan.push({type:'rectangle',x:left,y:top,width:options.width,height:options.height,strokeColor:INK,backgroundColor:'transparent',fillStyle:'solid',strokeWidth:1,roughness:0,opacity:100,customData:{nobleJigsawBorder:true}});
 for(const seam of geometry.seams){const origin=seam.points[0],points=seam.points.map(p=>[round(p[0]-origin[0]),round(p[1]-origin[1])]);plan.push({type:'line',x:left+origin[0],y:top+origin[1],width:Math.max(...points.map(p=>p[0]))-Math.min(...points.map(p=>p[0])),height:Math.max(...points.map(p=>p[1]))-Math.min(...points.map(p=>p[1])),points,strokeColor:INK,strokeWidth:1,roughness:0,opacity:100,startArrowhead:null,endArrowhead:null,customData:{nobleJigsawSeam:seam.id,nobleJigsawOwners:[...seam.owners],nobleJigsawSign:seam.sign}});}
 plan.push(text('Shared polygonal tab cuts; straight outer border.\nNo kerf, material or printer-scale allowance.\nTest a scrap copy before physical cutting.',left,top+options.height+20,14));
 return{options,geometry,plan,width:Math.max(512,options.width+32),height:top+options.height+96};
}
