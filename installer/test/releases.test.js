// GeneralsX @test Codex 19/10/2026 New game releases become the default without rebuilding the desktop app.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { latestRelease } = require('../releases');
const { Installer, PACKAGE } = require('../backend');
const hash = value => crypto.createHash('sha256').update(value).digest('hex');

function release(tag, overrides = {}) {
  const apk = `Thor-RTS-${tag.slice(1)}.apk`, sha256 = hash('test APK');
  const manifest = `${sha256}  ${apk}\n`;
  return { tag_name: tag, draft: false, published_at: '2026-10-19T12:00:00Z', manifest,
    assets: [{ name: apk, state: 'uploaded', digest: `sha256:${sha256}` },
      { name: 'SHA256SUMS.txt', state: 'uploaded', digest: `sha256:${hash(manifest)}` }], ...overrides };
}
function github(releases, pages = [releases]) {
  return async url => {
    if (url.startsWith('https://api.github.com/')) return Response.json(pages[Number(new URL(url).searchParams.get('page')) - 1] || []);
    const tag = decodeURIComponent(new URL(url).pathname.split('/').at(-2));
    const item = releases.find(r => r.tag_name === tag);
    assert(item, `Unexpected release URL ${url}`);
    assert(url.endsWith('/SHA256SUMS.txt'));
    return new Response(item.manifest);
  };
}
test('newest game wins numerically; previews work and newer desktop/draft releases cannot take over', async () => {
  const rows = [release('installer-v9.0.0'), release('v0.1.0-alpha.7'), release('v0.1.0-alpha.10'),
    release('v9.0.0', { draft: true }), release('v8.0.0', { published_at: null })];
  const selected = await latestRelease({ fetchImpl: github(rows) });
  assert.equal(selected.version, '0.1.0-alpha.10');
  assert.equal(selected.url, 'https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/download/v0.1.0-alpha.10/Thor-RTS-0.1.0-alpha.10.apk');
  assert.equal(selected.sha256, hash('test APK'));
});
test('stable beats its release candidates; a newer game version beats an older stable', async () => {
  let rows = [release('v0.1.0'), release('v0.1.0-rc.12')];
  assert.equal((await latestRelease({ fetchImpl: github(rows) })).version, '0.1.0');
  rows.push(release('v0.2.0-beta.1'));
  assert.equal((await latestRelease({ fetchImpl: github(rows) })).version, '0.2.0-beta.1');
});
test('release pagination does not hide game releases behind installer publications', async () => {
  const desktop = Array.from({ length: 100 }, (_, i) => release(`installer-v0.1.${i}`));
  const game = release('v0.1.0-alpha.8');
  assert.equal((await latestRelease({ fetchImpl: github([...desktop, game], [desktop, [game]]) })).version, '0.1.0-alpha.8');
});
test('an incomplete latest game fails rather than silently installing an older APK', async () => {
  const rows = [release('v0.1.0-alpha.7'), release('v0.1.0-alpha.8', { assets: [] })];
  await assert.rejects(latestRelease({ fetchImpl: github(rows) }), /newest game release is missing/);
});
test('missing, duplicated and conflicting APK checksums are rejected', async () => {
  for (const change of ['wrong-name', 'duplicate', 'conflict', 'corrupt-manifest']) {
    const r = release('v0.1.0-alpha.7');
    if (change === 'wrong-name') r.manifest = r.manifest.replace('.apk', '-other.apk');
    if (change === 'duplicate') r.manifest += r.manifest;
    if (change === 'conflict') r.assets[0].digest = `sha256:${'0'.repeat(64)}`;
    if (change !== 'corrupt-manifest') r.assets[1].digest = `sha256:${hash(r.manifest)}`;
    else r.manifest += 'changed';
    await assert.rejects(latestRelease({ fetchImpl: github([r]) }), /checksum/i, change);
  }
});
test('network, API errors, no releases and cancellation never fall back to a pinned old version', async () => {
  await assert.rejects(latestRelease({ fetchImpl: async () => { throw new Error('offline'); } }), /Check your connection/);
  await assert.rejects(latestRelease({ fetchImpl: async () => new Response('', { status: 403 }) }), /403/);
  await assert.rejects(latestRelease({ fetchImpl: github([]) }), /No published/);
  const controller = new AbortController(); controller.abort(new Error('Cancelled'));
  await assert.rejects(latestRelease({ signal: controller.signal, fetchImpl: () => assert.fail('Cancelled request reached network') }), /Cancelled/);
});
test('automatic update uses the discovered release and leaves an identical installed app running', async t => {
  const cache = await fs.mkdtemp(path.join(os.tmpdir(), 'thor-latest-'));
  t.after(() => fs.rm(cache, { recursive: true, force: true }));
  const r = release('v0.1.0-alpha.17'), selected = await latestRelease({ fetchImpl: github([r]) });
  await fs.writeFile(path.join(cache, selected.apk), 'test APK');
  class Device extends Installer {
    commands = [];
    latestRelease() { return Promise.resolve(selected); }
    devices() { return Promise.resolve({ devices: [{ serial: 'test', state: 'device', model: 'Thor' }] }); }
    shell(args) {
      this.commands.push(args);
      return Promise.resolve({ code: 0, output: args[0] === 'pm' ? `package:/data/app/${PACKAGE}/base.apk\n`
        : args[1]?.endsWith('sdk') ? '33' : 'arm64-v8a' });
    }
    remoteHash() { return Promise.resolve(selected.sha256); }
    adb() { assert.fail('An identical installed APK must not be reinstalled'); }
  }
  const device = new Device(cache);
  const result = await device.install({ serial: 'test', updateOnly: true });
  assert(result.sameApp);
  assert(!device.commands.some(args => ['am', 'rm', 'uninstall'].includes(args[0])));
});
