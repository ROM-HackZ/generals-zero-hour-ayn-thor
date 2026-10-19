// GeneralsX @feature Codex 19/10/2026 Resolve the newest published game and its checksum instead of pinning an old APK.
const crypto = require('node:crypto');
const REPOSITORY = 'ROM-HackZ/generals-zero-hour-ayn-thor';
const API = `https://api.github.com/repos/${REPOSITORY}/releases`;
const DOWNLOADS = `https://github.com/${REPOSITORY}/releases/download`;

function version(tag) {
  const match = /^v(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?$/.exec(tag || '');
  if (!match) return null;
  const parts = [Number(match[1]), Number(match[2]), Number(match[3]),
    match[4] ? { alpha: 0, beta: 1, rc: 2 }[match[4]] : 3, Number(match[5] || 0)];
  return parts.every(Number.isSafeInteger) ? parts : null;
}
function compare(a, b) {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}
function asset(release, name) {
  const matches = (release.assets || []).filter(a => a.name === name && a.state === 'uploaded');
  if (matches.length !== 1) throw new Error(`The newest game release is missing ${name}. Try again later, or choose a local APK in Advanced.`);
  return matches[0];
}
async function latestRelease({ signal, fetchImpl = fetch } = {}) {
  async function request(url) {
    signal?.throwIfAborted();
    let response;
    try {
      response = await fetchImpl(url, {
        signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(30000)]),
        headers: { 'User-Agent': 'Thor-RTS-Installer', Accept: 'application/vnd.github+json' }
      });
    } catch (error) {
      signal?.throwIfAborted();
      throw new Error('Could not check the latest game release. Check your connection, or choose a local APK in Advanced.', { cause: error });
    }
    if (!response.ok) throw new Error(`Could not check the latest game release (${response.status}). Try again later, or choose a local APK in Advanced.`);
    return response;
  }
  let newest;
  // Desktop releases have their own tags. Include game previews, but never drafts or installer files.
  for (let page = 1; ; page++) {
    const releases = await (await request(`${API}?per_page=100&page=${page}`)).json();
    if (!Array.isArray(releases)) throw new Error('Invalid game release list. Nothing was installed.');
    for (const candidate of releases) {
      const parsed = version(candidate.tag_name);
      if (!parsed || candidate.draft !== false || !Number.isFinite(Date.parse(candidate.published_at))) continue;
      if (!newest || compare(parsed, newest.parsed) > 0) newest = { release: candidate, parsed };
    }
    if (releases.length < 100) break;
  }
  if (!newest) throw new Error('No published Thor RTS game release was found. Choose a local APK in Advanced.');
  const release = newest.release, tag = release.tag_name, name = tag.slice(1);
  const apk = `Thor-RTS-${name}.apk`, apkAsset = asset(release, apk), sums = asset(release, 'SHA256SUMS.txt');
  const base = `${DOWNLOADS}/${encodeURIComponent(tag)}`;
  const manifest = await (await request(`${base}/SHA256SUMS.txt`)).text();
  if (manifest.length > 65536) throw new Error('The game checksum file is unexpectedly large. Nothing was installed.');
  if (sums.digest && sums.digest !== 'sha256:' + crypto.createHash('sha256').update(manifest).digest('hex')) {
    throw new Error('The published checksum file did not match GitHub’s digest. Nothing was installed.');
  }
  const hashes = manifest.split(/\r?\n/).flatMap(line => {
    const match = /^([a-f0-9]{64})\s+\*?([^\r\n]+)$/i.exec(line.trim());
    return match && match[2] === apk ? [match[1].toLowerCase()] : [];
  });
  if (hashes.length !== 1 || apkAsset.digest && apkAsset.digest !== `sha256:${hashes[0]}`) {
    throw new Error('The newest APK has a missing or inconsistent checksum. Nothing was installed.');
  }
  signal?.throwIfAborted();
  return { version: name, apk, url: `${base}/${apk}`, sha256: hashes[0] };
}

module.exports = { latestRelease };
