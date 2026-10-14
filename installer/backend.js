// GeneralsX @feature Codex 14/10/2026 Resumable desktop transfer of player-owned data.
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const release = require('./release.json');

const PACKAGE = 'com.generalsthor.zerohour';
const GAME_DATA = `/sdcard/Android/data/${PACKAGE}/files/GameData`;
const LAUNCHER = `${PACKAGE}/com.generalsx.zerohour.ThorLauncher`;
const TERMS = 'https://developer.android.com/studio/terms';
const quote = s => "'" + String(s).replaceAll("'", "'\"'\"'") + "'";
const sizeText = n => `${(n / 1024 ** 3).toFixed(2)} GB`;
async function exists(p) { try { return (await fs.stat(p)).isFile(); } catch { return false; } }
function checkCancelled(signal) { signal?.throwIfAborted(); }

async function hashFile(filename, signal) {
  const hash = crypto.createHash('sha256');
  const handle = await fs.open(filename, 'r');
  try {
    for await (const chunk of handle.createReadStream()) {
      checkCancelled(signal);
      hash.update(chunk);
    }
    return hash.digest('hex');
  } finally { await handle.close(); }
}

// No host shell. Remote Android shell arguments are quoted separately.
function run(command, args, { signal, onOutput, allowFailure = false, timeout = 120000 } = {}) {
  checkCancelled(signal);
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { windowsHide: true, shell: false, signal });
    let output = '';
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
    for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
      const text = chunk.toString();
      output = (output + text).slice(-65536);
      onOutput?.(text);
    });
    child.on('error', error => { clearTimeout(timer); reject(error); });
    child.on('close', code => {
      clearTimeout(timer);
      if (signal?.aborted) return reject(signal.reason);
      if (timedOut) return reject(new Error('USB operation timed out. Check the cable and reconnect, then try again.'));
      if (code !== 0 && !allowFailure) return reject(new Error(output.trim() || `${path.basename(command)} failed (${code}).`));
      resolve({ code, output });
    });
  });
}

function parseDevices(output) {
  return output.split(/\r?\n/).flatMap(line => {
    const match = line.match(/^(\S+)\s+(device|unauthorized|offline|no permissions)\b(.*)$/);
    if (!match) return [];
    const model = match[3].match(/model:(\S+)/)?.[1]?.replaceAll('_', ' ') || 'Android device';
    return [{ serial: match[1], state: match[2], model }];
  });
}

async function findAdb(cache) {
  const exe = process.platform === 'win32' ? 'adb.exe' : 'adb';
  const sdkRoots = [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk'),
    path.join(os.homedir(), 'Library', 'Android', 'sdk'), path.join(os.homedir(), 'Android', 'Sdk')];
  const candidates = [path.join(cache, 'platform-tools', exe),
    ...sdkRoots.filter(Boolean).map(p => path.join(p, 'platform-tools', exe)),
    ...(process.env.PATH || '').split(path.delimiter).filter(Boolean).map(p => path.join(p.replace(/^"|"$/g, ''), exe))];
  for (const p of candidates) if (await exists(p)) return p;
  return '';
}

async function suggestGameFolder() {
  const name = 'Command & Conquer Generals - Zero Hour';
  const steamRoots = process.platform === 'win32'
    ? ['C', 'D', 'E', 'F', 'G'].flatMap(d => [`${d}:\\Steam`, `${d}:\\SteamLibrary`]).concat(
      [process.env['ProgramFiles(x86)'], process.env.ProgramFiles].filter(Boolean).map(p => path.join(p, 'Steam')))
    : [path.join(os.homedir(), '.steam', 'steam'), path.join(os.homedir(), '.local', 'share', 'Steam'),
      path.join(os.homedir(), 'Library', 'Application Support', 'Steam')];
  for (const root of [...steamRoots]) {
    try {
      const vdf = await fs.readFile(path.join(root, 'steamapps', 'libraryfolders.vdf'), 'utf8');
      for (const match of vdf.matchAll(/"path"\s+"([^"]+)"/g)) steamRoots.push(match[1].replaceAll('\\\\', '\\'));
    } catch { /* Steam is optional; users can browse a copied installation. */ }
  }
  for (const root of new Set(steamRoots)) {
    const game = path.join(root, 'steamapps', 'common', name);
    if (await exists(path.join(game, 'INIZH.big'))) return game;
  }
  return '';
}

