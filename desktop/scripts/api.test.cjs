const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

async function loadModule(relativePath) {
    const source = fs.readFileSync(path.join(__dirname, relativePath), 'utf8');
    return import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}#${Math.random()}`);
}

async function client(t, fetchStub) {
    t.mock.method(console, 'error', () => {});
    const previous = { window: global.window, fetch: global.fetch };
    global.window = { api: { getBackendInfo: async () => ({ apiBaseUrl: 'http://127.0.0.1:18080/api' }) } };
    global.fetch = fetchStub;
    t.after(() => { global.window = previous.window; global.fetch = previous.fetch; });
    return (await loadModule('../src/renderer/src/api.js')).associago;
}

const jsonResponse = (value) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } });

test('authenticated API calls obtain fresh tokens and omit session cookies', async (t) => {
    const calls = [];
    const api = await client(t, async (url, config) => { calls.push({ url, config }); return jsonResponse([]); });
    let sequence = 0;
    api.setTokenProvider(async () => `fresh-${++sequence}`);
    await api.users.getAll();
    await api.users.getAll();
    assert.equal(calls[0].config.headers.Authorization, 'Bearer fresh-1');
    assert.equal(calls[1].config.headers.Authorization, 'Bearer fresh-2');
    assert.equal(calls[0].config.credentials, 'omit');
});

test('a lost response never causes an automatic duplicate mutation', async (t) => {
    let attempts = 0;
    const api = await client(t, async () => { attempts++; throw new TypeError('connection lost'); });
    await assert.rejects(api.users.create({ firstName: 'Test' }), /connection lost/);
    assert.equal(attempts, 1);
});

test('backend conflicts retain actionable messages and HTTP status', async (t) => {
    const api = await client(t, async () => new Response(JSON.stringify({ error: 'Database remoto: usa il servizio database.' }), { status: 409, headers: { 'Content-Type': 'application/json' } }));
    await assert.rejects(api.createBackup(), (error) => error.status === 409 && /Database remoto/.test(error.message));
});

test('logout supports plain text responses and reaches the backend', async (t) => {
    let request;
    const api = await client(t, async (url, config) => { request = { url, config }; return new Response('Logged out', { headers: { 'Content-Type': 'text/plain' } }); });
    assert.equal(await api.logout(), 'Logged out');
    assert.equal(request.config.method, 'POST');
    assert.ok(request.url.endsWith('/auth/logout'));
});

test('member edits use PUT and preserve year filters for the list', async (t) => {
    const requests = [];
    const api = await client(t, async (url, config) => { requests.push({ url, config }); return jsonResponse({}); });
    await api.memberships.update(12, { role: 'MEMBER' });
    await api.memberships.getByAssociation(3, 2026);
    assert.equal(requests[0].config.method, 'PUT');
    assert.ok(requests[0].url.endsWith('/v1/memberships/12'));
    assert.ok(requests[1].url.endsWith('/v1/memberships/association/3?anno=2026'));
});

test('an explicit empty organization role cannot restore stale president metadata', async () => {
    const { clerkApplicationRole } = await loadModule('../src/renderer/src/roles.js');
    const staleUser = { publicMetadata: { role: 'presidente' } };
    assert.equal(clerkApplicationRole(staleUser, { o: { rol: '' } }), 'socio');
    assert.equal(clerkApplicationRole(staleUser, { o: { rol: {} } }), 'socio');
    assert.equal(clerkApplicationRole(staleUser, { o: { rol: 'member' } }), 'socio');
    assert.equal(clerkApplicationRole(staleUser, { o: { rol: 'admin' } }), 'presidente');
});

test('an unlinked Clerk identifier cannot be sent as a numeric approval actor', async (t) => {
    let requests = 0;
    const api = await client(t, async () => { requests++; return jsonResponse({}); });
    assert.throws(() => api.budgets.approve(1, 'user_clerk_identifier'), /Collega questo accesso/);
    assert.throws(() => api.communications.send(1, null), /Collega questo accesso/);
    assert.equal(requests, 0);
});

test('text attachments download as authenticated blobs', async (t) => {
    let config;
    const api = await client(t, async (_url, options) => { config = options; return new Response('xml attachment', { headers: { 'Content-Type': 'text/xml' } }); });
    api.setTokenProvider(async () => 'fresh-download-token');
    const blob = await api.attachments.download('EXPENSE_DOCUMENT', 1, 'RECEIPT');
    assert.ok(blob instanceof Blob);
    assert.equal(await blob.text(), 'xml attachment');
    assert.equal(config.headers.Authorization, 'Bearer fresh-download-token');
});
