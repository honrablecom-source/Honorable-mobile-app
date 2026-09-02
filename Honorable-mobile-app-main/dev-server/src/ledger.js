const fs=require('node:fs');const path=require('node:path');const crypto=require('node:crypto');
const passes={normal:50,medium:150,plus:400,pro:1000,superior:2500};
const costs={SERAN_V1:1,SERAN_V2:2,SERAN_V3:4,SERAN_ULTRA:8};
const available=new Set(['SERAN_V1','SERAN_V2']);
class Ledger{
  constructor(file){this.file=file;this.data=this.load()}
  load(){try{const data=JSON.parse(fs.readFileSync(this.file,'utf8'));return{accounts:{},identities:{},verifiedPurchases:{},deductions:{},...data}}catch{return{accounts:{},identities:{},verifiedPurchases:{},deductions:{}}}}
  save(){fs.mkdirSync(path.dirname(this.file),{recursive:true});const temp=`${this.file}.${process.pid}.tmp`;fs.writeFileSync(temp,JSON.stringify(this.data,null,2));fs.renameSync(temp,this.file)}
  account(id){if(!id)throw Object.assign(new Error('ACCOUNT_REQUIRED'),{status:401});return this.data.accounts[id]??={balance:0,subscription:{status:'NONE'},transactions:[],purchases:[]}}
  resolveIdentity(provider,subject){if(!provider||!subject)throw Object.assign(new Error('IDENTITY_REQUIRED'),{status:401});const key=`${provider}:${subject}`;if(this.data.identities[key])return this.data.identities[key];const accountId=crypto.randomUUID();this.data.identities[key]=accountId;this.account(accountId);this.save();return accountId}
  view(id){const a=this.account(id);return{accountId:id,balance:a.balance,creditsExpire:false,subscription:a.subscription,transactions:[...a.transactions].reverse()}}
  purchase(id,passId,storeTransactionId){const credits=passes[passId];if(!credits)throw Object.assign(new Error('UNKNOWN_PASS'),{status:400});const owner=this.data.verifiedPurchases[storeTransactionId];if(owner&&owner!==id)throw Object.assign(new Error('PURCHASE_OWNED_BY_ANOTHER_ACCOUNT'),{status:409});if(owner)return this.view(id);const a=this.account(id);a.balance+=credits;a.purchases.push({passId,storeTransactionId,credits});a.transactions.push(this.tx('PURCHASE',credits,a.balance,`${passId} Memory Pass`,storeTransactionId));this.data.verifiedPurchases[storeTransactionId]=id;this.save();return this.view(id)}
  deduct(id,model,requestId){if(!available.has(model))throw Object.assign(new Error('MODEL_UNAVAILABLE'),{status:409});if(!requestId)throw Object.assign(new Error('REQUEST_ID_REQUIRED'),{status:400});const owner=this.data.deductions[requestId];if(owner&&owner!==id)throw Object.assign(new Error('REQUEST_ID_OWNED_BY_ANOTHER_ACCOUNT'),{status:409});if(owner)return this.view(id);const cost=costs[model],a=this.account(id);if(a.balance<cost)throw Object.assign(new Error('INSUFFICIENT_CREDITS'),{status:402});a.balance-=cost;a.transactions.push(this.tx('DEBIT',-cost,a.balance,`${model} search`,requestId));this.data.deductions[requestId]=id;this.save();return this.view(id)}
  restore(id){const a=this.account(id);a.transactions.push(this.tx('RESTORE',0,a.balance,'Purchases restored'));this.save();return this.view(id)}
  tx(type,credits,balanceAfter,description,id=crypto.randomUUID()){return{id,type,credits,balanceAfter,description,createdAt:new Date().toISOString()}}
}
module.exports={Ledger,passes,costs,available};
