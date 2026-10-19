// GeneralsX @test Codex 19/10/2026 Exercise the packaged installer through its actual renderer and IPC.
// Usage: node test/ui-smoke.js EXE OUTPUT_DIR [ADB SERIAL EXPECTED_GAME_VERSION]
// The optional arguments perform a real app-only install/update. Save the game first.
const fs = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const { latestRelease } = require('../releases');
const pkg = require('../package.json');
const [exe, output, adb, serial, expected] = process.argv.slice(2);
if (!exe || !output || Boolean(adb) !== Boolean(serial) || Boolean(adb) !== Boolean(expected)) {
  throw new Error('Usage: node test/ui-smoke.js EXE OUTPUT_DIR [ADB SERIAL EXPECTED_GAME_VERSION]');
}
(async () => {
  const root = path.resolve(output), profile = path.join(root, 'profile');
  await fs.mkdir(path.join(profile, 'downloads'), { recursive: true });
  // An old remembered local APK must not override automatic updates on launch.
  await fs.writeFile(path.join(profile, 'downloads', 'settings.json'), JSON.stringify({ apk: 'obsolete.apk', adb: adb || '' }));
  if (expected) assert.equal((await latestRelease()).version, expected, 'Unexpected live release; do not update the device');
  const child = spawn(path.resolve(exe), ['--remote-debugging-port=0', `--user-data-dir=${profile}`], { windowsHide: true });
  let socket;
  try {
    const port = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Installer debugging endpoint did not open')), 30000);
      child.on('exit', code => { clearTimeout(timer); reject(new Error(`Installer exited early (${code})`)); });
      child.stderr.on('data', chunk => {
        const match = chunk.toString().match(/DevTools listening on ws:\/\/127\.0\.0\.1:(\d+)/);
        if (match) { clearTimeout(timer); resolve(Number(match[1])); }
      });
      child.on('error', reject);
    });
    let page;
    for (let i = 0; i < 100 && !page; i++) {
      page = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(p => p.url.endsWith('/index.html'));
      if (!page) await new Promise(r => setTimeout(r, 100));
    }
    assert(page, 'Installer page missing');
    socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
    let sequence = 0; const pending = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data), request = pending.get(message.id);
      if (request) { pending.delete(message.id); message.error ? request.reject(new Error(message.error.message)) : request.resolve(message.result); }
    };
    const call = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++sequence; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async expression => {
      const value = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      assert(!value.exceptionDetails, JSON.stringify(value.exceptionDetails)); return value.result.value;
    };
    for (let i = 0; i < 100; i++) {
      if ((await evaluate("document.querySelector('#version')?.textContent || ''")).includes(`Installer ${pkg.version}`)) break;
      await new Promise(r => setTimeout(r, 100));
    }
    const label = await evaluate("document.querySelector('#version').textContent");
    assert(label.includes('newest published game app'), label);
    assert.equal(await evaluate("document.querySelector('#apk').value"), '', 'Old local APK remained the default');
    if (expected) {
      await evaluate(`document.querySelector('#adb').value=${JSON.stringify(adb)}; document.querySelector('#refresh').click()`);
      for (let i = 0; i < 100; i++) {
        if (await evaluate(`!document.querySelector('#install').disabled && document.querySelector('#device').value===${JSON.stringify(serial)}`)) break;
        await new Promise(r => setTimeout(r, 100));
      }
      assert.equal(await evaluate("document.querySelector('#device').value"), serial);
      await evaluate("document.querySelector('#update-only').click(); document.querySelector('#install').click()");
      for (let i = 0; i < 900; i++) {
        const stage = await evaluate("document.querySelector('#stage').textContent");
        if (stage === 'Ready to play') break;
        assert(!['Install stopped', 'Cancelled'].includes(stage), await evaluate("document.querySelector('#detail').textContent"));
        await new Promise(r => setTimeout(r, 200));
      }
      assert.equal(await evaluate("document.querySelector('#stage').textContent"), 'Ready to play');
      const log = await evaluate("document.querySelector('#log').textContent");
      assert(log.includes('Published APK checksum verified.'));
      assert(log.includes('The same APK is already installed.'));
    }
    const capture = await call('Page.captureScreenshot', { format: 'png' });
    await fs.writeFile(path.join(root, 'installer.png'), Buffer.from(capture.data, 'base64'));
    console.log(expected ? 'Packaged installer UI, automatic default, USB update/checksum and identical-app preservation passed.'
      : 'Packaged installer UI and automatic default passed.');
  } finally {
    // Finish the DevTools close handshake while the app still owns the socket.
    if (socket && socket.readyState === WebSocket.OPEN) await new Promise(resolve => {
      const timer = setTimeout(resolve, 2000);
      socket.addEventListener('close', () => { clearTimeout(timer); resolve(); }, { once: true });
      socket.close();
    });
    child.kill(); child.stdout?.destroy(); child.stderr?.destroy(); child.unref();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
