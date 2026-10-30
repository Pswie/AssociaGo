import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../src/renderer/src/clerk-sso.js', import.meta.url), 'utf8');
const { clerkReturnUrl, clerkSignInReturnOptions, clerkSatelliteOptions } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const location = { origin: 'https://gestionale.angelidelfuturoaps.com', host: 'gestionale.angelidelfuturoaps.com', pathname: '/suite/projects', search: '?suiteProject=91c8d8f2-a795-4166-82da-42d4c1e6283b&view=timeline', hash: '#details' };

test('absolute callback retains project UUID, route and fragment on its own origin', () => {
  assert.equal(clerkReturnUrl(location), location.origin + location.pathname + location.search + location.hash);
  const options = clerkSignInReturnOptions(location);
  assert.equal(new Set(Object.values(options)).size, 1);
  assert.equal(options.redirectUrl, clerkReturnUrl(location));
});
test('redirect input cannot replace callback origin or survive as an SDK redirect override', () => {
  const result = new URL(clerkReturnUrl({ ...location, pathname: '//attacker.example/route', search: '?suiteProject=91c8d8f2-a795-4166-82da-42d4c1e6283b&redirect_url=https://attacker.example&sign_in_force_redirect_url=https://attacker.example&after_sign_up_url=https://attacker.example&__clerk_synced=false&__clerk_db_jwt=synthetic-fixture' }));
  assert.equal(result.origin, location.origin);
  assert.equal(result.pathname, '//attacker.example/route');
  assert.deepEqual([...result.searchParams.keys()], ['suiteProject']);
});
test('satellite handshake uses actual current host and the one primary login', () => {
  assert.deepEqual(clerkSatelliteOptions(' https://www.angelidelfuturoaps.com/ ', location), { isSatellite: true, satelliteAutoSync: true, domain: location.host, signInUrl: 'https://www.angelidelfuturoaps.com/admin', signUpUrl: 'https://www.angelidelfuturoaps.com/admin' });
  assert.deepEqual(clerkSatelliteOptions('', location), {});
  assert.deepEqual(clerkSatelliteOptions('  ', location), {});
});
for (const primary of ['http://external.example', 'https://user:pass@primary.example', 'https://primary.example/path', 'https://primary.example/?redirect_url=https://attacker.example', 'https://primary.example/#fragment']) {
  test('rejects invalid primary origin: ' + primary.split('?')[0], () => assert.throws(() => clerkSatelliteOptions(primary, location)));
}
test('explicit local development remains supported', () => {
  assert.equal(clerkSatelliteOptions('http://localhost:3000', { ...location, host: 'localhost:5173' }).signInUrl, 'http://localhost:3000/admin');
});
