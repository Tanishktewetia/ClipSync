// Three-chapter looping product explanation. Uses synthetic text only; never accesses a clipboard.
(() => {
  const root = document.documentElement;
  const themeButton = document.querySelector('.theme-toggle');
  let saved = null;
  try { saved = localStorage.getItem('clipsync-theme'); } catch { }
  const color = matchMedia('(prefers-color-scheme: dark)');
  const applyTheme = theme => {
    root.dataset.theme = theme;
    themeButton?.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`);
    themeButton?.setAttribute('aria-pressed', String(theme === 'dark'));
  };
  applyTheme(saved === 'light' || saved === 'dark' ? saved : color.matches ? 'dark' : 'light');
  themeButton?.addEventListener('click', () => {
    saved = root.dataset.theme === 'dark' ? 'light' : 'dark'; applyTheme(saved);
    try { localStorage.setItem('clipsync-theme', saved); } catch { }
  });
  color.addEventListener('change', e => { if (!saved) applyTheme(e.matches ? 'dark' : 'light'); });
  document.querySelectorAll('.apk-download,.windows-download').forEach(link => link.addEventListener('click', () => {
    const windows = link.classList.contains('windows-download');
    document.querySelectorAll('.download-status').forEach(status => { status.textContent = windows
      ? 'Windows EXE download requested. Verify its checksum and follow Windows setup. This is an unsigned beta.'
      : 'Android APK download requested. Open it from Downloads and follow the sideloading guide.'; });
  }));
  const checksum = document.querySelector('[data-checksum]');
  if (checksum) fetch('downloads/SHA256SUMS.txt').then(r => { if (!r.ok) throw new Error('Unavailable'); return r.text(); })
    .then(text => { checksum.textContent = text.trim(); }).catch(() => { checksum.textContent = 'Checksum unavailable. Use SHA256SUMS.txt included with this build.'; });

  const demo = document.querySelector('.flow-demo');
  if (!demo || !Element.prototype.animate || !window.IntersectionObserver) return;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)');
  const pauseButton = demo.querySelector('.motion-toggle');
  const playButton = demo.querySelector('.demo-play');
  const chapters = [...demo.querySelectorAll('[data-chapter-select]')];
  const message = demo.querySelector('.demo-message');
  const title = demo.querySelector('.instruction-title');
  const detail = demo.querySelector('.instruction-detail');
  const paper = demo.querySelector('.in-transit');
  const source = demo.querySelector('.selected-text');
  const destination = demo.querySelector('.received-note');
  const zoom = demo.querySelector('.tile-zoom');
  const available = demo.querySelector('.available-send');
  const pointer = demo.querySelector('.tile-pointer');
  const labels = [...demo.querySelectorAll('.demo-timeline span')];
  const duration = 26000;
  const starts = {pc:0,tile:6500,phone:16500};
  const snapshots = {pc:5400,tile:14700,phone:24600};
  const scenes = [
    {at:0,chapter:'pc',state:'pc-copy',title:'Copy on Windows. No extra action.',detail:'PC → phone is automatic while connected and unlocked.',message:'Copy text on Windows. ClipSync handles the transfer.',labels:['Copy','Transfer','Paste'],active:0},
    {at:1800,chapter:'pc',state:'pc-transfer',title:'Straight to your phone. Automatically.',detail:'Text travels over your local network, not through a cloud clipboard.',message:'Windows → Android · encrypted over your Wi-Fi.',labels:['Copy','Transfer','Paste'],active:1},
    {at:4400,chapter:'pc',state:'pc-received',title:'Paste on Android. Keep going.',detail:'No Receive button to tap. Your text is already there.',message:'Ready on Android. That is the automatic direction.',labels:['Copy','Transfer','Paste'],active:2},
    {at:6500,chapter:'tile',state:'tile-swipe',title:'For the other direction, add a shortcut once.',detail:'Swipe down twice from the top of your phone to open Quick Settings.',message:'Tiles are the buttons beside Wi-Fi, Bluetooth and the flashlight.',labels:['Open panel','Add tile','Done'],active:0},
    {at:8500,chapter:'tile',state:'tile-edit',title:'Tap Edit or the pencil in Quick Settings.',detail:'Find Send to PC among the available tiles.',message:'One-time setup: open Edit and find the Send to PC tile.',labels:['Open panel','Add tile','Done'],active:1},
    {at:10500,chapter:'tile',state:'tile-drag',title:'Drag Send to PC into your active tiles.',detail:'Move it beside the shortcuts you use, then tap Done.',message:'Adding a tile creates the shortcut. It does not send any clipboard text.',labels:['Open panel','Add tile','Done'],active:1},
    {at:13500,chapter:'tile',state:'tile-added',title:'Done. Your Send to PC shortcut is ready.',detail:'You only add it once—not before every copy. Layout varies by phone.',message:'Alternative: use Send clipboard now in the persistent notification.',labels:['Open panel','Add tile','Done'],active:2},
    {at:16500,chapter:'phone',state:'phone-copy',title:'Copy text on Android first.',detail:'Copying alone does not send. Android requires your deliberate action.',message:'Phone → PC starts with your copy, not a silent background read.',labels:['Copy','Tap tile','Send to PC'],active:0},
    {at:18700,chapter:'phone',state:'phone-panel',title:'Open Quick Settings. Your tile is already there.',detail:'Swipe down twice, just as you would for Wi-Fi or Bluetooth.',message:'No need to add the tile again. Unlock first if asked.',labels:['Copy','Tap tile','Send to PC'],active:1},
    {at:20500,chapter:'phone',state:'phone-tap',title:'Tap Send to PC to share this copy.',detail:'That one tap lets ClipSync read the text and send it to Windows.',message:'This is the intentional send action—not an automatic phone clipboard read.',labels:['Copy','Tap tile','Send to PC'],active:1},
    {at:22200,chapter:'phone',state:'phone-transfer',title:'Now the text travels back to Windows.',detail:'Phone → PC, over the same paired, encrypted connection.',message:'Android → Windows · sent only after tapping the tile.',labels:['Copy','Tap tile','Send to PC'],active:2},
    {at:24300,chapter:'phone',state:'phone-received',title:'Paste on your PC. Both directions, explained.',detail:'PC → phone is automatic. Phone → PC is one deliberate tap.',message:'Ready on Windows. The walkthrough will repeat.',labels:['Copy','Tap tile','Send to PC'],active:2}
  ];
  let paused = false, visible = false, started = false, running = true;
  let elapsed = 0, cycle = 0, lastTime = null, frame = 0, lastScene = '';
  let demoAnimations = [], decorativeAnimations = [];
  pauseButton.hidden = false; playButton.hidden = false;
  demo.querySelector('.walkthrough-chapters').hidden = false;
  demo.dataset.cycle = '0';

  function createAnimation(element, keyframes, ms = duration) {
    const animation = element.animate(keyframes, {duration:ms,fill:'both',easing:'linear'});
    animation.pause(); animation.currentTime = 0; return animation;
  }
  const timed = frames => frames.map(([at,properties]) => ({offset:at/duration,...properties}));
  function buildDemo() {
    demoAnimations.forEach(a=>a.cancel()); demoAnimations=[];
    // Measure unanimated boxes, so replay/resizing cannot accumulate transform offsets.
    const from=source.getBoundingClientRect(), to=destination.getBoundingClientRect(), origin=paper.getBoundingClientRect();
    const center = rect => ({x:rect.left+rect.width/2,y:rect.top+rect.height/2});
    const offset = (rect,base) => ({x:center(rect).x-center(base).x,y:center(rect).y-center(base).y});
    const a=offset(from,origin),b=offset(to,origin);
    const av=available.getBoundingClientRect(),slot=demo.querySelector('.qs-slot').getBoundingClientRect();
    const drag=offset(slot,av),edit=demo.querySelector('.qs-edit').getBoundingClientRect();
    const tip=pointer.getBoundingClientRect(),editPointer=offset(edit,tip),startPointer=offset(av,tip),tapPointer=offset(slot,tip),donePointer=offset(demo.querySelector('.qs-done').getBoundingClientRect(),tip);
    const pose=(p,rotation=0,scale=1)=>`translate(${p.x}px,${p.y}px) rotate(${rotation}deg) scale(${scale})`;
    const travel=(x,y)=>`translate(${x}px,${y}px)`;
    const add=(el,keys)=>demoAnimations.push(createAnimation(el,timed(keys)));
    add(paper,[[0,{transform:pose(a,-8,.45),opacity:0}],[1300,{transform:pose(a,-8,.45),opacity:0}],[1900,{transform:pose(a,-8,.75),opacity:1,easing:'cubic-bezier(.22,1,.36,1)'}],[3100,{transform:pose({x:0,y:-15},7,1),opacity:1,easing:'cubic-bezier(.65,0,.35,1)'}],[4200,{transform:pose(b,5,.45),opacity:1}],[4500,{transform:pose(b,5,.4),opacity:0}],[21800,{transform:pose(b,5,.45),opacity:0}],[22400,{transform:pose(b,5,.7),opacity:1,easing:'cubic-bezier(.22,1,.36,1)'}],[23200,{transform:pose({x:0,y:-15},-8,1),opacity:1,easing:'cubic-bezier(.65,0,.35,1)'}],[24100,{transform:pose(a,-5,.45),opacity:1}],[24400,{transform:pose(a,-5,.4),opacity:0}],[duration,{transform:pose(a,-5,.4),opacity:0}]]);
    add(source,[[0,{backgroundColor:'#fffefa'}],[750,{backgroundColor:'#edcd79'}],[5000,{backgroundColor:'#edcd79'}],[6500,{backgroundColor:'#e7ebc6'}],[24000,{backgroundColor:'#e7ebc6'}],[24700,{backgroundColor:'#e1eccf'}],[duration,{backgroundColor:'#e1eccf'}]]);
    add(destination,[[0,{opacity:.35}],[4200,{opacity:.35}],[4800,{opacity:1}],[duration,{opacity:1}]]);
    add(demo.querySelector('.arrival-tick'),[[0,{opacity:0,transform:'scale(0)'}],[4200,{opacity:0,transform:'scale(0)'}],[4800,{opacity:1,transform:'scale(1)'}],[16000,{opacity:1,transform:'scale(1)'}],[16500,{opacity:0,transform:'scale(0)'}],[duration,{opacity:0,transform:'scale(0)'}]]);
    add(demo.querySelector('.track-signal'),[[0,{strokeDashoffset:'1',opacity:0}],[1600,{strokeDashoffset:'1',opacity:1}],[4400,{strokeDashoffset:'0',opacity:1}],[6500,{strokeDashoffset:'0',opacity:0}],[22000,{strokeDashoffset:'-1',opacity:0}],[22400,{strokeDashoffset:'-1',opacity:1}],[24300,{strokeDashoffset:'0',opacity:1}],[duration,{strokeDashoffset:'0',opacity:.5}]]);
    add(zoom,[[0,{opacity:0,visibility:'hidden',transform:'translateY(30px) scale(.8)'}],[7700,{opacity:0,visibility:'hidden',transform:'translateY(30px) scale(.8)'}],[8250,{opacity:1,visibility:'visible',transform:'translateY(0) scale(1)'}],[15100,{opacity:1,visibility:'visible',transform:'translateY(0) scale(1)'}],[16000,{opacity:0,visibility:'hidden',transform:'translateY(-14px) scale(.96)'}],[19300,{opacity:0,visibility:'hidden',transform:'translateY(30px) scale(.8)'}],[19900,{opacity:1,visibility:'visible',transform:'translateY(0) scale(1)'}],[21500,{opacity:1,visibility:'visible',transform:'translateY(0) scale(1)'}],[22300,{opacity:0,visibility:'hidden',transform:'translateY(-14px) scale(.96)'}],[duration,{opacity:0,visibility:'hidden',transform:'translateY(-14px) scale(.96)'}]]);
    add(available,[[0,{opacity:0,transform:'translate(0,0)'}],[9500,{opacity:0,transform:'translate(0,0)'}],[10000,{opacity:1,transform:'translate(0,0)'}],[11100,{opacity:1,transform:'translate(0,0)',easing:'cubic-bezier(.65,0,.35,1)'}],[12800,{opacity:1,transform:travel(drag.x,drag.y)}],[13200,{opacity:0,transform:travel(drag.x,drag.y)}],[duration,{opacity:0,transform:travel(drag.x,drag.y)}]]);
    add(demo.querySelector('.active-send'),[[0,{opacity:0}],[12900,{opacity:0}],[13400,{opacity:1}],[duration,{opacity:1}]]);
    add(demo.querySelector('.qs-empty'),[[0,{opacity:1}],[12000,{opacity:1}],[13000,{opacity:0}],[duration,{opacity:0}]]);
    add(demo.querySelector('.qs-done'),[[0,{opacity:0}],[13200,{opacity:0}],[13900,{opacity:1}],[15900,{opacity:1}],[16500,{opacity:0}],[duration,{opacity:0}]]);
    add(demo.querySelector('.qs-available-label'),[[0,{opacity:0}],[9400,{opacity:0}],[9800,{opacity:1}],[13000,{opacity:1}],[13700,{opacity:0}],[duration,{opacity:0}]]);
    add(demo.querySelector('.qs-edit'),[[0,{backgroundColor:'transparent'}],[8800,{backgroundColor:'transparent'}],[9300,{backgroundColor:'#edcd79'}],[9700,{backgroundColor:'transparent'}],[duration,{backgroundColor:'transparent'}]]);
    add(pointer,[[0,{opacity:0,transform:pose(editPointer)}],[8550,{opacity:0,transform:pose(editPointer)}],[9000,{opacity:1,transform:pose(editPointer)}],[9600,{opacity:1,transform:pose(editPointer,.0,.8)}],[10300,{opacity:1,transform:pose(startPointer)}],[11100,{opacity:1,transform:pose(startPointer),easing:'cubic-bezier(.65,0,.35,1)'}],[12800,{opacity:1,transform:pose(tapPointer)}],[13400,{opacity:1,transform:pose(tapPointer)}],[14200,{opacity:1,transform:pose(donePointer)}],[14600,{opacity:1,transform:pose(donePointer,0,.7)}],[15100,{opacity:0,transform:pose(donePointer)}],[20100,{opacity:0,transform:pose({x:tapPointer.x+25,y:tapPointer.y+25})}],[20600,{opacity:1,transform:pose(tapPointer)}],[21000,{opacity:1,transform:pose(tapPointer,0,.7)}],[21500,{opacity:0,transform:pose(tapPointer)}],[duration,{opacity:0,transform:pose(tapPointer)}]]);
    add(demo.querySelector('.tap-ripple'),[[0,{opacity:0,transform:'scale(0)'}],[20600,{opacity:0,transform:'scale(0)'}],[20800,{opacity:1,transform:'scale(1)'}],[21500,{opacity:0,transform:'scale(4)'}],[duration,{opacity:0,transform:'scale(4)'}]]);
    add(demo.querySelector('.phone-copy-menu'),[[0,{opacity:0,transform:'translateY(5px)'}],[16900,{opacity:0,transform:'translateY(5px)'}],[17400,{opacity:1,transform:'translateY(0)'}],[18100,{opacity:1,transform:'translateY(0)'}],[18500,{opacity:0,transform:'translateY(-5px)'}],[duration,{opacity:0,transform:'translateY(-5px)'}]]);
    add(demo.querySelector('.phone-swipe'),[[0,{opacity:0,transform:'translateY(-10px)'}],[6500,{opacity:0,transform:'translateY(-10px)'}],[7200,{opacity:1,transform:'translateY(10px)'}],[7900,{opacity:0,transform:'translateY(25px)'}],[18650,{opacity:0,transform:'translateY(-10px)'}],[19100,{opacity:1,transform:'translateY(10px)'}],[19800,{opacity:0,transform:'translateY(25px)'}],[duration,{opacity:0,transform:'translateY(25px)'}]]);
    // Dim the underlying devices only while the explanatory Quick Settings close-up is open.
    for(const device of demo.querySelectorAll('.flow-device')) add(device,[[0,{opacity:1}],[7800,{opacity:1}],[8300,{opacity:.35}],[15100,{opacity:.35}],[16100,{opacity:1}],[19400,{opacity:1}],[19900,{opacity:.35}],[21500,{opacity:.35}],[22400,{opacity:1}],[duration,{opacity:1}]]);
    renderDemo();
  }
  function renderDemo() {
    demoAnimations.forEach(a=>{a.currentTime=elapsed;});
    demo.dataset.progress=(elapsed/duration).toFixed(4);
    demo.dataset.cycle=String(cycle);
    const scene=scenes.findLast(item=>elapsed>=item.at)||scenes[0];
    if(lastScene===scene.state)return;
    lastScene=scene.state;demo.dataset.state=scene.state;demo.dataset.chapter=scene.chapter;
    title.textContent=scene.title;detail.textContent=scene.detail;message.textContent=scene.message;
    chapters.forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.chapterSelect===scene.chapter)));
    labels.forEach((label,i)=>{label.textContent=scene.labels[i];label.classList.toggle('timeline-active',i===scene.active);});
    const phoneMode=scene.chapter==='phone';
    demo.querySelector('.destination-greeting').innerHTML=phoneMode?'A thought from<br>your phone.':'Right where<br>you need it.';
    demo.querySelector('.phone-sample').textContent=phoneMode?'Meet at 10?':'Keep that thought.';
    demo.querySelector('.received-label').textContent=phoneMode?'Copied on Android':'Ready to paste';
    source.textContent=scene.state==='phone-received'?'Meet at 10?':'Keep that thought.';
    demo.querySelector('.note-caption').textContent=scene.state==='phone-received'?'Received from your phone':'On your Windows PC';
    demo.querySelector('.pc-key').textContent=scene.state==='phone-received'?'V':'C';
    demo.querySelector('.phone-footnote').innerHTML=phoneMode?'Send with one tile tap<br><span>Not sent by copying alone</span>':'From Windows<br><span>Encrypted on your network</span>';
    demo.querySelector('.qs-hint').textContent=scene.chapter==='phone'?'Tap your shortcut to send this copied text':'Your shortcuts beside Wi-Fi and Bluetooth';
  }
  function tick(time) {
    frame=0;
    if(!running||paused||reduce.matches||!visible||document.hidden){lastTime=null;return;}
    if(lastTime!==null){elapsed+=Math.min(time-lastTime,80);if(elapsed>=duration){elapsed%=duration;cycle++;lastScene='';}}
    lastTime=time;renderDemo();frame=requestAnimationFrame(tick);
  }
  function wake(){if(!frame&&running&&visible&&!paused&&!reduce.matches&&!document.hidden)frame=requestAnimationFrame(tick);}
  function stopFrames(){cancelAnimationFrame(frame);frame=0;lastTime=null;}
  function seek(chapter,staticState=false){
    started=true;elapsed=staticState?snapshots[chapter]:starts[chapter];lastTime=null;lastScene='';
    if(!demoAnimations.length)buildDemo();else renderDemo();
    wake();
  }
  chapters.forEach(button=>button.addEventListener('click',()=>seek(button.dataset.chapterSelect,reduce.matches||paused)));
  playButton.addEventListener('click',()=>seek('pc',reduce.matches||paused));
  const observer=new IntersectionObserver(entries=>{
    const entry=entries[0];visible=entry.isIntersecting;
    if(visible&&!started)seek('pc',reduce.matches);else if(visible)wake();else stopFrames();
  },{threshold:0});
  // Observe the graphic, not the whole tall walkthrough: mobile must not freeze mid-scene.
  observer.observe(demo.querySelector('.flow-canvas'));
  document.addEventListener('visibilitychange',()=>{if(document.hidden)stopFrames();else wake();});

  const story=document.querySelector('.scroll-story');
  const steps=[...document.querySelectorAll('.story-steps article')];
  const wire=story?.querySelector('.story-wire span');
  let wireAnimation=wire?createAnimation(wire,[{transform:'scaleX(.08)'},{transform:'scaleX(1)'}],1000):null;
  let scrollFrame=0;
  function renderScroll(){
    scrollFrame=0;if(!story||!steps.length)return;
    if(reduce.matches||paused){wireAnimation?.cancel();wireAnimation=null;story.removeAttribute('data-active-step');steps.forEach(step=>step.classList.remove('is-current'));story.querySelector('.story-caption').textContent='Find. Pair. Keep going.';return;}
    if(!wireAnimation&&wire)wireAnimation=createAnimation(wire,[{transform:'scaleX(.08)'},{transform:'scaleX(1)'}],1000);
    const rect=story.getBoundingClientRect(),progress=Math.max(0,Math.min(1,(innerHeight*.65-rect.top)/(rect.height-innerHeight*.3)));
    if(wireAnimation)wireAnimation.currentTime=progress*1000;
    let active=0;steps.forEach((step,index)=>{if(step.getBoundingClientRect().top<innerHeight*.62)active=index;});
    steps.forEach((step,index)=>step.classList.toggle('is-current',index===active));story.dataset.activeStep=String(active);
    story.querySelector('.story-caption').textContent=['Find your devices','Compare all six digits','Connected. Carry on.'][active];
  }
  const queueScroll=()=>{if(!scrollFrame)scrollFrame=requestAnimationFrame(renderScroll);};
  window.addEventListener('scroll',queueScroll,{passive:true});
  let resizeTimer;
  window.addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(demoAnimations.length)buildDemo();queueScroll();},120);});
  const reveal=new IntersectionObserver(entries=>entries.forEach(entry=>{
    if(!entry.isIntersecting)return;reveal.unobserve(entry.target);if(reduce.matches||paused)return;
    const animation=entry.target.animate([{opacity:.35,transform:'translateY(12px)'},{opacity:1,transform:'translateY(0)'}],{duration:500,easing:'cubic-bezier(.22,1,.36,1)'});
    decorativeAnimations.push(animation);animation.finished.then(()=>{decorativeAnimations=decorativeAnimations.filter(a=>a!==animation);}).catch(()=>{});
  }),{threshold:.3});
  document.querySelectorAll('.section-heading h2,.download-section h2,.faq h2').forEach(element=>reveal.observe(element));
  pauseButton.addEventListener('click',()=>{
    paused=!paused;pauseButton.setAttribute('aria-pressed',String(paused));pauseButton.textContent=paused?'Resume motion':'Pause motion';demo.dataset.motion=paused?'paused':'enabled';
    decorativeAnimations.forEach(a=>a.cancel());decorativeAnimations=[];stopFrames();renderScroll();if(!paused)wake();
  });
  function motionPreference(){
    decorativeAnimations.forEach(a=>a.cancel());decorativeAnimations=[];pauseButton.hidden=reduce.matches;
    playButton.textContent=reduce.matches?'Show PC → phone':'Restart walkthrough';
    if(reduce.matches){stopFrames();seek(demo.dataset.chapter||'pc',true);}
    else if(!started&&visible)seek('pc');else wake();
    renderScroll();
  }
  reduce.addEventListener('change',motionPreference);motionPreference();
})();
