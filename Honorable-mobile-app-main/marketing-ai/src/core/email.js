export class EmailService {
  status() { return {development:'DISCONNECTED',production:'DOMAIN REQUIRED'}; }
  async sendWelcome() { throw new Error('Not implemented'); }
}

export class TestEmailService extends EmailService {
  constructor() { super(); this.deliveries=[]; }
  status() { return {development:'TEST MODE — NO EMAIL SENT',production:'DOMAIN REQUIRED'}; }
  async sendWelcome(message) { this.deliveries.push(message); return {status:'simulated',providerId:null}; }
}

export class DisabledEmailService extends EmailService {
  constructor({developmentConnected=false}={}) { super(); this.developmentConnected=developmentConnected; }
  status() { return {development:this.developmentConnected?'CONNECTED':'CONFIGURATION REQUIRED',production:'DOMAIN REQUIRED'}; }
  async sendWelcome() { return {status:'skipped',providerId:null}; }
}

export class ResendEmailService extends EmailService {
  constructor({apiKey,from,developmentConnected=false,fetchImpl=fetch}) { super(); this.apiKey=apiKey; this.from=from; this.developmentConnected=developmentConnected; this.fetch=fetchImpl; }
  status() { return {development:this.developmentConnected?'CONNECTED':'CONFIGURATION REQUIRED',production:this.apiKey&&this.from?'CONNECTED':'DOMAIN REQUIRED'}; }
  async sendWelcome({to,unsubscribeUrl}) {
    if (!this.apiKey||!this.from) throw new Error('Resend is not configured');
    const response=await this.fetch('https://api.resend.com/emails',{method:'POST',headers:{authorization:`Bearer ${this.apiKey}`,'content-type':'application/json'},body:JSON.stringify({
      from:this.from,to:[to],subject:"You're on the Honorable waitlist",
      text:`You’re in. You remember the moment. Honorable finds it.\n\nWe’ll email you when private early access is ready. Your memories stay yours—Honorable is built with privacy at its core.\n\nUnsubscribe: ${unsubscribeUrl}`,
      html:`<p>You’re in.</p><p><strong>You remember the moment. Honorable finds it.</strong></p><p>We’ll email you when private early access is ready. Your memories stay yours—Honorable is built with privacy at its core.</p><p><a href="${unsubscribeUrl}">Unsubscribe</a></p>`
    })});
    const result=await response.json().catch(()=>({}));
    if (!response.ok) { const error=new Error('Email provider rejected the request'); error.code=String(result.name||response.status); throw error; }
    return {status:'success',providerId:result.id};
  }
}

export class ResendDevelopmentEmailService {
  constructor({apiKey,from='Honorable Development <onboarding@resend.dev>',recipient,fetchImpl=fetch}) { this.apiKey=apiKey;this.from=from;this.recipient=recipient?.trim().toLowerCase();this.fetch=fetchImpl; }
  status() { return this.apiKey&&this.recipient?'CONNECTED':'CONFIGURATION REQUIRED'; }
  async sendTest({to=this.recipient}={}) {
    if(!this.apiKey||!this.recipient)throw new Error('Resend development email is not configured');
    if(to?.trim().toLowerCase()!==this.recipient){const error=new Error('Development email recipient is not allowed');error.code='recipient_not_allowed';throw error;}
    const response=await this.fetch('https://api.resend.com/emails',{method:'POST',headers:{authorization:`Bearer ${this.apiKey}`,'content-type':'application/json'},body:JSON.stringify({from:this.from,to:[this.recipient],subject:'Honorable development email test',text:'Honorable Resend development mode is connected.'})});
    const result=await response.json().catch(()=>({}));
    if(!response.ok){const error=new Error('Email provider rejected the request');error.code=String(result.name||response.status);throw error;}
    return {status:'success',providerId:result.id};
  }
}

export function createEmailService(settings, dependencies={}) {
  if (dependencies.emailService) return dependencies.emailService;
  const developmentConnected=Boolean(settings.emailApiKey&&settings.emailDevelopmentRecipient);
  if(settings.emailProvider==='resend')return new ResendEmailService({apiKey:settings.emailApiKey,from:settings.emailFrom,developmentConnected});
  if(settings.emailProvider==='test')return new TestEmailService();
  return new DisabledEmailService({developmentConnected});
}
