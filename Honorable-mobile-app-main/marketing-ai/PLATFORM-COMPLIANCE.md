# Platform integration status

The dashboard never auto-posts, comments, DMs, or harvests private data. A human explicitly refreshes discovery and reviews public-source links. `LIVE`, `MANUAL`, and `MOCK` records are labeled separately.

## YouTube — official YouTube Data API v3

- Authentication: Google Cloud API key with YouTube Data API v3 enabled.
- Access: `search.list` for public video snippets, followed by `videos.list` for public statistics. No video is downloaded or rehosted.
- Quota: search has a material quota cost; one bounded query is used per manual refresh and results are limited to 25.
- Approval: standard API project/terms; additional quota requires Google's audit/extension process.
- Allowed behavior here: discover public videos, retain minimal metadata, score them, and link to YouTube for human review.

## Instagram — official Instagram Graph API

- Authentication: long-lived Meta access token and an Instagram Business/Creator account ID connected to a Facebook Page.
- Permissions/review: current Meta eligibility plus `instagram_basic` and `pages_read_engagement`; hashtag/public-content access is subject to the current Instagram API product, permissions, app review, and account limitations.
- Quota: four bounded hashtag lookups per manual refresh; Graph API usage headers and current Meta limits must be monitored in production.
- Allowed behavior here: hashtag search and permitted public media fields only. No HTML scraping, messaging, commenting, or publishing.
- Status remains `CONFIGURATION REQUIRED` until credentials are present; credentials alone do not guarantee Meta approval.

## Reddit — official OAuth Data API, approval gated

- Authentication: registered OAuth client credentials and a descriptive User-Agent.
- Approval: this is commercial marketing intelligence. Set `REDDIT_COMMERCIAL_APPROVAL=true` only after express Reddit approval/separate agreement. Until then the adapter makes no request and reports `APPROVAL REQUIRED`.
- Quota: honor Reddit response rate-limit headers and agreement-specific limits. Refresh is manual and bounded to 25 public posts.
- Allowed behavior here: approved public post search, minimal metadata, scoring, and a reddit.com review link. No comments, DMs, private data, or model training.

## TikTok — manual only

- TikTok Research Tools are not used for commercial marketing discovery. The Commercial Content API concerns commercial/advertising content and is not treated as a general organic conversation search API.
- Status: `OFFICIAL API UNAVAILABLE` for this use case. Accept only manually entered observations or exports obtained from an approved TikTok business/ads tool in a future import flow.
- No TikTok scraping exists.

## Web discovery — Google Programmable Search JSON API

- Authentication: API key plus Programmable Search Engine ID.
- Quota/billing: Google project quota and billing terms apply. One bounded query returns at most 10 links per manual refresh.
- Allowed behavior here: search-result title, snippet, and public URL only. No aggressive follow-up crawling is performed.

Official references: YouTube Data API `search.list` and `videos.list` documentation; Meta Instagram Platform/Graph API documentation; Reddit Developer Terms and Data API Terms; TikTok for Developers product documentation; Google Programmable Search JSON API documentation. Re-check provider terms before production launch because permissions and products change.
