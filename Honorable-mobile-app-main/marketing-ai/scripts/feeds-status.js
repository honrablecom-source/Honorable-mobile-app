import { config } from '../src/config.js';
import { loadFeedRegistry, validateFeed } from '../src/core/free-sources.js';

const settings=config();
const registry=await loadFeedRegistry(settings.publicFeedsRegistry);
const custom=settings.rssFeedUrls.map((url,i)=>({name:`Custom feed ${i+1}`,platform:'FORUM',url,topic:'custom',enabled:true}));
for(const feed of [...registry,...custom]){
  const result=await validateFeed(feed);
  console.log(`FEED NAME: ${result.name}`);
  console.log(`DOMAIN: ${result.domain}`);
  console.log(`HTTP STATUS: ${result.httpStatus}`);
  console.log(`FORMAT: ${result.format}`);
  console.log(`ITEMS: ${result.items}`);
  console.log(result.status);
  console.log('');
}
