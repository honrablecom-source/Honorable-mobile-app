import source from 'noble:photo-channel-pack-worker';
export function makeChannelPackWorker(){const url=URL.createObjectURL(new Blob([source],{type:'text/javascript'}));try{return{worker:new Worker(url),dispose:()=>URL.revokeObjectURL(url)};}catch(error){URL.revokeObjectURL(url);throw error;}}
