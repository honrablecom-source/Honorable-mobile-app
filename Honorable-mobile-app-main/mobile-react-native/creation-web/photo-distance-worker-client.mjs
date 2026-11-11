import source from 'noble:photo-distance-worker';
export function makeDistanceWorker(){const url=URL.createObjectURL(new Blob([source],{type:'text/javascript'}));try{return{worker:new Worker(url),dispose:()=>URL.revokeObjectURL(url)};}catch(error){URL.revokeObjectURL(url);throw error;}}
