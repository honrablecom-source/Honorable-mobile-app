import http from 'node:http';
import { config } from './config.js';
import { createMarketingApp } from './app.js';
import { verifyConfig } from '../scripts/verify-config.js';

const settings=config();
const integrationStatus=await verifyConfig();
const server=http.createServer(createMarketingApp(settings,{integrationStatus}));
server.listen(settings.port,settings.host,()=>{
  console.log(`Honorable waitlist: ${settings.publicBaseUrl}/`);
  console.log(`Marketing dashboard: ${settings.publicBaseUrl}/dashboard`);
});
