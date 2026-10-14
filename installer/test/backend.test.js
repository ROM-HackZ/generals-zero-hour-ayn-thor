// GeneralsX @feature Codex 14/10/2026 Check transfer recovery and save preservation without a device.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const { Installer, validateGame, parseDevices, quote, hashFile, download, extractTools, GAME_DATA, PACKAGE } = require('../backend');

async function fixture(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'thor installer '));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(path.join(root, 'ZH_Generals', 'Data'), { recursive: true });
  await fs.mkdir(path.join(root, 'Data'), { recursive: true });
  await fs.writeFile(path.join(root, 'INIZH.big'), 'BIGFzero-hour');
  await fs.writeFile(path.join(root, 'ZH_Generals', 'INI.big'), 'BIGFgenerals');
  await fs.writeFile(path.join(root, 'Data', "a file's.ini"), 'owned data');
  await fs.writeFile(path.join(root, 'test.apk'), 'PK-fake-apk');
  return root;
}
class FakeThor extends Installer {
  constructor(cache) { super(cache); this.files = new Map(); this.commands = []; }
  async devices() { return { adb: '/fake/adb', devices: [{ serial: 'test', model: 'Thor', state: 'device' }] }; }
  async shell(args) {
    this.commands.push(args);
    const [cmd, ...rest] = args;
    if (cmd === 'getprop') return { code: 0, output: rest[0].endsWith('sdk') ? '33' : 'arm64-v8a' };
    if (cmd === 'df') return { code: 0, output: 'Filesystem 1K-blocks Used Available Use% Mounted on\nfake 999999999 0 999999999 0% /sdcard' };
    if (cmd === 'pm') return { code: 0, output: `package:/data/app/${PACKAGE}/base.apk\n` };
    if (cmd === 'sha256sum') {
      const bytes = this.files.get(rest[0]);
      return { code: bytes ? 0 : 1, output: bytes ? require('node:crypto').createHash('sha256').update(bytes).digest('hex') + '  ' + rest[0] : '' };
    }
    if (cmd === 'mv') { this.files.set(rest[2], this.files.get(rest[1])); this.files.delete(rest[1]); }
    return { code: 0, output: '' };
  }
  async adb(args) {
    this.commands.push(args);
    if (args[0] === 'push') this.files.set(args[2], this.corrupt ? Buffer.from('corrupt') : await fs.readFile(args[1]));
    if (args[0] === 'install' && this.signatureError) throw new Error('INSTALL_FAILED_UPDATE_INCOMPATIBLE');
    return { code: 0, output: 'Success' };
  }
}
test('requires both games and rejects corrupt archives before USB operations', async t => {
  const root = await fixture(t);
  assert.equal((await validateGame(root)).archives.length, 2);
  await fs.writeFile(path.join(root, 'INIZH.big'), 'NOPE');
  await assert.rejects(validateGame(root), /Invalid game archive/);
});
test('quotes Android shell paths with spaces and apostrophes; parses unauthorized and multiple devices', () => {
  assert.equal(quote("a file's.ini"), "'a file'\"'\"'s.ini'");
  const devices = parseDevices('List of devices attached\nabc device product:x model:AYN_Thor\ndef unauthorized\nghi no permissions (missing udev rules)\n');
  assert.deepEqual(devices.map(d => d.state), ['device', 'unauthorized', 'no permissions']);
  assert.equal(devices[0].model, 'AYN Thor');
});
test('verified transfer resumes without rewriting correct files or touching saves', async t => {
  const root = await fixture(t), job = new FakeThor(root), game = await validateGame(root);
  job.files.set('/private/player.sav', Buffer.from('save'));
  await job.transfer([...game.archives, ...game.loose], 'Test');
  assert.equal(job.files.get(`${GAME_DATA}/Data/a file's.ini`).toString(), 'owned data');
  job.commands = [];
  assert.deepEqual(await job.transfer([...game.archives, ...game.loose], 'Test'), { copied: 0, skipped: 3 });
  assert.equal(job.commands.filter(c => c[0] === 'push').length, 0);
  assert.equal(job.files.get('/private/player.sav').toString(), 'save');
});
test('bad USB transfer leaves the old file intact and never commits incoming data', async t => {
  const root = await fixture(t), job = new FakeThor(root), game = await validateGame(root);
  job.corrupt = true;
  const remote = `${GAME_DATA}/${game.archives[0].relative}`;
  job.files.set(remote, Buffer.from('old file'));
  await assert.rejects(job.transfer(game.archives, 'Test'), /Checksum mismatch/);
  assert.equal(job.files.get(remote).toString(), 'old file');
  assert(!job.commands.some(c => c[0] === 'mv'));
});
test('signature mismatch never uninstalls or transfers game data', async t => {
  const root = await fixture(t), job = new FakeThor(root);
  job.signatureError = true;
  await assert.rejects(job.install({ apk: path.join(root, 'test.apk'), serial: 'test', updateOnly: true }), /different signing key/);
  assert(!job.commands.some(c => ['uninstall', 'push', 'rm'].includes(c[0])));
});
test('same installed app is kept running; update-only does not require game folder', async t => {
  const root = await fixture(t), job = new FakeThor(root);
  job.files.set(`/data/app/${PACKAGE}/base.apk`, await fs.readFile(path.join(root, 'test.apk')));
  const result = await job.install({ apk: path.join(root, 'test.apk'), serial: 'test', updateOnly: true });
  assert(result.sameApp);
  assert(!job.commands.some(c => ['install', 'am', 'push'].includes(c[0])));
});
test('cancellation keeps completed files and stops subsequent copies', async t => {
  const root = await fixture(t), job = new FakeThor(root), game = await validateGame(root);
  await job.transfer([game.archives[0]], 'Test');
  job.cancel();
  await assert.rejects(job.transfer(game.archives.slice(1), 'Test'), /Cancelled/);
  assert.equal(job.files.size, 1);
});
test('download checksum failure discards the partial APK; valid cache works offline', async t => {
  const root = await fixture(t);
  const server = http.createServer((_req, res) => res.end('APK download'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const target = path.join(root, 'download.apk'), url = `http://127.0.0.1:${server.address().port}/apk`;
  await assert.rejects(download(url, target, { expectedHash: '0'.repeat(64) }), /checksum/);
  await assert.rejects(fs.stat(target));
  await assert.rejects(fs.stat(`${target}.part`));
  await download(url, target);
  const hash = await hashFile(target);
  await download('http://127.0.0.1:1/offline', target, { expectedHash: hash });
  assert.equal(await fs.readFile(target, 'utf8'), 'APK download');
});
test('USB tools reject archive traversal before writing any files', async t => {
  const root = await fixture(t), zip = path.join(root, 'tools.zip');
  const { zipSync, strToU8 } = require('fflate');
  for (const name of ['../outside.txt', 'platform-tools/../outside.txt', 'platform-tools/a\\b', 'platform-tools/file:stream']) {
    await fs.writeFile(zip, zipSync({ [name]: strToU8('bad data') }));
    await assert.rejects(extractTools(zip, root), /Invalid USB-tools archive path/);
    assert(!(await fs.readdir(root)).some(name => name.startsWith('.tools-')));
  }
});