async function validateGame(root, signal) {
  if (!root || !path.isAbsolute(root)) throw new Error('Choose your Zero Hour game folder first.');
  root = path.resolve(root);
  if (!await exists(path.join(root, 'INIZH.big')) || !await exists(path.join(root, 'ZH_Generals', 'INI.big'))) {
    throw new Error('Choose the folder containing INIZH.big and ZH_Generals/INI.big. Use the Steam PC installation, or a copy of that entire folder.');
  }
  const archives = [];
  for (const relative of ['', 'ZH_Generals']) {
    for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
      checkCancelled(signal);
      if (!/\.big$/i.test(entry.name)) continue;
      if (!entry.isFile() || !/^[A-Za-z0-9._-]+$/.test(entry.name)) throw new Error(`Unsupported archive: ${entry.name}`);
      const local = path.join(root, relative, entry.name);
      const handle = await fs.open(local, 'r');
      try {
        const header = Buffer.alloc(4);
        await handle.read(header, 0, 4, 0);
        if (!['BIGF', 'BIG4'].includes(header.toString('ascii'))) throw new Error(`Invalid game archive: ${entry.name}. Verify your Steam installation.`);
      } finally { await handle.close(); }
      archives.push({ local, relative: [relative, entry.name].filter(Boolean).join('/'), size: (await fs.stat(local)).size });
    }
  }
  const loose = [];
  async function walk(relative) {
    checkCancelled(signal);
    for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
      const child = path.join(relative, entry.name);
      if (entry.isSymbolicLink()) throw new Error(`Game data contains a symbolic link: ${child}. Choose a complete local copy.`);
      if (entry.isDirectory()) await walk(child);
      else if (entry.isFile()) loose.push({ local: path.join(root, child), relative: child.split(path.sep).join('/'), size: (await fs.stat(path.join(root, child))).size });
    }
  }
  for (const relative of ['Data', path.join('ZH_Generals', 'Data')]) {
    try { await fs.access(path.join(root, relative)); } catch { continue; }
    await walk(relative);
  }
  return { root, archives: archives.sort((a, b) => a.relative.localeCompare(b.relative)), loose,
    size: [...archives, ...loose].reduce((n, f) => n + f.size, 0) };
}

async function download(url, target, { signal, expectedHash, onProgress = () => {} } = {}) {
  await fs.mkdir(path.dirname(target), { recursive: true });
  if (await exists(target) && expectedHash && await hashFile(target, signal) === expectedHash) return target;
  const part = `${target}.part`;
  const response = await fetch(url, { signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(300000)]),
    headers: { 'User-Agent': 'Thor-RTS-Installer' } });
  if (!response.ok) throw new Error(`Download failed (${response.status}). Check your connection, or choose a downloaded APK in Advanced.`);
  const total = Number(response.headers.get('content-length')) || 0;
  const handle = await fs.open(part, 'w');
  const hash = crypto.createHash('sha256');
  let bytes = 0;
  try {
    for await (const chunk of response.body) {
      checkCancelled(signal);
      await handle.writeFile(chunk);
      hash.update(chunk);
      bytes += chunk.length;
      onProgress(bytes, total);
    }
  } catch (error) {
    await fs.rm(part, { force: true });
    throw error;
  } finally { await handle.close(); }
  if (expectedHash && hash.digest('hex') !== expectedHash) {
    await fs.rm(part, { force: true });
    throw new Error('The APK checksum did not match the published build. The download was discarded. Try again.');
  }
  await fs.rename(part, target);
  return target;
}

