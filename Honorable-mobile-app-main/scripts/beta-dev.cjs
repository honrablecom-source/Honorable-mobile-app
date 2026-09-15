// An isolated, invite-only local BETA channel. This is not a production deployment.
const path=require('node:path');
process.env.HONORABLE_RELEASE_CHANNEL='BETA';
process.env.HONORABLE_LEDGER_PATH=process.env.HONORABLE_LEDGER_PATH||path.resolve(__dirname,'../dev-server/data/beta-ledger.json');
require('./admin-dev.cjs');
