import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createHandler, validState } from './mercadolivre-oauth.mjs';

test('manual authorization never exchanges or exposes the code in the response', async () => {
  let session = { state: 'manual-state', manual: true, expires: Date.now() + 60000 };
  let exchanges = 0;
  const server = http.createServer(createHandler({ readSession: () => session,
    consumeSession: () => { session = null; }, exchange: async () => { exchanges++; } }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/?code=secret-code&state=manual-state`);
    assert.equal(response.status, 200);
    assert.ok(!(await response.text()).includes('secret-code'));
    assert.equal(exchanges, 0);
    assert.equal(session, null);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('expired, missing and mismatched states are rejected', () => {
  assert.equal(validState(null, 'a'), false);
  assert.equal(validState({ state: 'a', expires: 0 }, 'a'), false);
  assert.equal(validState({ state: 'a', expires: Date.now() + 10000 }, 'b'), false);
});
test('callback isolates API, validates state and prevents code replay', async () => {
  let session = { state: 'expected', expires: Date.now() + 60000 };
  let exchanges = 0;
  const server = http.createServer(createHandler({ readSession: () => session,
    consumeSession: () => { session = null; }, exchange: async () => { exchanges++; } }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(base + '/api/products')).status, 404);
    assert.equal((await fetch(base + '/?code=secret&state=wrong')).status, 400);
    assert.equal(exchanges, 0);
    const response = await fetch(base + '/?code=secret&state=expected');
    assert.equal(response.status, 200);
    assert.ok(!(await response.text()).includes('secret'));
    assert.equal(exchanges, 1);
    assert.equal((await fetch(base + '/?code=secret&state=expected')).status, 400);
    assert.equal(exchanges, 1);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
