// Excalidraw 0.18.1 already falls back to full WOFF2 when its subsetter is blocked.
// Select that lossless path directly: no dynamic-JS decompressor initialization.
export function fullFontExportSource(source){
 const marker='QB=async(F,L)=>{try{let I=await NQ(F,L);return AI(I)}catch(I){return console.error("Skipped glyph subsetting",I),AI(F)}}';
 if(typeof source!=='string'||source.split(marker).length!==2)throw Error('Pinned SVG font export changed; review full-font embedding before bundling.');
 return source.replace(marker,'QB=async(F,L)=>AI(F)');
}
