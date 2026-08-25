import { databaseStatus } from './database-check.js';

const result=await databaseStatus();
console.log(`DATABASE: ${result.status}`);
console.log(`SSL: ${result.ssl}`);
if(result.reason)console.log(`REASON: ${result.reason}`);
if(result.status==='FAILED')process.exitCode=1;
