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

test('project assignment and transaction attachment use scoped existing records', async (t) => {
    const requests = [];
    const api = await client(t, async (url, config) => { requests.push({ url, config }); return jsonResponse({}); });
    await api.projects.assign(9, { personType: 'VOLUNTEER', personId: 21 });
    await api.projects.availableTransactions(9);
    await api.projects.attachTransaction(9, 35);
    assert.ok(requests[0].url.endsWith('/v1/projects/9/assignments'));
    assert.deepEqual(JSON.parse(requests[0].config.body), { personType: 'VOLUNTEER', personId: 21 });
    assert.ok(requests[1].url.endsWith('/v1/projects/9/transactions/available'));
    assert.ok(requests[2].url.endsWith('/v1/projects/9/transactions/35'));
    assert.equal(requests[2].config.method, 'POST');
    assert.equal(requests[2].config.body, undefined);
});

test('suite sharing sends local identity without browser-supplied owner or secrets', async (t) => {
    const requests = [];
    const api = await client(t, async (url, config) => { requests.push({ url, config }); return jsonResponse({}); });
    await api.suiteProjects.share(9);
    await api.suiteProjects.link('73013b9d-7288-4e34-a841-5db4dc15ba60', 9);
    assert.ok(requests[0].url.endsWith('/integrations/v1/projects/share'));
    assert.deepEqual(JSON.parse(requests[0].config.body), { localId: '9' });
    assert.ok(requests[1].url.endsWith('/integrations/v1/projects/73013b9d-7288-4e34-a841-5db4dc15ba60/link'));
    assert.equal(Object.hasOwn(requests[0].config.headers, 'X-Suite-Key'), false);
});

test('a requested shared project resolves only by explicit app correspondence', async () => {
    const { localProjectForContext, safeProjectLink, SUITE_UUID, mayViewProjectFinance } = await loadModule('../src/renderer/src/project-context.js');
    const local = [{ id: 7, name: 'Stesso titolo' }, { id: 9, name: 'Progetto locale' }];
    assert.equal(localProjectForContext({ instances: [{ system: 'associago', entity: 'project', localId: '9' }] }, local), local[1]);
    assert.equal(localProjectForContext({ title: 'Stesso titolo', instances: [] }, local), null);
    assert.equal(localProjectForContext({ instances: [{ system: 'associago', entity: 'project', localId: '404' }] }, local), null);
    assert.equal(SUITE_UUID.test('unknown'), false);
    assert.equal(safeProjectLink('javascript:alert(1)'), null);
    assert.equal(safeProjectLink('https://user:password@example.com'), null);
    assert.equal(mayViewProjectFinance('coordinatore-servizi'), false);
    assert.equal(mayViewProjectFinance('presidente'), true);
    assert.equal(mayViewProjectFinance('ADMIN', true), true);
});

test('web association selection is explicit when more than one association exists', async () => {
    const { initialWebAssociation } = await loadModule('../src/renderer/src/association-choice.js');
    const first = { id: 1, name: 'Associazione uno' };
    const second = { id: 2, name: 'Associazione due' };
    assert.equal(initialWebAssociation([first]), first);
    assert.equal(initialWebAssociation([first, second]), null);
    assert.equal(initialWebAssociation([first, second], '2'), second);
    assert.throws(() => initialWebAssociation([first, second], '404'), /Nessun’altra associazione/);
    assert.throws(() => initialWebAssociation([first], 'bad'), /non valido/);
    assert.throws(() => initialWebAssociation([], ''), /Nessuna associazione/);
});
