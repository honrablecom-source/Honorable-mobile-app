// Uses the existing environment contract. Reports checks, never configuration values.
const GOOGLE_CLIENT = /^\d+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/;

function inspectConfiguration(env = {}) {
  const client = env.HONORABLE_GOOGLE_WEB_CLIENT_ID || '';
  const api = env.HONORABLE_ACCOUNT_API_URL || '';
  let endpoint;
  try {
    const url = new URL(api);
    if (url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash) endpoint = url;
  } catch {}
  return {
    service: 'honorable-account', scope: 'DEPLOYMENT_CONFIGURATION',
    checks: {
      googleClientConfigured: !!client,
      googleClientValid: GOOGLE_CLIENT.test(client),
      accountApiConfigured: !!api,
      accountApiHttps: !!endpoint,
    },
  };
}

async function checkConfiguration(env = {}, {probe = false, fetchImpl = global.fetch, timeoutMs = 5000} = {}) {
  const report = inspectConfiguration(env);
  if (probe && Object.values(report.checks).every(Boolean)) {
    const base = env.HONORABLE_ACCOUNT_API_URL.replace(/\/$/, '') + '/';
    for (const route of ['healthz', 'readyz']) {
      let passed = false;
      try {
        const response = await fetchImpl(new URL(route, base), {redirect: 'error', signal: AbortSignal.timeout(timeoutMs)});
        const body = await response.json();
        passed = response.status === 200 && body?.service === 'honorable-account' &&
          (route === 'healthz' ? body.status === 'UP' : body.scope === 'ACCOUNT_API_CONFIGURATION' && body.ready === true);
      } catch { /* A fixed check result prevents URLs or transport details leaking to logs. */ }
      report.checks[route === 'healthz' ? 'accountApiReachable' : 'accountApiProvidersConfigured'] = passed;
    }
  } else if (probe) {
    report.checks.accountApiReachable = false;
    report.checks.accountApiProvidersConfigured = false;
  }
  report.probed = probe;
  report.ready = Object.values(report.checks).every(Boolean);
  report.deviceVerified = false;
  report.storeReady = false;
  return report;
}

if (require.main === module) {
  const args = process.argv.slice(2);
  if (args.some(arg => arg !== '--probe')) {
    console.error('Usage: node scripts/check-account-config.cjs [--probe]');
    process.exitCode = 2;
  } else checkConfiguration(process.env, {probe: args.includes('--probe')}).then(report => {
    console.log(JSON.stringify(report, null, 2));
    process.exitCode = report.ready ? 0 : 2;
  }).catch(() => { console.error('ACCOUNT_CONFIGURATION_CHECK_FAILED'); process.exitCode = 2; });
}
module.exports = {inspectConfiguration, checkConfiguration};
