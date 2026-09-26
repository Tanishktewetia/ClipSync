// Package real, version-matched Android + Windows artifacts. Never uploads or deploys.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const version = '0.7.0';
const destination = path.join(root, 'dist/site');
const json = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
const sha256 = data => crypto.createHash('sha256').update(data).digest('hex');
const apk = path.join(root, 'android/app/build/outputs/apk/debug/app-debug.apk');
const android = json(path.join(path.dirname(apk), 'output-metadata.json'));
if (!android.elements.some(e => e.versionName === version && e.versionCode === 8)) throw new Error('Build the matching Phase 7 APK first; refusing a stale download.');
const windows = json(path.join(root, 'dist/windows-download/download.json'));
const exeName = `ClipSync-${version}-win-x64.exe`;
if (windows.version !== version || windows.runtime !== 'win-x64' || windows.filename !== exeName || windows.selfContained !== true || windows.singleFile !== true || windows.signed !== false)
  throw new Error('Run scripts/build-windows-download.ps1 first; expected an unsigned self-contained win-x64 beta.');
const executable = fs.readFileSync(path.join(root, 'dist', exeName));
if (executable.length !== windows.bytes || sha256(executable) !== windows.sha256 || executable.toString('ascii',0,2) !== 'MZ')
  throw new Error('Windows EXE does not match its build manifest; refusing to package it.');
const pe = executable.readUInt32LE(0x3c);
if (pe > executable.length - 6 || executable.toString('ascii',pe,pe+4) !== 'PE\0\0' || executable.readUInt16LE(pe+4) !== 0x8664)
  throw new Error('Expected a valid Windows x64 PE executable.');
const artifacts = [{filename:`ClipSync-debug-${version}.apk`,data:fs.readFileSync(apk)}, {filename:exeName,data:executable}];
// Validate all artifacts before updating the previously working bundle.
fs.mkdirSync(path.join(destination, 'downloads'), {recursive:true});
for (const file of ['index.html','docs.html','style.css','app.js','mark.svg']) fs.copyFileSync(path.join(__dirname,file),path.join(destination,file));
for (const item of artifacts) {
  fs.writeFileSync(path.join(destination,'downloads',item.filename),item.data);
  if (item.filename.endsWith('.apk')) fs.writeFileSync(path.join(root,'dist',item.filename),item.data);
}
fs.writeFileSync(path.join(destination,'downloads/SHA256SUMS.txt'),artifacts.map(a=>`${sha256(a.data)}  ${a.filename}`).join('\n')+'\n');
fs.writeFileSync(path.join(destination,'downloads/manifest.json'),JSON.stringify({version,artifacts:artifacts.map(a=>({filename:a.filename,bytes:a.data.length,sha256:sha256(a.data)}))},null,2)+'\n');
console.log(`Packaged site: ${destination}. APK + self-contained EXE included. Nothing deployed.`);
