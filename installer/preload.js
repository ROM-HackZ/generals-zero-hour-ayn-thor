// GeneralsX @feature Codex 14/10/2026 Expose only the installer actions to the sandboxed page.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('thor', {
  initial: () => ipcRenderer.invoke('initial'),
  browse: kind => ipcRenderer.invoke('browse', kind),
  devices: options => ipcRenderer.invoke('devices', options),
  install: options => ipcRenderer.invoke('install', options),
  cancel: () => ipcRenderer.invoke('cancel'),
  link: key => ipcRenderer.invoke('link', key),
  saveLog: () => ipcRenderer.invoke('save-log'),
  onProgress: callback => ipcRenderer.on('progress', (_event, data) => callback(data))
});
