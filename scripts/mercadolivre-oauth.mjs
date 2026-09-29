// Auxiliary local onboarding tool; the monitoring application remains Java.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, timingSafeEqual } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const envPath = path.join(root, '.env');
const sessionPath = path.join(root, '.runtime', 'ml-oauth-session.json');
function env() {
  return Object.fromEntries(fs.readFileSync(envPath, 'utf8').split(/\r?\n/)
    .filter(line => /^[A-Z_]+=/.test(line)).map(line => {
      const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1).trim().replace(/^"(.*)"$/, '$1')];
    }));
}
export function saveEnv(values, filename = envPath) {
  let content = fs.readFileSync(filename, 'utf8');
  for (const [key, value] of Object.entries(values)) {
    if (!/^[A-Z_]+$/.test(key) || /[\r\n]/.test(String(value))) throw new Error('Invalid configuration');
    const line = `${key}=${value}`;
    const pattern = new RegExp(`^${key}=.*$`, 'm');
    content = pattern.test(content) ? content.replace(pattern, () => line) : content.trimEnd() + '\n' + line + '\n';
  }
  const temporary = filename + '.oauth-' + randomBytes(8).toString('hex');
  try {
    fs.writeFileSync(temporary, content, { mode: 0o600 });
    fs.renameSync(temporary, filename);
  } finally { if (fs.existsSync(temporary)) fs.unlinkSync(temporary); }
}
export function validState(session, state) {
  if (!session || !state || session.expires < Date.now()) return false;
  const a = Buffer.from(session.state); const b = Buffer.from(state);
  return a.length === b.length && timingSafeEqual(a, b);
}
export function createHandler({ readSession, consumeSession, exchange }) {
  return async (req, res) => {
    const reply = (status, message) => {
      res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store',
        'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
      res.end(message);
    };
    try {
      const url = new URL(req.url, 'http://localhost');
      if (req.method !== 'GET' || url.pathname !== '/') return reply(404, 'Não encontrado.');
      if (!url.search) return reply(200, 'Milizé — retorno de autorização do Mercado Livre pronto.');
      const session = readSession();
      if (!validState(session, url.searchParams.get('state'))) {
        console.log('OAuth: ' + (!session ? 'session_missing' : session.expires < Date.now() ? 'session_expired' : 'state_mismatch'));
        return reply(400, 'Autorização inválida ou expirada. Inicie novamente pelo seu PC.');
      }
      consumeSession(); // One use, including rejected grants and failed exchanges.
      if (url.searchParams.has('error')) {
        console.log('OAuth: authorization_denied');
        return reply(400, 'Autorização não concedida.');
      }
      const code = url.searchParams.get('code');
      if (!code || code.length > 4096) return reply(400, 'Código de autorização ausente ou inválido.');
      if (session.manual === true) {
        return reply(200, 'Modo manual: código recebido e NÃO utilizado pelo bot. Copie o valor de code na barra de endereço (até o próximo &), use no Insomnia e não compartilhe a URL. Use a mesma redirect_uri desta autorização.');
      }
      await exchange(code, session);
      reply(200, 'Autorização recebida! As credenciais foram salvas no seu PC. Pode fechar esta aba. Falta validar o acesso aos produtos e recarregar o bot.');
    } catch { console.log('OAuth: callback_failed'); reply(502, 'Não foi possível concluir a autorização. Inicie novamente pelo seu PC.'); }
  };
}
async function main() {
  const config = env();
  if (process.argv[2] === 'authorize') {
    for (const key of ['MERCADO_LIVRE_CLIENT_ID', 'MERCADO_LIVRE_CLIENT_SECRET', 'MERCADO_LIVRE_REDIRECT_URI']) {
      if (!config[key]) throw new Error(`Configure ${key} no .env`);
    }
    const redirect = new URL(config.MERCADO_LIVRE_REDIRECT_URI);
    if (redirect.protocol !== 'https:' || redirect.pathname !== '/' || redirect.search || redirect.hash) throw new Error('Use a raiz HTTPS do túnel como redirect.');
    const state = randomBytes(32).toString('hex');
    fs.mkdirSync(path.dirname(sessionPath), { recursive: true });
    fs.writeFileSync(sessionPath, JSON.stringify({ state, manual: process.argv.includes('--manual'), expires: Date.now() + 10 * 60_000,
      redirect: config.MERCADO_LIVRE_REDIRECT_URI, clientId: config.MERCADO_LIVRE_CLIENT_ID }), { mode: 0o600 });
    const url = new URL('https://auth.mercadolivre.com.br/authorization');
    url.search = new URLSearchParams({ response_type: 'code', client_id: config.MERCADO_LIVRE_CLIENT_ID,
      redirect_uri: config.MERCADO_LIVRE_REDIRECT_URI, state }).toString();
    console.log(url.href);
    return;
  }
  const handler = createHandler({
    readSession: () => fs.existsSync(sessionPath) ? JSON.parse(fs.readFileSync(sessionPath, 'utf8')) : null,
    consumeSession: () => fs.unlinkSync(sessionPath),
    exchange: async (code, session) => {
      const current = env();
      if (current.MERCADO_LIVRE_CLIENT_ID !== session.clientId || current.MERCADO_LIVRE_REDIRECT_URI !== session.redirect) throw new Error('Configuration changed');
      const response = await fetch('https://api.mercadolibre.com/oauth/token', {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20_000),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
        body: new URLSearchParams({ grant_type: 'authorization_code', client_id: session.clientId,
          client_secret: current.MERCADO_LIVRE_CLIENT_SECRET, redirect_uri: session.redirect, code })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        const known = ['invalid_grant', 'invalid_client', 'invalid_request', 'unauthorized_client', 'unsupported_grant_type'];
        console.log('OAuth token HTTP ' + response.status + ': ' + (known.includes(body.error) ? body.error : 'unclassified'));
        throw new Error('Token exchange failed');
      }
      const token = await response.json();
      if (typeof token.access_token !== 'string' || !token.access_token || typeof token.refresh_token !== 'string' || !token.refresh_token || !(token.expires_in > 0)) throw new Error('Invalid token response');
      saveEnv({ MERCADO_LIVRE_ACCESS_TOKEN: token.access_token, MERCADO_LIVRE_REFRESH_TOKEN: token.refresh_token,
        MERCADO_LIVRE_TOKEN_EXPIRES_AT: new Date(Date.now() + token.expires_in * 1000).toISOString() });
      console.log('Credenciais salvas. Valide o acesso e recrie o serviço do bot.');
    }
  });
  const server = http.createServer({ maxHeaderSize: 8192 }, handler);
  server.requestTimeout = 30_000;
  server.listen(8765, '127.0.0.1', () => console.log('Callback pronto em 127.0.0.1:8765'));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => { console.error('Falha ao iniciar OAuth. Confira as credenciais e a URI no .env.'); process.exitCode = 1; });
}
