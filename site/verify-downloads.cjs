// Download verification only: never launches the downloaded Windows executable.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const base=process.env.SITE_URL||'http://127.0.0.1:4177';
const output=path.resolve(__dirname,'../dist/phase7-verification/downloads');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const links=[];
  for(const file of ['index.html','docs.html']){
   await page.goto(`${base}/${file}`,{waitUntil:'networkidle'});
   const hrefs=await page.locator('a[href]').evaluateAll(as=>as.map(a=>a.getAttribute('href')));
   for(const href of new Set(hrefs)){
    if(/^https?:/.test(href))continue;
    const target=new URL(href,`${base}/${file}`);
    const response=await page.request.head(target.href);assert.equal(response.status(),200,href);
    if(target.hash){const html=await(await page.request.get(target.href)).text();assert.ok(html.includes(`id="${target.hash.slice(1)}"`),href);}
    links.push(href);
   }
  }
  const sums=await(await page.request.get(`${base}/downloads/SHA256SUMS.txt`)).text();
  const manifest=await(await page.request.get(`${base}/downloads/manifest.json`)).json();
  assert.equal(manifest.artifacts.length,2);
  await page.goto(`${base}/#download`);
  const downloads=[];
  for(const [selector,filename,type] of [
   ['.windows-download','ClipSync-0.7.0-win-x64.exe','application/octet-stream'],
   ['.apk-download','ClipSync-debug-0.7.0.apk','application/vnd.android.package-archive']]){
   const event=page.waitForEvent('download');await page.locator(selector).click();const download=await event;
   assert.equal(download.suggestedFilename(),filename);const saved=path.join(output,filename);await download.saveAs(saved);
   const bytes=fs.readFileSync(saved),hash=crypto.createHash('sha256').update(bytes).digest('hex');
   assert.ok(sums.includes(`${hash}  ${filename}`));
   const record=manifest.artifacts.find(a=>a.filename===filename);assert.equal(record.sha256,hash);assert.equal(record.bytes,bytes.length);
   const response=await page.request.head(`${base}/downloads/${filename}`);
   assert.equal(response.headers()['content-type'],type);assert.match(response.headers()['content-disposition'],/attachment/);
   if(filename.endsWith('.exe')){assert.equal(bytes.toString('ascii',0,2),'MZ');const pe=bytes.readUInt32LE(0x3c);assert.equal(bytes.toString('ascii',pe,pe+4),'PE\0\0');assert.equal(bytes.readUInt16LE(pe+4),0x8664);}
   else assert.equal(bytes.subarray(0,2).toString('ascii'),'PK');
   downloads.push({filename,bytes:bytes.length,sha256:hash,mime:type,attachment:true});
  }
  const variants=[];
  for(const [width,theme] of [[1440,'light'],[1440,'dark'],[390,'light'],[390,'dark'],[320,'light']]){
   await page.setViewportSize({width,height:900});await page.emulateMedia({colorScheme:theme});
   for(const file of ['index.html','docs.html']){
    await page.goto(`${base}/${file}`,{waitUntil:'networkidle'});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    if(file==='index.html'){
     await page.locator('.faq-list summary').first().click();assert.notEqual(await page.locator('.faq-list details').first().getAttribute('open'),null);
     await page.locator('#download').scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,`${width}-${theme}-download-viewport.png`)});
    }else await page.screenshot({path:path.join(output,`${width}-${theme}-docs.png`)});
   }
   variants.push({width,theme,noOverflow:true});
  }
  await page.goto(base);await page.locator('.theme-toggle').click();const theme=await page.locator('html').getAttribute('data-theme');await page.reload();assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
  const traversal=await page.request.get(`${base}/%2e%2e%5cRULES.md`);assert.notEqual(traversal.status(),200);
  assert.equal(errors.length,0);
  const report={internalLinks:links.length,downloads,variants,themePersistence:true,pathTraversalBlocked:true,errors};
  fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
 }finally{await browser.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
