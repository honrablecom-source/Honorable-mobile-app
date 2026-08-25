import { spawnSync } from 'node:child_process';

const names=['Honorable','HonorableApp','GetHonorable'];const available=spawnSync('maigret',['--version'],{encoding:'utf8'});
console.log('BRAND USERNAME AUDIT — SEPARATE FROM CUSTOMER DISCOVERY');
if(available.error?.code==='ENOENT'){console.log('STATUS: MAIGRET NOT INSTALLED');console.log('No username searches were attempted.');process.exit(0);}
if(available.status!==0){console.log('STATUS: MAIGRET UNAVAILABLE');process.exit(0);}
for(const name of names){console.log(`\nBRAND: ${name}`);const result=spawnSync('maigret',[name,'--json','simple','--no-color'],{stdio:'inherit'});if(result.status!==0)console.log(`STATUS: CHECK FAILED (${result.status})`);}
