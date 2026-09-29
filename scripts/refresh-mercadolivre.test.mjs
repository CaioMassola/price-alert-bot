import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { refresh } from './refresh-mercadolivre.mjs';

function fixture(t, expires = '2000-01-01T00:00:00Z') {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ml-refresh-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const filename = path.join(dir, '.env');
  fs.writeFileSync(filename, `MIN_STORE_DISCOUNT=10\nMERCADO_LIVRE_CLIENT_ID=test\nMERCADO_LIVRE_CLIENT_SECRET=test-secret\nMERCADO_LIVRE_ACCESS_TOKEN=old\nMERCADO_LIVRE_REFRESH_TOKEN=old-refresh\nMERCADO_LIVRE_TOKEN_EXPIRES_AT=${expires}\n`);
  return filename;
}
const response = () => ({ ok: true, json: async () => ({ access_token: 'new-access', refresh_token: 'new-refresh', expires_in: 21600 }) });
test('rotates both tokens before reloading and preserves user settings', async t => {
  const filename = fixture(t); let reloads = 0;
  assert.equal(await refresh({ filename, request: async (url, options) => {
    assert.equal(url, 'https://api.mercadolibre.com/oauth/token');
    assert.equal(options.body.get('grant_type'), 'refresh_token');
    assert.equal(options.body.get('refresh_token'), 'old-refresh');
    assert.equal(options.redirect, 'error'); return response();
  }, reload: () => { reloads++; assert.match(fs.readFileSync(filename, 'utf8'), /MERCADO_LIVRE_REFRESH_TOKEN=new-refresh/); } }), true);
  assert.equal(reloads, 1); assert.match(fs.readFileSync(filename, 'utf8'), /MIN_STORE_DISCOUNT=10/);
});
test('valid tokens do not call OAuth or restart the bot', async t => {
  const filename = fixture(t, '2099-01-01T00:00:00Z');
  assert.equal(await refresh({ filename, request: () => assert.fail(), reload: () => assert.fail() }), false);
});
test('failed reload retries using saved credentials without refreshing again', async t => {
  const filename = fixture(t);
  await assert.rejects(refresh({ filename, request: response, reload: () => { throw new Error('Docker offline'); } }));
  let reloaded = false;
  await refresh({ filename, request: () => assert.fail(), reload: () => { reloaded = true; } });
  assert.equal(reloaded, true); assert.equal(fs.existsSync(filename + '.reload-pending'), false);
});
test('rejected or malformed tokens never replace credentials', async t => {
  const filename = fixture(t); const before = fs.readFileSync(filename, 'utf8');
  for (const request of [() => ({ ok: false }), () => ({ ok: true, json: async () => ({ access_token: 'partial' }) })]) {
    await assert.rejects(refresh({ filename, request, reload: () => assert.fail() }));
    assert.equal(fs.readFileSync(filename, 'utf8'), before);
  }
});
