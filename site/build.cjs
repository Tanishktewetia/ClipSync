// Package the current Android beta APK and Windows setup. Never uploads or deploys.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const destination = path.join(root, 'dist/site');
const json = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
const sha256 = data => crypto.createHash('sha256').update(data).digest('hex');
const mib = bytes => `${(bytes / 1048576).toFixed(bytes < 2 * 1048576 ? 1 : 0)} MB`;

if (process.env.VERCEL === '1') {
  const releases = 'https://github.com/Tanishktewetia/ClipSync/releases';
  const replacements = source => source
    .replaceAll('href="downloads/windows-setup.exe" download', `href="${releases}" target="_blank" rel="noopener noreferrer"`)
    .replaceAll('href="downloads/android.apk" download', `href="${releases}" target="_blank" rel="noopener noreferrer"`)
    .replaceAll('href="downloads/SHA256SUMS.txt" download', `href="${releases}" target="_blank" rel="noopener noreferrer"`)
    .replaceAll('Download Windows installer', 'Windows beta releases')
    .replaceAll('Download Android APK', 'Android beta releases')
    .replaceAll('Verify both downloads with SHA-256', 'View release checksums')
    .replaceAll('Download SHA256SUMS.txt for both files', 'View release checksums')
    .replaceAll('This site bundle contains the current Windows installer and optimized Android APK. Compare each downloaded file\'s SHA-256 with the generated checksum list.', 'Beta installers and SHA-256 checksums are published with GitHub releases when available.')
    .replace('<pre class="checksum" data-checksum>Loading this build\'s checksums…</pre>', '<pre class="checksum">Release checksums are listed with each GitHub beta release.</pre>')
    .replace('v0.7 beta', 'Latest beta');

  fs.rmSync(destination, {recursive:true, force:true});
  fs.mkdirSync(destination, {recursive:true});
  for (const file of ['index.html','docs.html']) {
    fs.writeFileSync(path.join(destination,file), replacements(fs.readFileSync(path.join(__dirname,file),'utf8')));
  }
  for (const file of ['style.css','app.js','mark.svg']) {
    fs.copyFileSync(path.join(__dirname,file),path.join(destination,file));
  }
  console.log(`Built Vercel static site: ${destination}. Binary downloads link to GitHub Releases.`);
  process.exit(0);
}

const androidOutput = path.join(root, 'android/app/build/outputs/apk/release');
const android = json(path.join(androidOutput, 'output-metadata.json'));
const androidElement = android.elements.find(element => element.outputFile);
if (!androidElement?.versionName || !androidElement?.versionCode) throw new Error('Build the optimized Android release APK first.');
const apk = path.join(androidOutput, androidElement.outputFile);
if (!fs.existsSync(apk)) throw new Error(`Android APK missing: ${apk}`);
const apkName = `ClipSync-beta-${androidElement.versionName}.apk`;
const apkBytes = fs.readFileSync(apk);
if (apkBytes.subarray(0, 2).toString('ascii') !== 'PK') throw new Error('Android artifact is not a valid APK/ZIP file.');

const windows = json(path.join(root, 'dist/windows-download/download.json'));
if (!windows.version || windows.runtime !== 'win-x64' || windows.installer !== true || windows.selfContained !== false || windows.runtimeBootstrap !== true || windows.signed !== false)
  throw new Error('Run scripts/build-windows-download.ps1 first; expected the compact unsigned Windows setup.');
const setupPath = path.join(root, 'dist', windows.filename);
const setupBytes = fs.readFileSync(setupPath);
if (setupBytes.length !== windows.bytes || sha256(setupBytes) !== windows.sha256 || setupBytes.toString('ascii', 0, 2) !== 'MZ')
  throw new Error('Windows setup does not match its build manifest.');
const pe = setupBytes.readUInt32LE(0x3c);
if (pe > setupBytes.length - 6 || setupBytes.toString('ascii', pe, pe + 4) !== 'PE\0\0') throw new Error('Expected a valid Windows setup executable.');

const artifacts = [
  {platform:'android', version:androidElement.versionName, filename:apkName, data:apkBytes},
  {platform:'windows', version:windows.version, filename:windows.filename, data:setupBytes}
];
const replacements = source => source
  .replaceAll('downloads/windows-setup.exe', `downloads/${windows.filename}`)
  .replaceAll('downloads/android.apk', `downloads/${apkName}`)
  .replaceAll('Windows 11 · x64</p>', `Windows 11 · x64 · v${windows.version} · ${mib(setupBytes.length)}</p>`)
  .replaceAll('Android 10+ for TLS 1.3</p>', `Android 10+ for TLS 1.3 · v${androidElement.versionName} · ${mib(apkBytes.length)}</p>`);

fs.mkdirSync(destination, {recursive:true});
const downloads = path.join(destination, 'downloads');
fs.rmSync(downloads, {recursive:true, force:true});
fs.mkdirSync(downloads, {recursive:true});
for (const file of ['index.html','docs.html']) fs.writeFileSync(path.join(destination,file), replacements(fs.readFileSync(path.join(__dirname,file),'utf8')));
for (const file of ['style.css','app.js','mark.svg']) fs.copyFileSync(path.join(__dirname,file),path.join(destination,file));
for (const item of artifacts) {
  fs.writeFileSync(path.join(downloads,item.filename),item.data);
  if (item.platform === 'android') fs.writeFileSync(path.join(root,'dist',item.filename),item.data);
}
fs.writeFileSync(path.join(downloads,'SHA256SUMS.txt'),artifacts.map(a=>`${sha256(a.data)}  ${a.filename}`).join('\n')+'\n');
fs.writeFileSync(path.join(downloads,'manifest.json'),JSON.stringify({versions:{windows:windows.version,android:androidElement.versionName},artifacts:artifacts.map(a=>({platform:a.platform,version:a.version,filename:a.filename,bytes:a.data.length,sha256:sha256(a.data)}))},null,2)+'\n');
console.log(`Packaged site: ${destination}. ${windows.filename} (${mib(setupBytes.length)}) + ${apkName} (${mib(apkBytes.length)}). Nothing deployed.`);
