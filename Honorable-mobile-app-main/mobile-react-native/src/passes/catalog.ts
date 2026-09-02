export type PassId='normal'|'medium'|'plus'|'pro'|'superior';
export type SeranCreditModel='SERAN_V1'|'SERAN_V2'|'SERAN_V3'|'SERAN_ULTRA';
export const memoryPasses=[
  {id:'normal',name:'Normal',credits:50},{id:'medium',name:'Medium',credits:150},
  {id:'plus',name:'Plus',credits:400},{id:'pro',name:'Pro',credits:1000},
  {id:'superior',name:'Superior',credits:2500},
] as const;
export const seranCreditCosts=[
  {model:'SERAN_V1',name:'Seran V1',credits:1,available:true},
  {model:'SERAN_V2',name:'Seran V2',credits:2,available:true},
  {model:'SERAN_V3',name:'Seran V3',credits:4,available:false},
  {model:'SERAN_ULTRA',name:'Seran Ultra',credits:8,available:false},
] as const;
export const passById=(id:string)=>memoryPasses.find(pass=>pass.id===id);
export const modelCost=(model:string)=>seranCreditCosts.find(item=>item.model===model);
