import source from 'noble:photo-clone-worker';
export function makeCloneWorker(){
 const url=URL.createObjectURL(new Blob([source],{type:'text/javascript'}));
 try{return{worker:new Worker(url),dispose:()=>URL.revokeObjectURL(url)};}
 catch(error){URL.revokeObjectURL(url);throw error;}
}
