import { config } from '../src/config.js';
import { ResilientWebDiscoveryCollector } from '../src/core/web-discovery.js';

const status=await new ResilientWebDiscoveryCollector(config()).status();
const settings=config();
console.log(`ZERO COST MODE: ${settings.zeroCostMode?'ON':'OFF'}`);
console.log(`GOOGLE: ${status.google}`);
console.log(`SEARXNG: ${status.searxng}${status.workingUpstreams.length?` (${status.workingUpstreams.join(', ')})`:''}`);
console.log(`RSS/FEEDS: ${settings.rssFeedUrls.length?`${settings.rssFeedUrls.length} CONFIGURED`:'NO CUSTOM FEEDS CONFIGURED'}`);
console.log('FORUMS: HACKER NEWS PUBLIC API');
console.log('REDDIT: PUBLIC RSS (LIVE CHECK DURING DISCOVER)');
console.log(`OTHER FREE SOURCES: ${settings.customerDiscoveryYoutubeEnabled?'YOUTUBE OFFICIAL FREE QUOTA':'YOUTUBE SECONDARY / DISABLED'}, PUBLIC_WEB MANUAL_ASSIST`);
console.log(`ACTIVE PROVIDER: ${status.activeProvider}`);
