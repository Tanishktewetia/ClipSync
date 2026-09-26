// Localhost by default. Pass --lan only for an intentional private Wi-Fi sideload session.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = fs.realpathSync(path.resolve(__dirname, '../dist/site'));
const port = Number(process.env.PORT || 4177);
const host = process.argv.includes('--lan') ? '0.0.0.0' : '127.0.0.1';
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.apk': 'application/vnd.android.package-archive', '.exe': 'application/octet-stream', '.json': 'application/json; charset=utf-8', '.txt': 'text/plain; charset=utf-8' };
http.createServer((req, res) => {
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405, { Allow: 'GET, HEAD' }); res.end(); return; }
  try {
    const url = new URL(req.url, 'http://localhost');
    const rel = decodeURIComponent(url.pathname).replace(/^\/+/, '') || 'index.html';
    const candidate = path.resolve(root, rel);
    if (!candidate.startsWith(root + path.sep)) { res.writeHead(403); res.end(); return; }
    const file = fs.realpathSync(candidate);
    if (!file.startsWith(root + path.sep) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
    res.setHeader('Content-Length', fs.statSync(file).size);
    if (file.endsWith('.apk') || file.endsWith('.exe')) res.setHeader('Content-Disposition', 'attachment; filename="' + path.basename(file) + '"');
    if (req.method === 'HEAD') { res.end(); return; }
    const stream = fs.createReadStream(file); stream.on('error', () => res.destroy()); stream.pipe(res);
  } catch { res.writeHead(404); res.end('Not found'); }
}).listen(port, host, () => console.log(`ClipSync preview: http://${host}:${port} — serving only ${root}`));
