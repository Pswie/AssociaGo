import { test } from 'node:test';
import assert from 'node:assert/strict';
import { remoteBackendInfo, isSafeExternalUrl, isSqliteHeader } from '../src/main/runtime-policy.mjs';

test('remote backend preserves a proxy prefix and explicit port', () => {
    const info = remoteBackendInfo('https://example.org:8443/associago/');
    assert.equal(info.apiBaseUrl, 'https://example.org:8443/associago/api');
    assert.equal(info.healthUrl, 'https://example.org:8443/associago/actuator/health');
    assert.equal(info.port, 8443);
});

test('remote backend rejects executable schemes, embedded credentials and ambiguous URLs', () => {
    for (const url of ['javascript:alert(1)', 'file:///tmp/backend', 'https://user:password@example.org', 'https://example.org/?token=secret', 'https://example.org/#path', 'invalid']) {
        assert.equal(remoteBackendInfo(url), null);
    }
    assert.equal(remoteBackendInfo('https://example.org').port, 443);
    assert.equal(remoteBackendInfo('http://127.0.0.1:8080').port, 8080);
});

test('external links cannot launch local files or application protocols', () => {
    for (const url of ['file:///C:/Windows/system32/cmd.exe', 'ms-msdt:/id', 'javascript:alert(1)', 'https://user:password@example.org']) {
        assert.equal(isSafeExternalUrl(url), false);
    }
    assert.equal(isSafeExternalUrl('https://example.org/help'), true);
    assert.equal(isSafeExternalUrl('mailto:support@example.org'), true);
});

test('database imports require the SQLite header including its terminating byte', () => {
    assert.equal(isSqliteHeader(Buffer.from('SQLite format 3\0rest')), true);
    assert.equal(isSqliteHeader(Buffer.from('SQLite format 3!rest')), false);
    assert.equal(isSqliteHeader(Buffer.from('{}')), false);
});
