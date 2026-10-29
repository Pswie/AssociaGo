import { URL } from 'node:url';

function remoteBackendInfo(rawUrl) {
    try {
        const url = new URL(rawUrl);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
            return null;
        }
        const baseUrl = `${url.origin}${url.pathname.replace(/\/+$/, '')}`;
        return {
            schemaVersion: 1,
            protocol: url.protocol.slice(0, -1),
            host: url.hostname,
            port: Number(url.port || (url.protocol === 'https:' ? 443 : 80)),
            baseUrl,
            apiBaseUrl: `${baseUrl}/api`,
            healthUrl: `${baseUrl}/actuator/health`,
            remote: true
        };
    } catch {
        return null;
    }
}

function isSafeExternalUrl(rawUrl) {
    try {
        const url = new URL(rawUrl);
        return ['https:', 'http:', 'mailto:'].includes(url.protocol) && !url.username && !url.password;
    } catch {
        return false;
    }
}

function isSqliteHeader(buffer) {
    return buffer.length >= 16 && buffer.subarray(0, 16).equals(Buffer.from('SQLite format 3\0'));
}

export { remoteBackendInfo, isSafeExternalUrl, isSqliteHeader };
