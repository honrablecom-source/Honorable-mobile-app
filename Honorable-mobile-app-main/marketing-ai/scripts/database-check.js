import { Pool } from 'pg';

const timeoutMs=8000;
const localHosts=new Set(['localhost','127.0.0.1','::1']);
function failureReason(error) {
  const code=String(error?.code||'').toUpperCase();
  const message=String(error?.message||'').toLowerCase();
  if (['ENOTFOUND','EAI_AGAIN'].includes(code)) return 'DNS_FAILURE';
  if (code==='28P01'||message.includes('password authentication failed')) return 'AUTHENTICATION_FAILED';
  if (code==='3D000'||message.includes('database')&&message.includes('does not exist')) return 'DATABASE_NOT_FOUND';
  if (code==='ECONNREFUSED') return 'CONNECTION_REFUSED';
  if (['ETIMEDOUT','ESOCKETTIMEDOUT'].includes(code)||message.includes('timeout')) return 'TIMEOUT';
  if (message.includes('ssl')||message.includes('certificate')||message.includes('no pg_hba.conf entry')) return 'SSL_FAILURE';
  return 'UNKNOWN';
}

export async function databaseStatus(env=process.env) {
  if (!env.DATABASE_URL) return {status:'MISSING',reason:'MISSING',ssl:'NOT CHECKED'};
  let url;
  try {
    url=new URL(env.DATABASE_URL);
    if (!['postgres:','postgresql:'].includes(url.protocol)||!url.hostname) throw new Error('invalid');
  } catch { return {status:'FAILED',reason:'INVALID_URL',ssl:'NOT CHECKED'}; }
  const ssl=localHosts.has(url.hostname)?false:{rejectUnauthorized:false};
  const pool=new Pool({connectionString:env.DATABASE_URL,ssl,max:1,connectionTimeoutMillis:timeoutMs,query_timeout:timeoutMs});
  try { await pool.query('SELECT 1'); return {status:'CONNECTED',reason:null,ssl:ssl?'ENABLED':'DISABLED FOR LOCALHOST'}; }
  catch(error) { return {status:'FAILED',reason:failureReason(error),ssl:ssl?'ENABLED':'DISABLED FOR LOCALHOST'}; }
  finally { await pool.end().catch(()=>{}); }
}