// Google tools contain ordinary files. Extract into a fresh directory, never follow
// archive links or allow paths outside platform-tools; commit only after validation.
async function extractTools(zip, cache, signal) {
  const { unzipSync } = require('fflate');
  let total = 0;
  const files = unzipSync(await fs.readFile(zip), { filter: entry => {
    const segments = entry.name.replace(/\/$/, '').split('/');
    if (segments[0] !== 'platform-tools' || segments.some(s => !s || s === '..' || s === '.' || /[\\:\x00-\x1f]/.test(s))) {
      throw new Error('Invalid USB-tools archive path. Nothing was installed.');
    }
    total += entry.originalSize;
    if (entry.originalSize > 128 * 1024 ** 2 || total > 512 * 1024 ** 2) throw new Error('USB-tools archive is unexpectedly large.');
    return !entry.name.endsWith('/');
  } });
  const temporary = await fs.mkdtemp(path.join(path.resolve(cache), '.tools-'));
  try {
    for (const [name, bytes] of Object.entries(files)) {
      checkCancelled(signal);
      const local = path.resolve(temporary, ...name.split('/'));
      if (!local.startsWith(temporary + path.sep)) throw new Error('Invalid tools path.');
      await fs.mkdir(path.dirname(local), { recursive: true });
      await fs.writeFile(local, bytes);
    }
    const adb = path.join(temporary, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
    if (process.platform !== 'win32') await fs.chmod(adb, 0o755);
    const version = await run(adb, ['version'], { signal });
    if (!version.output.includes('Android Debug Bridge version')) throw new Error('Downloaded USB tools did not contain a working adb.');
    checkCancelled(signal);
    const destination = path.resolve(cache, 'platform-tools');
    if (path.dirname(destination) !== path.resolve(cache)) throw new Error('Invalid tools cache directory.');
    await fs.rm(destination, { recursive: true, force: true });
    await fs.rename(path.join(temporary, 'platform-tools'), destination);
    return path.join(destination, path.basename(adb));
  } finally {
    if (path.dirname(temporary) === path.resolve(cache) && path.basename(temporary).startsWith('.tools-')) {
      await fs.rm(temporary, { recursive: true, force: true });
    }
  }
}

class Installer {
  constructor(cache, emit = () => {}) { this.cache = cache; this.emit = emit; this.controller = new AbortController(); }
  cancel() { this.controller.abort(new Error('Cancelled. Completed files are kept; click Install again to resume.')); }
  get signal() { return this.controller.signal; }
  report(stage, detail, progress = null) { this.emit({ stage, detail, progress }); }
  log(detail) { this.emit({ log: detail }); }
  adb(args, options = {}) { return run(this.adbPath, ['-s', this.serial, ...args], { signal: this.signal, ...options }); }
  shell(args, options) { return this.adb(['shell', args.map(quote).join(' ')], options); }
  async tools(adbPath, acceptTools) {
    if (adbPath) {
      if (!path.isAbsolute(adbPath) || !await exists(adbPath)) throw new Error('The USB tools path must point to adb (adb.exe on Windows).');
      return adbPath;
    }
    const existing = await findAdb(this.cache);
    if (existing) return existing;
    if (!acceptTools) throw new Error('USB tools are missing. Read and accept Google’s SDK terms to download them, or choose your existing adb in Advanced.');
    const platform = { win32: 'windows', darwin: 'darwin', linux: 'linux' }[process.platform];
    if (!platform || (platform === 'linux' && process.arch !== 'x64')) throw new Error('Automatic USB tools download supports Windows, macOS and Linux x64. Choose a compatible adb in Advanced.');
    this.report('Getting USB tools', 'Downloading Android Platform-Tools from Google…');
    const zip = path.join(this.cache, `platform-tools-${platform}.zip`);
    await download(`https://dl.google.com/android/repository/platform-tools-latest-${platform}.zip`, zip,
      { signal: this.signal, onProgress: (n, total) => this.report('Getting USB tools', 'Downloading from Google…', total ? n / total : null) });
    checkCancelled(this.signal);
    return extractTools(zip, this.cache, this.signal);
  }
  async devices(options) {
    this.adbPath = await this.tools(options.adb, options.acceptTools);
    const result = await run(this.adbPath, ['devices', '-l'], { signal: this.signal });
    return { adb: this.adbPath, devices: parseDevices(result.output) };
  }
  async remoteHash(remote) {
    const result = await this.shell(['sha256sum', remote], { allowFailure: true });
    return result.code === 0 ? result.output.match(/^([a-f0-9]{64})\b/i)?.[1]?.toLowerCase() : '';
  }
  async transfer(files, label) {
    const total = files.reduce((sum, f) => sum + f.size, 0);
    let completed = 0, copied = 0, skipped = 0;
    const created = new Set();
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      checkCancelled(this.signal);
      const describe = `${i + 1}/${files.length} · ${file.relative}`;
      this.report(label, `Checking ${describe}`, total ? completed / total : 0);
      const hash = file.hash || await hashFile(file.local, this.signal);
      const remote = `${GAME_DATA}/${file.relative}`;
      if (file.matches || await this.remoteHash(remote) === hash) { skipped++; }
      else {
        const parent = path.posix.dirname(remote);
        if (!created.has(parent)) { await this.shell(['mkdir', '-p', parent]); created.add(parent); }
        this.report(label, `Copying ${describe}`, total ? completed / total : 0);
        await this.adb(['push', file.local, `${remote}.incoming`], {
          timeout: 900000,
          onOutput: text => {
            const percent = text.match(/\[\s*(\d+)%\]/)?.[1];
            if (percent) this.report(label, `Copying ${describe}`, total ? (completed + file.size * Number(percent) / 100) / total : 0);
          }
        });
        if (await this.remoteHash(`${remote}.incoming`) !== hash) throw new Error(`Checksum mismatch: ${file.relative}. Reconnect and try again; the original file was kept.`);
        await this.shell(['mv', '-f', `${remote}.incoming`, remote]);
        copied++;
      }
      completed += file.size;
      this.report(label, `Checked ${describe}`, total ? completed / total : 1);
    }
    this.log(`${label}: ${copied} copied, ${skipped} already correct.`);
    return { copied, skipped };
  }
  async install(options) {
    this.report('Checking game folder', 'Reading your PC installation…');
    const game = options.updateOnly ? null : await validateGame(options.game, this.signal);
    if (game) this.log(`${game.archives.length} archives, ${game.loose.length} loose files · ${sizeText(game.size)}.`);
    const result = await this.devices(options);
    const ready = result.devices.filter(d => d.state === 'device');
    this.serial = options.serial || (ready.length === 1 ? ready[0].serial : '');
    const chosen = result.devices.find(d => d.serial === this.serial);
    if (!chosen || chosen.state !== 'device') throw new Error(ready.length > 1 ? 'Choose the Thor in the device list.' : 'Unlock the Thor, enable USB debugging and accept the USB permission prompt. Then refresh devices.');
    const sdk = Number((await this.shell(['getprop', 'ro.build.version.sdk'])).output.trim());
    const abi = (await this.shell(['getprop', 'ro.product.cpu.abilist'])).output.trim();
    if (sdk < 28 || !abi.includes('arm64-v8a')) throw new Error('This preview needs Android 9 or newer on an ARM64 device. It was tested on the AYN Thor.');
    this.log(`Using ${chosen.model} (${this.serial}).`);
    if (game) {
      const df = await this.shell(['df', '-k', '/sdcard']);
      const available = Number(df.output.trim().split(/\r?\n/).at(-1).trim().split(/\s+/)[3]) * 1024;
      // A resumed transfer can need far less than the full dataset. Account only for different files.
      this.report('Checking storage', 'Comparing files already on the Thor…');
      let required = 0, largest = 0;
      const files = [...game.archives, ...game.loose];
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        checkCancelled(this.signal);
        this.report('Checking existing files', `${i + 1}/${files.length} · ${file.relative}`, i / files.length);
        file.hash = await hashFile(file.local, this.signal);
        file.matches = await this.remoteHash(`${GAME_DATA}/${file.relative}`) === file.hash;
        if (!file.matches) {
          required += file.size; largest = Math.max(largest, file.size);
        }
      }
      if (Number.isFinite(available) && available < required + largest + 200 * 1024 ** 2) {
        throw new Error(`Not enough free space on the Thor. Need about ${sizeText(required + largest + 200 * 1024 ** 2)} for this transfer; ${sizeText(available)} is free.`);
      }
    }
    let apk = options.apk;
    if (apk) {
      if (!path.isAbsolute(apk) || !await exists(apk)) throw new Error('Choose an existing APK file, or leave the APK path blank for the published preview.');
      this.log('Using your local APK. The published checksum applies only to the automatic download.');
    } else {
      this.report('Getting the game app', `Downloading preview ${release.version}…`);
      apk = await download(release.url, path.join(this.cache, release.apk), { signal: this.signal, expectedHash: release.sha256,
        onProgress: (n, total) => this.report('Getting the game app', `Downloading preview ${release.version}…`, total ? n / total : null) });
      this.log('Published APK checksum verified.');
    }
    const installed = await this.shell(['pm', 'path', PACKAGE], { allowFailure: true });
    const installedPath = installed.output.match(/^package:(.+base\.apk)\s*$/m)?.[1]?.trim();
    const sameApp = installedPath && await this.remoteHash(installedPath) === await hashFile(apk, this.signal);
    if (!sameApp) {
      this.report('Installing the game app', 'Updating Thor RTS and keeping its data…');
      try { await this.adb(['install', '-r', apk], { timeout: 300000 }); }
      catch (error) {
        if (/UPDATE_INCOMPATIBLE|signatures.*match/i.test(error.message)) throw new Error('This APK has a different signing key. Back up your saves before choosing how to replace it. The installer has left the existing app in place.');
        throw error;
      }
      await this.shell(['am', 'start', '-n', LAUNCHER]);
    } else this.log('The same APK is already installed.');
    if (game) {
      if (sameApp && [...game.archives, ...game.loose].some(file => !file.matches)) {
        // The game may hold archives open. Stop it only when replacing actual data.
        this.report('Preparing game files', 'Closing Thor RTS before replacing game files…');
        await this.shell(['am', 'force-stop', PACKAGE]);
      }
      await this.shell(['mkdir', '-p', `${GAME_DATA}/ZH_Generals`]);
      await this.transfer(game.archives, 'Game archives');
      await this.transfer(game.loose, 'Extra game files');
    }
    // Avoid disturbing a match when everything already matches on a later run.
    if (!sameApp || game && [...game.archives, ...game.loose].some(file => !file.matches)) await this.shell(['am', 'start', '-n', LAUNCHER]);
    this.report('Ready to play', 'Open Thor RTS on the handheld and press Play.', 1);
    this.log('Done. Your PC installation and existing saves were kept.');
    return { serial: this.serial, sameApp, adb: this.adbPath, model: chosen.model };
  }
}

module.exports = { Installer, validateGame, parseDevices, findAdb, suggestGameFolder, hashFile, download, extractTools,
  quote, release, TERMS, GAME_DATA, PACKAGE, run };
