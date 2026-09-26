// Existing Playwright only: set PLAYWRIGHT_MODULE and optionally CHROME_PATH. No site dependency.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const base=process.env.SITE_URL||'http://127.0.0.1:4177';
const output=path.resolve(__dirname,'../dist/phase7-verification/bidirectional');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 const results=[],errors=[];
 try{
  for(const [name,width,height,theme] of [['desktop',1440,1100,'light'],['dark',1440,1100,'dark'],['mobile',390,844,'light'],['small',320,780,'dark'],['tablet',768,1024,'light'],['landscape',844,390,'dark']]){
   const context=await browser.newContext({viewport:{width,height},colorScheme:theme,reducedMotion:'no-preference'});
   const page=await context.newPage();page.on('pageerror',e=>errors.push(name+': '+e.message));
   await page.goto(base,{waitUntil:'networkidle'});
   const demo=page.locator('.flow-demo'),canvas=page.locator('.flow-canvas');
   const select=async chapter=>{await page.locator(`[data-chapter-select="${chapter}"]`).click();await canvas.scrollIntoViewIfNeeded();};
   const wait=state=>page.waitForFunction(s=>document.querySelector('.flow-demo').dataset.state===s,state,{timeout:20000});
   await select('pc');await wait('pc-transfer');
   await page.locator('.motion-toggle').click();const progress=await demo.getAttribute('data-progress');await page.waitForTimeout(180);assert.equal(await demo.getAttribute('data-progress'),progress);
   await canvas.screenshot({path:path.join(output,`${name}-pc-to-phone.png`)});
   await page.locator('.motion-toggle').click();await canvas.scrollIntoViewIfNeeded();await wait('pc-received');
   assert.match(await page.locator('.instruction-title').innerText(),/Paste on Android/);
   await select('tile');await wait('tile-edit');
   assert.match(await page.locator('.instruction-detail').innerText(),/available tiles/);
   await wait('tile-drag');await page.waitForTimeout(1500);
   await page.locator('.motion-toggle').click();
   await demo.screenshot({path:path.join(output,`${name}-add-tile.png`)});
   const activeOpacity=await page.locator('.active-send').evaluate(e=>Number(getComputedStyle(e).opacity));
   // Resume, finish adding once, and show the now-active Send to PC shortcut.
   await page.locator('.motion-toggle').click();await canvas.scrollIntoViewIfNeeded();await wait('tile-added');
   assert.match(await page.locator('.instruction-detail').innerText(),/only add it once/);
   await select('phone');await wait('phone-copy');
   assert.match(await page.locator('.instruction-detail').innerText(),/Copying alone does not send/);
   await wait('phone-tap');await page.waitForTimeout(300);
   await page.locator('.motion-toggle').click();await demo.screenshot({path:path.join(output,`${name}-tap-to-send.png`)});
   assert.ok(await page.locator('.active-send').evaluate(e=>Number(getComputedStyle(e).opacity))>.9);
   await page.locator('.motion-toggle').click();await canvas.scrollIntoViewIfNeeded();await wait('phone-transfer');await page.waitForTimeout(900);
   await page.locator('.motion-toggle').click();await canvas.screenshot({path:path.join(output,`${name}-phone-to-pc.png`)});
   await page.locator('.motion-toggle').click();await canvas.scrollIntoViewIfNeeded();await wait('phone-received');
   assert.equal(await page.locator('.selected-text').innerText(),'Meet at 10?');
   assert.equal(await page.locator('.pc-arrival').isVisible(),true);
   const cycle=Number(await demo.getAttribute('data-cycle'));
   await page.waitForFunction(before=>Number(document.querySelector('.flow-demo').dataset.cycle)>before,cycle,{timeout:6000});
   assert.equal(await demo.getAttribute('data-chapter'),'pc');
   // Loop pauses offscreen and resumes only when visible.
   await page.locator('.footer').evaluate(e=>e.scrollIntoView({behavior:'instant'}));await page.waitForTimeout(180);
   const offscreen=await demo.getAttribute('data-progress');await page.waitForTimeout(220);assert.equal(await demo.getAttribute('data-progress'),offscreen);
   await page.emulateMedia({reducedMotion:'reduce'});await select('tile');
   assert.equal(await page.locator('.motion-toggle').isVisible(),false);
   assert.equal(await demo.getAttribute('data-state'),'tile-added');
   assert.equal(await page.evaluate(()=>document.getAnimations().filter(a=>a.playState==='running').length),0);
   await select('phone');assert.equal(await demo.getAttribute('data-state'),'phone-received');
   const staticProgress=await demo.getAttribute('data-progress');await page.waitForTimeout(150);assert.equal(await demo.getAttribute('data-progress'),staticProgress);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   await page.locator('#download').scrollIntoViewIfNeeded();await page.locator('#download').screenshot({path:path.join(output,`${name}-downloads.png`)});
   assert.equal(await page.locator('.windows-download').isVisible(),true);assert.equal(await page.locator('.apk-download').isVisible(),true);
   await select('pc');await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.screenshot({path:path.join(output,`${name}-hero.png`)});
   results.push({name,width,pcToPhone:true,tileEditAndDrag:true,explicitTileTap:true,phoneToPc:true,loops:true,pause:true,offscreenSuspension:true,reducedMotionChapterSelection:true,noOverflow:true});
   await context.close();
  }
  const context=await browser.newContext({javaScriptEnabled:false,viewport:{width:390,height:844}});const page=await context.newPage();await page.goto(base);
  assert.equal(await page.locator('h1').isVisible(),true);assert.equal(await page.locator('.walkthrough-chapters').isVisible(),false);
  assert.match(await page.locator('.tile-explainer').innerText(),/Edit \/ pencil/);
  assert.equal(await page.locator('.windows-download').count(),1);assert.equal(await page.locator('.apk-download').count(),1);
  await context.close();assert.equal(errors.length,0);
  const report={results,noJavaScriptFallback:true,errors};fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
 }finally{await browser.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
