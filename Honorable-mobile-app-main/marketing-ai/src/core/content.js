export class MarketingLanguageModel {
  status() { return 'ABSTRACT'; }
  async generate() { throw new Error('MarketingLanguageModel not configured'); }
}

export class LocalRulesMarketingModel extends MarketingLanguageModel {
  status() { return 'LOCAL RULES'; }
  async generate({ format='TikTok', opportunity }) {
    const pain = opportunity?.painPoint || 'SEARCH';
    const hook = ({ VIDEO:'POV: You remember the exact moment—but not which video.', SCREENSHOT:'You know the screenshot exists. You just cannot find it.', PRIVACY:'What if photo search never needed your photos in the cloud?', ORGANIZATION:'Stop organizing 20,000 photos just to find one.', SEARCH:'POV: You have 28,000 photos and need one from years ago.' }[pain] || 'You remember the moment. Now find it.');
    return { format, hook, beats:[
      'Show the real problem: endless scrolling through a large library.',
      'Type a remembered description: “my son playing tennis outside wearing blue.”',
      'Reveal the strongest real photo or video match.',
      'Close on privacy: “Your memories stay yours.”'
    ], cta:'You remember the moment. Honorable finds it. Join the waitlist.', claims:'Use only validated local search behavior. No testimonial or unvalidated performance claim.', status:'DRAFT', generatedBy:'local-rules' };
  }
}

export class MarketingResponseGenerator {
  constructor(waitlistUrl){this.waitlistUrl=waitlistUrl;}
  publicWaitlistUrl(){try{const url=new URL(this.waitlistUrl);return url.protocol==='https:'&&!['localhost','127.0.0.1','0.0.0.0'].includes(url.hostname)&&!url.hostname.endsWith('.localhost')?url:null;}catch{return null;}}
  outreachStatus(){return this.publicWaitlistUrl()?'READY_FOR_REVIEW':'BLOCKED_NO_PUBLIC_WAITLIST_URL';}
  attributedUrl(opportunity){const url=this.publicWaitlistUrl();if(!url)return null;url.searchParams.set('utm_source',opportunity.platform.toLowerCase().replace(/[^a-z0-9]+/g,'-'));url.searchParams.set('utm_campaign',opportunity.painPoint.toLowerCase().replaceAll('_','-'));return url.toString();}
  generateResponse(opportunity){
    const need=({PHOTO_SEARCH:'finding a photo from what you remember instead of its date',VIDEO_SEARCH:'finding a remembered moment inside an old video',SCREENSHOT_SEARCH:'finding a screenshot from the details you remember',DATE_UNKNOWN:'finding a photo without remembering when it was taken',TOO_MANY_PHOTOS:'searching a large camera roll without endless scrolling',DOCUMENT_SEARCH:'finding an old receipt or document screenshot',PRIVACY_CONCERN:'searching a photo library privately',CURRENT_SEARCH_TOOL_FAILED:'using a more descriptive search when the current tool falls short',PHOTO_ORGANIZATION:'finding things without maintaining more albums'})[opportunity.painPoint]||'finding a remembered photo or video by description';
    const openings={UNKNOWN_DATE_SEARCH:'I’m building Honorable around exactly this problem.',DESCRIPTION_BASED_SEARCH:'Remembering the scene should be enough to find the photo.',SCREENSHOT_CONTENT_SEARCH:'A screenshot should be searchable by the details inside it.',VIDEO_CONTENT_SEARCH:'Finding one remembered video moment should not require replaying every clip.',LARGE_LIBRARY_SEARCH:'Large camera rolls are where ordinary search breaks down.',CURRENT_VISUAL_SEARCH_FAILED:'I’ve run into the limits of date-and-keyword photo search too.',OBJECT_SEARCH:'The object you remember can be the search query.',SCENE_SEARCH:'The scene you remember can be the search query.',ACTIVITY_SEARCH:'The activity you remember can be the search query.',NATURAL_LANGUAGE_MEDIA_SEARCH:'That is the use case Honorable is being built for.'};
    const link=this.attributedUrl(opportunity);const ending=link?` We’re testing early access now: ${link}`:' [LINK NOT PUBLISHABLE — set HONORABLE_WAITLIST_URL to a public HTTPS URL]';
    return `${openings[opportunity.supportedSearchNeed]||'I’m building Honorable for this kind of media-search problem.'} It searches your library locally by what you remember, specifically ${need}.${ending}`;
  }
  generateAutoReply(opportunity){const attributed=this.attributedUrl(opportunity);if(!attributed)return this.generateResponse(opportunity);const url=new URL(attributed);url.searchParams.set('utm_medium','auto_reply');return this.generateResponse(opportunity).replace(attributed,url.toString());}
}

export class ContentGenerator {
  constructor(model = new LocalRulesMarketingModel()) { this.model = model; }
  generate(input) { return this.model.generate(input); }
}
