import { Pool } from 'pg';
import crypto from 'node:crypto';

export class WaitlistRepository {
  addWaitlist() { throw new Error('Not implemented'); }
  unsubscribe() { throw new Error('Not implemented'); }
  analytics() { throw new Error('Not implemented'); }
  recordEmailDelivery() { throw new Error('Not implemented'); }
}

export class LocalJsonWaitlistRepository extends WaitlistRepository {
  constructor(store) { super(); this.store=store; }
  addWaitlist(input) { return this.store.addWaitlist(input); }
  unsubscribe(token) { return this.store.unsubscribe(token); }
  recordEmailDelivery(delivery) { return this.store.recordEmailDelivery(delivery); }
  async analytics() { return this.store.waitlistAnalytics(); }
}

export class ProductionWaitlistRepository extends WaitlistRepository {
  constructor(connectionString, pool) {
    super();
    if (!connectionString && !pool) throw new Error('DATABASE_URL is required when WAITLIST_STORE=postgres');
    this.pool=pool || new Pool({connectionString,ssl:connectionString?.includes('localhost')?false:{rejectUnauthorized:false},max:5});
    this.ready=this.initialize();
  }
  async initialize() {
    await this.pool.query(`CREATE TABLE IF NOT EXISTS waitlist_members (
      id uuid PRIMARY KEY, email text NOT NULL UNIQUE, device text NOT NULL CHECK (device IN ('Android','iPhone','Both')),
      consent_at timestamptz NOT NULL, signup_at timestamptz NOT NULL DEFAULT now(), source text NOT NULL,
      campaign text NOT NULL, content text NOT NULL, referral_code text NOT NULL UNIQUE,
      unsubscribe_token_hash text NOT NULL UNIQUE, unsubscribed_at timestamptz
    )`);
    await this.pool.query(`CREATE TABLE IF NOT EXISTS email_deliveries (
      id uuid PRIMARY KEY, member_id uuid REFERENCES waitlist_members(id) ON DELETE CASCADE, kind text NOT NULL,
      status text NOT NULL, provider_id text, error_code text, created_at timestamptz NOT NULL DEFAULT now()
    )`);
  }
  hash(token) { return crypto.createHash('sha256').update(token).digest('hex'); }
  async addWaitlist({email,device='Both',consent,source='direct',campaign='organic',content='unknown'}) {
    await this.ready; const normalized=email.trim().toLowerCase(); const token=crypto.randomBytes(24).toString('hex');
    const values=[crypto.randomUUID(),normalized,device,new Date(),source,campaign,content,crypto.randomBytes(6).toString('hex'),this.hash(token)];
    const inserted=await this.pool.query(`INSERT INTO waitlist_members
      (id,email,device,consent_at,source,campaign,content,referral_code,unsubscribe_token_hash)
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (email) DO NOTHING
      RETURNING id,email,device,consent_at AS "consentAt",signup_at AS "createdAt",source,campaign,content,referral_code AS "referralCode",unsubscribed_at AS "unsubscribedAt"`,values);
    if (inserted.rows[0]) return {member:{...inserted.rows[0],active:true,unsubscribeToken:token},duplicate:false};
    const existing=await this.pool.query(`SELECT id,email,device,consent_at AS "consentAt",signup_at AS "createdAt",source,campaign,content,referral_code AS "referralCode",unsubscribed_at AS "unsubscribedAt" FROM waitlist_members WHERE email=$1`,[normalized]);
    return {member:{...existing.rows[0],active:!existing.rows[0].unsubscribedAt},duplicate:true};
  }
  async unsubscribe(token) { await this.ready; const result=await this.pool.query('UPDATE waitlist_members SET unsubscribed_at=COALESCE(unsubscribed_at,now()) WHERE unsubscribe_token_hash=$1',[this.hash(token)]); return result.rowCount>0; }
  async recordEmailDelivery({memberId,kind,status,providerId,errorCode}) { await this.ready; await this.pool.query('INSERT INTO email_deliveries (id,member_id,kind,status,provider_id,error_code) VALUES ($1,$2,$3,$4,$5,$6)',[crypto.randomUUID(),memberId,kind,status,providerId||null,errorCode||null]); }
  async analytics() { await this.ready; const [summary,sources,campaigns,devices,email]=await Promise.all([
    this.pool.query(`SELECT count(*)::int signups,count(*) FILTER (WHERE signup_at>=now()-interval '7 days')::int "newSignups",count(*) FILTER (WHERE unsubscribed_at IS NULL)::int "activeSubscribers",count(*) FILTER (WHERE unsubscribed_at IS NOT NULL)::int "unsubscribeCount" FROM waitlist_members`),
    this.pool.query('SELECT source label,count(*)::int count FROM waitlist_members GROUP BY source ORDER BY count DESC'),
    this.pool.query('SELECT campaign label,count(*)::int count FROM waitlist_members GROUP BY campaign ORDER BY count DESC'),
    this.pool.query('SELECT device label,count(*)::int count FROM waitlist_members GROUP BY device ORDER BY count DESC'),
    this.pool.query("SELECT status label,count(*)::int count FROM email_deliveries WHERE kind='welcome' GROUP BY status ORDER BY count DESC")]);
    return {...summary.rows[0],bySource:sources.rows,byCampaign:campaigns.rows,byDevice:devices.rows,emailDeliveries:email.rows};
  }
}

export function createWaitlistRepository(settings, store) {
  return settings.waitlistStore==='postgres' ? new ProductionWaitlistRepository(settings.databaseUrl) : new LocalJsonWaitlistRepository(store);
}
