// Independent dense Gaussian elimination of all patch constraints; no conjugate-gradient recurrence.
function gradientOracle(source,sw,sh,destination,dw,dh,rect,placement){
 const flat=pixels=>Array.from({length:pixels.length},(_,i)=>i%4===3?255:Math.floor(pixels[i]*pixels[i-i%4+3]/255+255*(1-pixels[i-i%4+3]/255)+.5)),s=flat(source),data=flat(destination),[sx,sy,w,h]=rect,[dx,dy]=placement,n=w*h,rows=Array.from({length:n},()=>Array(n+3).fill(0));
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const row=rows[y*w+x];row[y*w+x]=4;for(const[ox,oy]of[[-1,0],[1,0],[0,-1],[0,1]]){const ix=x+ox,iy=y+oy,inside=ix>=0&&iy>=0&&ix<w&&iy<h; if(inside)row[iy*w+ix]-=1;for(let c=0;c<3;c++){row[n+c]+=s[((sy+y)*sw+sx+x)*4+c]-s[((sy+iy)*sw+sx+ix)*4+c];if(!inside)row[n+c]+=data[((dy+iy)*dw+dx+ix)*4+c];}}}
 for(let pivot=0;pivot<n;pivot++){let selected=pivot;for(let y=pivot+1;y<n;y++)if(Math.abs(rows[y][pivot])>Math.abs(rows[selected][pivot]))selected=y;[rows[pivot],rows[selected]]=[rows[selected],rows[pivot]];const diagonal=rows[pivot][pivot];for(let x=pivot;x<n+3;x++)rows[pivot][x]/=diagonal;for(let y=0;y<n;y++)if(y!==pivot){const multiplier=rows[y][pivot];for(let x=pivot;x<n+3;x++)rows[y][x]-=multiplier*rows[pivot][x];}}
 const solutions=[0,1,2].map(c=>rows.map(row=>row[n+c]));for(let y=0;y<h;y++)for(let x=0;x<w;x++)for(let c=0;c<3;c++)data[((dy+y)*dw+dx+x)*4+c]=Math.floor(Math.max(0,Math.min(255,solutions[c][y*w+x]))+.5);
 return{data,solutions};
}
module.exports={gradientOracle};
