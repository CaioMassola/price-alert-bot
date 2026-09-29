import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { saveEnv } from './mercadolivre-oauth.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export async function refresh({ filename, request = fetch, reload, now = Date.now() }) {
  const config = Object.fromEntries(fs.readFileSync(filename, 'utf8').split(/\r?\n/)
    .filter(line => /^[A-Z_]+=/.test(line)).map(line => {
      const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1).trim().replace(/^"(.*)"$/, '$1')];
    }));
  const pending = filename + '.reload-pending';
  const expiry = Date.parse(config.MERCADO_LIVRE_TOKEN_EXPIRES_AT);
  if (config.MERCADO_LIVRE_ACCESS_TOKEN && expiry > now + 10 * 60_000) {
    if (fs.existsSync(pending)) { await reload(); fs.unlinkSync(pending); }
    return false;
  }
  for (const key of ['CLIENT_ID', 'CLIENT_SECRET', 'REFRESH_TOKEN'])
    if (!config['MERCADO_LIVRE_' + key]) throw new Error('OAuth credentials missing');
  const response = await request('https://api.mercadolibre.com/oauth/token', {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20_000),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body: new URLSearchParams({ grant_type: 'refresh_token', client_id: config.MERCADO_LIVRE_CLIENT_ID,
      client_secret: config.MERCADO_LIVRE_CLIENT_SECRET, refresh_token: config.MERCADO_LIVRE_REFRESH_TOKEN })
  });
  if (!response.ok) throw new Error('OAuth refresh rejected');
  const token = await response.json();
  if (typeof token.access_token !== 'string' || !token.access_token || typeof token.refresh_token !== 'string' ||
      !token.refresh_token || !Number.isFinite(token.expires_in) || token.expires_in <= 0)
    throw new Error('Invalid OAuth response');
  // Persist the restart marker first: a failed Docker reload must not lose rotated credentials.
  fs.writeFileSync(pending, 'pending', { mode: 0o600 });
  saveEnv({ MERCADO_LIVRE_ACCESS_TOKEN: token.access_token, MERCADO_LIVRE_REFRESH_TOKEN: token.refresh_token,
    MERCADO_LIVRE_TOKEN_EXPIRES_AT: new Date(now + token.expires_in * 1000).toISOString() }, filename);
  await reload();
  fs.unlinkSync(pending);
  return true;
}

async function main() {
  fs.mkdirSync(path.join(root, '.runtime'), { recursive: true });
  const lockPath = path.join(root, '.runtime', 'ml-refresh.lock');
  let lock;
  try { lock = fs.openSync(lockPath, 'wx'); }
  catch { console.error('Renovacao ja em andamento ou lock pendente de revisao.'); process.exitCode = 1; return; }
  try {
    const renewed = await refresh({ filename: path.join(root, '.env'), reload: () => {
      const docker = process.platform === 'win32' ? 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe' : 'docker';
      const result = spawnSync(docker, ['compose', 'up', '-d', '--no-deps', '--force-recreate', 'price-alert-api'],
        { cwd: root, stdio: 'ignore', windowsHide: true, timeout: 120_000 });
      if (result.error || result.status !== 0) throw new Error('Docker reload failed');
    }});
    console.log(renewed ? 'Token renovado e bot recarregado.' : 'Token vigente; verificacao concluida.');
  } catch {
    console.error('Renovacao nao concluida. Verifique autorizacao OAuth e Docker; credenciais nao exibidas.');
    process.exitCode = 1;
  } finally { fs.closeSync(lock); fs.unlinkSync(lockPath); }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
