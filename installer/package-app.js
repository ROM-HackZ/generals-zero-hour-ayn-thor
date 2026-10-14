// GeneralsX @build Codex 14/10/2026 Local desktop bundles; no CI or game data.
const path = require('node:path');
const fs = require('node:fs/promises');
const { packager } = require('@electron/packager');
const { run } = require('./backend');
const pkg = require('./package.json');
const [platform = process.platform, arch = process.arch, format] = process.argv.slice(2);
if (!['win32', 'darwin', 'linux'].includes(platform) || !['x64', 'arm64'].includes(arch)) throw new Error('Usage: node package-app.js [win32|darwin|linux] [x64|arm64]');
(async () => {
  // License for this derivative and third-party notices ship beside the app.
  await fs.copyFile(path.join(__dirname, '..', 'LICENSE.md'), path.join(__dirname, 'LICENSE.md'));
  const folders = await packager({
    dir: __dirname, out: path.join(__dirname, '..', 'artifacts', 'installer'),
    name: 'Thor RTS Installer', executableName: 'thor-rts-installer', platform, arch,
    electronVersion: pkg.devDependencies.electron, appVersion: pkg.version,
    appBundleId: 'org.romhackz.thor-rts-installer', appCategoryType: 'public.app-category.utilities',
    icon: platform === 'win32' ? path.join(__dirname, 'icon.ico') : platform === 'darwin' ? path.join(__dirname, 'icon.icns') : undefined,
    asar: true, prune: true, overwrite: true,
    ignore: [/^\/test($|\/)/, /^\/package-app\.js$/, /^\/make-icon\.py$/, /^\/package-lock\.json$/],
    ...(platform === 'win32' ? { win32metadata: { CompanyName: 'ROM-HackZ', FileDescription: pkg.productName, ProductName: pkg.productName } } : {})
  });
  for (const folder of folders) console.log(folder);
  if (platform === 'darwin') {
    const bundle = path.join(folders[0], 'Thor RTS Installer.app');
    if (process.env.THOR_RCODESIGN) {
      await run(process.env.THOR_RCODESIGN, ['sign', '--timestamp-url', 'none', '--entitlements-xml-file',
        path.join(__dirname, 'entitlements.mac.plist'), bundle], { timeout: 300000 });
      console.log('Mac bundle ad-hoc signed (no Developer ID or notarization).');
    } else if (process.platform === 'darwin') {
      await run('/usr/bin/codesign', ['--deep', '--force', '--sign', '-', '--entitlements',
        path.join(__dirname, 'entitlements.mac.plist'), bundle], { timeout: 300000 });
    } else throw new Error('Set THOR_RCODESIGN to rcodesign when packaging Mac apps from another OS.');
  }
  if (format) {
    const { build, Platform, Arch } = require('electron-builder');
    if (!(platform === 'win32' && format === 'portable' || platform === 'linux' && format === 'deb')) throw new Error('Optional format: portable on Windows, deb on Linux.');
    const target = platform === 'win32' ? Platform.WINDOWS : Platform.LINUX;
    await build({ prepackaged: folders[0], targets: target.createTarget([format], Arch[arch]),
      projectDir: __dirname, config: {
        appId: 'org.romhackz.thor-rts-installer', productName: pkg.productName,
        directories: { output: path.join(__dirname, '..', 'artifacts', 'installer') },
        artifactName: `Thor-RTS-Installer-${pkg.version}-${platform === 'win32' ? 'Windows' : 'Linux'}-${arch}.\u0024{ext}`,
        win: { icon: path.join(__dirname, 'icon.ico'), executableName: 'thor-rts-installer', signAndEditExecutable: false },
        portable: { requestExecutionLevel: 'user' },
        linux: { icon: path.join(__dirname, 'icon.png'), executableName: 'thor-rts-installer', category: 'Utility', syncDesktopName: true,
          synopsis: pkg.description, maintainer: `${pkg.author.name} <${pkg.author.email}>` },
        deb: { depends: ['libgtk-3-0', 'libnotify4', 'libnss3', 'libxss1', 'libxtst6', 'xdg-utils', 'libatspi2.0-0', 'libuuid1', 'libsecret-1-0', 'libgbm1'] }
      } });
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
