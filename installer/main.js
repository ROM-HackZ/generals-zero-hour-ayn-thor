// GeneralsX @feature Codex 14/10/2026 Isolated desktop installer UI and native file pickers.
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { Installer, findAdb, suggestGameFolder, TERMS } = require('./backend');
let window, active, cache;
const history = [];
const indexURL = pathToFileURL(path.join(__dirname, 'index.html')).href;
const links = {
  terms: TERMS,
  help: 'https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_INSTALL.md',
  releases: 'https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases',
  issues: 'https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/issues'
};
function emit(event) {
  if (event.log) history.push(`${new Date().toISOString()}  ${event.log}`);
  if (!window.isDestroyed()) window.webContents.send('progress', event);
}
function handle(name, callback) {
  ipcMain.handle(name, (event, ...args) => {
    if (event.sender !== window.webContents || event.senderFrame !== window.webContents.mainFrame || event.senderFrame.url !== indexURL) {
      throw new Error('Invalid installer request.');
    }
    return callback(...args);
  });
}
async function operation(options, method) {
  if (active) return { error: 'Wait for the current operation to finish.' };
  const job = active = new Installer(cache, emit);
  try {
    if (!options || typeof options !== 'object') throw new Error('Invalid installer options.');
    const safe = {};
    for (const key of ['game', 'apk', 'adb', 'serial']) {
      if (options[key] != null && typeof options[key] !== 'string') throw new Error('Invalid path or device.');
      safe[key] = options[key]?.trim() || '';
    }
    safe.acceptTools = options.acceptTools === true;
    safe.updateOnly = options.updateOnly === true;
    const result = await job[method](safe);
    // GeneralsX @bugfix Codex 19/10/2026 A one-off local APK must not silently pin every future update.
    if (method === 'install') await fs.writeFile(path.join(cache, 'settings.json'), JSON.stringify({ game: safe.game, adb: job.adbPath }));
    return { result };
  } catch (error) {
    const message = error.message || String(error);
    emit({ log: message });
    return { error: message, cancelled: job.signal.aborted };
  } finally { active = null; }
}
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { if (window) { if (window.isMinimized()) window.restore(); window.focus(); } });
  app.whenReady().then(async () => {
    cache = path.join(app.getPath('userData'), 'downloads');
    await fs.mkdir(cache, { recursive: true });
    window = new BrowserWindow({ width: 880, height: 860, minWidth: 700, minHeight: 720,
      title: 'Thor RTS Installer', backgroundColor: '#10181e', autoHideMenuBar: true,
      icon: path.join(__dirname, 'icon.png'),
      webPreferences: { preload: path.join(__dirname, 'preload.js'), nodeIntegration: false, contextIsolation: true, sandbox: true } });
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', event => event.preventDefault());
    window.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
    handle('initial', async () => {
      let settings = {};
      try { settings = JSON.parse(await fs.readFile(path.join(cache, 'settings.json'), 'utf8')); } catch { /* First run. */ }
      const adb = await findAdb(cache);
      return { game: settings.game || await suggestGameFolder(), adb: adb || settings.adb || '',
        platform: process.platform, version: app.getVersion() };
    });
    handle('browse', async kind => {
      if (active) return '';
      if (!['game', 'apk', 'adb'].includes(kind)) return '';
      const result = await dialog.showOpenDialog(window, kind === 'game'
        ? { title: 'Choose your Zero Hour game folder', properties: ['openDirectory'] }
        : { title: kind === 'apk' ? 'Choose a local APK' : 'Choose adb (adb.exe on Windows)', properties: ['openFile'],
          filters: kind === 'apk' ? [{ name: 'Android app', extensions: ['apk'] }] : [] });
      return result.canceled ? '' : result.filePaths[0];
    });
    handle('devices', options => operation(options, 'devices'));
    handle('install', options => operation(options, 'install'));
    handle('cancel', () => { active?.cancel(); });
    handle('link', key => links[key] ? shell.openExternal(links[key]) : undefined);
    handle('save-log', async () => {
      const result = await dialog.showSaveDialog(window, { defaultPath: 'thor-rts-install.log', filters: [{ name: 'Log', extensions: ['log'] }] });
      if (!result.canceled) await fs.writeFile(result.filePath, history.join('\n') + '\n', 'utf8');
    });
    window.on('close', event => {
      if (active) { event.preventDefault(); emit({ stage: 'Transfer running', detail: 'Cancel the transfer before closing the installer.' }); }
    });
    await window.loadFile(path.join(__dirname, 'index.html'));
  });
  app.on('window-all-closed', () => app.quit());
}
