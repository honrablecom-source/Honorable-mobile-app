const fs=require('node:fs'),path=require('node:path');
process.env.HONORABLE_ADMIN_CONFIG=process.env.HONORABLE_ADMIN_CONFIG||path.resolve(__dirname,'../dev-server/data/admin-users.json');
if(!fs.existsSync(process.env.HONORABLE_ADMIN_CONFIG)){console.error('Run npm run admin:setup first to create an explicit local OWNER login.');process.exit(1)}
// Enable admin on the same account process as the consumer web test. No second ledger writer.
require('./web-test.cjs');
console.log('Private developer console: http://localhost:'+(process.env.HONORABLE_WEB_PORT||4174)+'/admin');
