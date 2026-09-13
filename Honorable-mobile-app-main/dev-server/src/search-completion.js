const crypto=require('node:crypto');
/** In-process trust boundary for the local shared-engine gateway. Never installed on a client.
 * No media, query, result, signing key or receipt is sent to the browser.
 * Production Android requires a separate attested completion provider and fails closed without it.
 */
class LocalSearchCompletions {
 constructor({clock=Date.now}={}){this.clock=clock;this.receipts=new Map()}
 issue({accountId,requestId,model,result}){
  if(!['SERAN_V1','SERAN_V2'].includes(model)||!Array.isArray(result?.results)||!result.results.length)throw Error('INVALID_ENGINE_COMPLETION');
  for(const[key,value]of this.receipts)if(value.expiresAt<this.clock())this.receipts.delete(key);
  const proof=crypto.randomBytes(32).toString('base64url');
  this.receipts.set(proof,{accountId,requestId,model,expiresAt:this.clock()+60000});return proof;
 }
 verify=({accountId,requestId,model,proof})=>{const receipt=this.receipts.get(proof);return !!receipt&&receipt.expiresAt>=this.clock()&&receipt.accountId===accountId&&receipt.requestId===requestId&&receipt.model===model};
}
module.exports={LocalSearchCompletions};
