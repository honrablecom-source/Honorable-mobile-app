import path from 'node:path';

export function config(env = process.env, cwd = process.cwd()) {
  const validDatabaseUrl=(()=>{try{return ['postgres:','postgresql:'].includes(new URL(env.DATABASE_URL).protocol);}catch{return false;}})();
  const sender=(env.EMAIL_FROM?.match(/<([^>]+)>/)?.[1]||env.EMAIL_FROM||'').trim();
  const zeroCostMode=env.ZERO_COST_MODE!=='false';
  const hasDiscovery=Boolean(env.YOUTUBE_API_KEY||env.SEARXNG_BASE_URL||(env.GOOGLE_SEARCH_API_KEY&&env.GOOGLE_SEARCH_ENGINE_ID)||env.RSS_FEED_URLS||zeroCostMode);
  return {
    host: env.HOST || '127.0.0.1',
    port: Number(env.PORT || 4173),
    publicBaseUrl: env.PUBLIC_BASE_URL || 'http://127.0.0.1:4173',
    waitlistUrl: env.HONORABLE_WAITLIST_URL || env.PUBLIC_BASE_URL || 'http://127.0.0.1:4173/',
    autoReply:{dryRun:env.AUTO_REPLY_DRY_RUN!=='false',relevanceThreshold:Number(env.AUTO_REPLY_RELEVANCE_THRESHOLD||85),autoReplyThreshold:Number(env.AUTO_REPLY_SCORE_THRESHOLD||88),productFitThreshold:Number(env.OUTREACH_PRODUCT_FIT_THRESHOLD||80),platforms:{}},
    dataDir: path.resolve(cwd, env.DATA_DIR || './data'),
    llmProvider: env.MARKETING_LLM_PROVIDER || 'local-rules',
    waitlistStore: env.WAITLIST_STORE || (validDatabaseUrl?'postgres':'local-json'),
    databaseUrl: env.DATABASE_URL,
    // Production sending is opt-in even when credentials exist. This prevents an
    // unverified EMAIL_FROM value from turning waitlist delivery on implicitly.
    emailProvider: env.EMAIL_PROVIDER || 'disabled',
    emailApiKey: env.RESEND_API_KEY,
    emailFrom: env.EMAIL_FROM,
    emailDevelopmentFrom: env.RESEND_DEV_FROM || 'Honorable Development <onboarding@resend.dev>',
    emailDevelopmentRecipient: env.RESEND_DEV_RECIPIENT || sender || undefined,
    discoveryMode: env.DISCOVERY_MODE || (hasDiscovery?'live':'mock'),
    discoveryQueryBudget:Number(env.DISCOVERY_QUERY_BUDGET||5),
    customerDiscoveryYoutubeEnabled:env.CUSTOMER_DISCOVERY_YOUTUBE_ENABLED==='true',
    zeroCostMode,
    rssFeedUrls:String(env.RSS_FEED_URLS||'').split(',').map(x=>x.trim()).filter(Boolean),
    publicFeedsRegistry:path.resolve(cwd,env.PUBLIC_FEEDS_REGISTRY||'./config/public-feeds.json'),
    webSearch: { apiKey:env.GOOGLE_SEARCH_API_KEY, engineId:env.GOOGLE_SEARCH_ENGINE_ID },
    braveSearch:{apiKey:env.BRAVE_SEARCH_API_KEY},
    searxng:{baseUrl:env.SEARXNG_BASE_URL,engines:env.SEARXNG_ENGINES},
    credentials: {
      tiktok: env.TIKTOK_ACCESS_TOKEN,
      instagram: env.INSTAGRAM_ACCESS_TOKEN && env.INSTAGRAM_BUSINESS_ACCOUNT_ID ? { token:env.INSTAGRAM_ACCESS_TOKEN, accountId:env.INSTAGRAM_BUSINESS_ACCOUNT_ID } : null,
      youtube: env.YOUTUBE_API_KEY,
      reddit: env.REDDIT_CLIENT_ID && env.REDDIT_CLIENT_SECRET && env.REDDIT_COMMERCIAL_APPROVAL === 'true' ? { clientId:env.REDDIT_CLIENT_ID, clientSecret:env.REDDIT_CLIENT_SECRET, userAgent:env.REDDIT_USER_AGENT } : null,
      redditApproval: env.REDDIT_COMMERCIAL_APPROVAL === 'true',
      googleTrends: env.GOOGLE_TRENDS_PROVIDER_KEY,
      email: env.RESEND_API_KEY && env.EMAIL_FROM
    }
  };
}
