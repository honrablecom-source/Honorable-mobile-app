import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const emptyState = () => ({ waitlist:[], events:[], campaigns:[], drafts:[], emailDeliveries:[],opportunities:[],discoveryRuns:[],autoReplySettings:{globalEnabled:false,killSwitch:false,platforms:{}},autoReplyProposals:[],autoReplyAudit:[] });

export class JsonStore {
  constructor(directory) { this.directory = directory; this.file = path.join(directory, 'marketing-state.json'); this.state = emptyState(); this.ready = this.load(); this.queue = Promise.resolve(); }
  async load() { await mkdir(this.directory, { recursive:true }); try { this.state = { ...emptyState(), ...JSON.parse(await readFile(this.file, 'utf8')) }; } catch (error) { if (error.code !== 'ENOENT') throw error; } }
  async persist() { const temporary = `${this.file}.tmp`; await writeFile(temporary, JSON.stringify(this.state, null, 2), { mode:0o600 }); await rename(temporary, this.file); }
  mutate(action) { this.queue = this.queue.then(async () => { await this.ready; const result = action(this.state); await this.persist(); return result; }); return this.queue; }
  async snapshot() { await this.ready; return structuredClone(this.state); }
  addWaitlist({ email, device='Both', consent, source='direct', campaign='organic', content='unknown' }) {
    return this.mutate(state => {
      const normalized = email.trim().toLowerCase();
      const existing = state.waitlist.find(x => x.email === normalized);
      if (existing) return { member:existing, duplicate:true };
      const now=new Date().toISOString();
      const member = { id:crypto.randomUUID(), email:normalized, device, consent:Boolean(consent), consentAt:now, source, campaign, content, createdAt:now, active:true, unsubscribeToken:crypto.randomBytes(24).toString('hex'), referralCode:crypto.randomBytes(6).toString('hex') };
      state.waitlist.push(member); return { member, duplicate:false };
    });
  }
  unsubscribe(token) { return this.mutate(state => { const member=state.waitlist.find(x=>x.unsubscribeToken===token); if (!member) return false; member.active=false; member.unsubscribedAt=new Date().toISOString(); return true; }); }
  addEvent(event) { return this.mutate(state => { state.events.push({ id:crypto.randomUUID(), type:event.type, source:event.source||'direct', campaign:event.campaign||'organic', content:event.content||'unknown', createdAt:new Date().toISOString() }); if(state.events.length>10000)state.events=state.events.slice(-10000); return true; }); }
  addDraft(draft) { return this.mutate(state => { const saved={ ...draft,id:crypto.randomUUID(),status:'DRAFT',createdAt:new Date().toISOString() }; state.drafts.push(saved); return saved; }); }
  saveDiscovery(opportunities,run={}) { return this.mutate(state=>{const previous=new Map(state.opportunities.map(x=>[x.sourceUrl||x.id,x]));for(const item of opportunities){const old=previous.get(item.sourceUrl||item.id);previous.set(item.sourceUrl||item.id,{...old,...item,status:old?.status||item.status,suggestedResponse:old?.suggestedResponse||item.suggestedResponse,discoveredAt:old?.discoveredAt||new Date().toISOString(),lastSeenAt:new Date().toISOString()});}state.opportunities=[...previous.values()].sort((a,b)=>b.score-a.score).slice(0,1000);state.discoveryRuns.push({...run,found:opportunities.length,createdAt:new Date().toISOString()});state.discoveryRuns=state.discoveryRuns.slice(-100);return opportunities;}); }
  updateOpportunity(id,updates){return this.mutate(state=>{const item=state.opportunities.find(x=>x.id===id);if(!item)return null;Object.assign(item,updates,{updatedAt:new Date().toISOString()});return item;});}
  saveAutoReplyProposals(items){return this.mutate(state=>{state.autoReplyProposals=items.map(item=>({...item,id:item.id||crypto.randomUUID(),createdAt:new Date().toISOString()}));return state.autoReplyProposals;});}
  updateAutoReplyProposal(id,updates){return this.mutate(state=>{const item=state.autoReplyProposals.find(x=>x.id===id);if(!item)return null;Object.assign(item,updates,{updatedAt:new Date().toISOString()});return item;});}
  setAutoReplySettings(updates){return this.mutate(state=>{Object.assign(state.autoReplySettings,updates,{updatedAt:new Date().toISOString()});return state.autoReplySettings;});}
  addAutoReplyAudit(entry){return this.mutate(state=>{const saved={...entry,id:crypto.randomUUID(),timestamp:new Date().toISOString()};state.autoReplyAudit.push(saved);return saved;});}
  approveDraft(id) { return this.mutate(state => { const draft=state.drafts.find(x=>x.id===id); if(!draft)return null; draft.status='APPROVED — READY FOR MANUAL PUBLISHING';draft.approvedAt=new Date().toISOString();return draft; }); }
  addCampaign(input) { return this.mutate(state => { const campaign={ id:crypto.randomUUID(),name:input.name,variant:input.variant||'A',message:input.message,status:'DRAFT',createdAt:new Date().toISOString() };state.campaigns.push(campaign);return campaign; }); }
  recordEmailDelivery(delivery) { return this.mutate(state => { state.emailDeliveries.push({...delivery,id:crypto.randomUUID(),createdAt:new Date().toISOString()}); return true; }); }
  async waitlistAnalytics() { const state=await this.snapshot(); const base=publicAnalytics(state); const countBy=(items,key)=>Object.entries(items.reduce((all,x)=>((all[x[key]||'unknown']=(all[x[key]||'unknown']||0)+1),all),{})).map(([label,count])=>({label,count})).sort((a,b)=>b.count-a.count); return {...base,newSignups:state.waitlist.filter(x=>Date.now()-new Date(x.createdAt)<7*86400000).length,unsubscribeCount:state.waitlist.filter(x=>!x.active).length,byDevice:countBy(state.waitlist,'device'),emailDeliveries:countBy(state.emailDeliveries,'status')}; }
}

export function publicAnalytics(state) {
  const visitors=state.events.filter(x=>x.type==='landing_view').length;
  const signups=state.waitlist.length;
  const countBy=(items,key)=>Object.entries(items.reduce((all,x)=>((all[x[key]||'unknown']=(all[x[key]||'unknown']||0)+1),all),{})).map(([label,count])=>({label,count})).sort((a,b)=>b.count-a.count);
  return { visitors,signups,conversionRate:visitors?Number((signups/visitors*100).toFixed(1)):0,activeSubscribers:state.waitlist.filter(x=>x.active).length,bySource:countBy(state.waitlist,'source'),byCampaign:countBy(state.waitlist,'campaign') };
}
