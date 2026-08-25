export class CampaignManager {
  constructor(store) { this.store=store; }
  create(input) {
    if (!input?.name || !input?.message) throw new Error('Campaign name and message are required');
    return this.store.addCampaign(input);
  }
  approveDraft(id) { return this.store.approveDraft(id); }
  publishingStatus() { return 'DISABLED — APPROVAL PRODUCES MANUAL-READY DRAFTS ONLY'; }
}

export class WaitlistAnalytics {
  constructor(store) { this.store=store; }
  async snapshot() { const { publicAnalytics }=await import('../store.js'); return publicAnalytics(await this.store.snapshot()); }
}
