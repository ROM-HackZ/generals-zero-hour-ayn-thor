// GeneralsX @feature Codex 14/10/2026 Keep device discovery and long transfers off the UI thread.
const $ = id => document.getElementById(id);
let busy = false;
function options() { return { game: $('game').value, adb: $('adb').value, apk: $('apk').value,
  serial: $('device').value, acceptTools: $('accept-tools').checked, updateOnly: $('update-only').checked }; }
function setBusy(value) {
  busy = value;
  for (const control of document.querySelectorAll('input, select, button[data-browse], #refresh, #install')) control.disabled = value;
  $('game').disabled = value || $('update-only').checked;
  $('cancel').hidden = !value;
  $('cancel').disabled = false;
  $('progress').hidden = !value;
  if (value) $('status').classList.remove('error');
}
function status(stage, detail, error = false) {
  $('stage').textContent = stage;
  $('detail').textContent = detail;
  $('status').classList.toggle('error', error);
}
window.thor.onProgress(event => {
  if (event.stage) status(event.stage, event.detail);
  if (event.progress != null) $('progress').value = Math.max(0, Math.min(1, event.progress));
  else if (event.stage) $('progress').removeAttribute('value');
  if (event.log) { $('log').textContent += event.log + '\n'; $('log').scrollTop = $('log').scrollHeight; }
});
for (const button of document.querySelectorAll('[data-browse]')) button.addEventListener('click', async () => {
  const selected = await window.thor.browse(button.dataset.browse);
  if (selected) $(button.dataset.browse).value = selected;
  if (button.dataset.browse === 'adb' && selected) await refresh();
});
for (const button of document.querySelectorAll('[data-link]')) button.addEventListener('click', () => window.thor.link(button.dataset.link));
$('save-log').addEventListener('click', () => window.thor.saveLog());
$('update-only').addEventListener('change', () => { $('game').disabled = $('update-only').checked; });
$('cancel').addEventListener('click', () => { $('cancel').disabled = true; status('Cancelling', 'Finishing the current check. Completed files will be kept.'); window.thor.cancel(); });
async function refresh() {
  if (busy) return;
  const previous = $('device').value;
  setBusy(true);
  status('Looking for your Thor', 'Checking the USB connection…');
  try {
    const response = await window.thor.devices(options());
    $('device').replaceChildren();
    if (response.error) {
      $('device').add(new Option('No device found — check USB debugging', ''));
      $('tools-note').hidden = true;
      // Missing tools need an explicit license choice; do not accept it for the player.
      if (/USB tools are missing/.test(response.error)) $('tools-note').hidden = false;
      status(response.cancelled ? 'Cancelled' : 'Check your connection', response.error, !response.cancelled);
      return;
    }
    const { devices, adb } = response.result;
    $('adb').value = adb;
    $('tools-note').hidden = true;
    const ready = devices.filter(d => d.state === 'device');
    if (ready.length !== 1) $('device').add(new Option(ready.length ? 'Choose your Thor…' : 'No ready device — unlock the Thor', ''));
    for (const device of devices) {
      const option = new Option(`${device.model} · ${device.serial} · ${device.state === 'device' ? 'Ready' : device.state}`, device.serial);
      option.disabled = device.state !== 'device';
      $('device').add(option);
    }
    if (ready.some(d => d.serial === previous)) $('device').value = previous;
    else if (ready.length === 1) $('device').value = ready[0].serial;
    const unauthorized = devices.some(d => d.state === 'unauthorized');
    $('device-hint').textContent = unauthorized ? 'Accept the USB debugging permission prompt on the handheld, then refresh.'
      : devices.some(d => d.state === 'no permissions') ? 'Linux needs permission to access the USB device. Setup help has the steps.'
      : ready.length ? 'Connected. Check the folder above, then install.' : 'Check the cable and USB debugging. Setup help has the steps.';
    status(ready.length ? 'Ready when you are' : 'Waiting for your Thor', $('device-hint').textContent);
  } catch (error) { status('Could not check devices', error.message, true); }
  finally { setBusy(false); }
}
$('refresh').addEventListener('click', refresh);
$('install').addEventListener('click', async () => {
  if (busy) return;
  setBusy(true);
  $('install').textContent = 'Installing…';
  try {
    const response = await window.thor.install(options());
    if (response.error) {
      status(response.cancelled ? 'Cancelled' : 'Install stopped', response.error, !response.cancelled);
      if (/USB tools are missing/.test(response.error)) $('tools-note').hidden = false;
    } else {
      $('adb').value = response.result.adb;
      $('tools-note').hidden = true;
      $('device').replaceChildren(new Option(`${response.result.model} · ${response.result.serial} · Ready`, response.result.serial));
      $('device-hint').textContent = 'Connected. Installation complete.';
    }
  } catch (error) { status('Install stopped', error.message, true); }
  finally { setBusy(false); $('install').textContent = 'Install on Thor'; }
});
window.thor.initial().then(async initial => {
  for (const field of ['game', 'adb', 'apk']) $(field).value = initial[field] || '';
  $('version').textContent = `Game preview ${initial.release.version}. Downloads the app and copies your own game files over USB.`;
  if (initial.adb) await refresh();
  else {
    $('device').replaceChildren(new Option('USB tools needed — see below', ''));
    $('tools-note').hidden = false;
    status('Connect your Thor', 'Accept Google’s terms in step 2, then refresh to get the USB tools. You can also choose an existing adb in Advanced.');
  }
}).catch(error => status('Could not start installer', error.message, true));
