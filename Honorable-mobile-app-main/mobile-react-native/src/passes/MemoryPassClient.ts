import type{PassId,SeranCreditModel}from'./catalog';
export type MemoryTransaction={id:string;type:'PURCHASE'|'DEBIT'|'RESTORE';credits:number;balanceAfter:number;createdAt:string;description:string};
export type MemoryAccount={accountId:string;balance:number;creditsExpire:false;subscription:{status:'NONE'|'ACTIVE'|'EXPIRED';tier?:string};transactions:MemoryTransaction[]};
export class MemoryPassClient{
  private token?:string;
  constructor(private baseUrl=''){}
  configure=(baseUrl:string)=>{this.baseUrl=baseUrl.replace(/\/$/,'')};
  private async call<T>(path:string,init?:RequestInit,authenticated=true):Promise<T>{const response=await fetch(`${this.baseUrl}${path}`,{...init,headers:{'content-type':'application/json',...(authenticated&&this.token?{authorization:`Bearer ${this.token}`}:{ }),...init?.headers}});const body=await response.json();if(!response.ok)throw new Error(body.error??'Memory Pass server unavailable');return body as T}
  signInDev=async(email:string)=>{const value=await this.call<{accessToken:string}>('/dev/auth/token',{method:'POST',body:JSON.stringify({email})},false);this.token=value.accessToken;return this.account()};
  signInGoogle=async(idToken:string)=>{const value=await this.call<{accessToken:string}>('/v1/auth/google',{method:'POST',body:JSON.stringify({idToken})},false);this.token=value.accessToken;return this.account()};
  signOut=()=>{this.token=undefined};
  authenticated=()=>!!this.token;
  account=()=>this.call<MemoryAccount>('/v1/account');
  buyDev=(passId:PassId)=>this.call<MemoryAccount>('/dev/purchases',{method:'POST',body:JSON.stringify({passId})});
  deduct=(model:SeranCreditModel,requestId:string)=>this.call<MemoryAccount>('/v1/credits/deduct',{method:'POST',body:JSON.stringify({model,requestId})});
  restore=()=>this.call<MemoryAccount>('/v1/purchases/restore',{method:'POST',body:'{}'});
}
