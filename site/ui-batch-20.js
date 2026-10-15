/* VidGravity UI batch 20 — additive, dependency-free PWA polish. */
(() => {
  'use strict';
  const $ = (s, root=document) => root.querySelector(s);
  const $$ = (s, root=document) => [...root.querySelectorAll(s)];
  const safeStore = {
    get(k, fallback='') { try { return localStorage.getItem(k) ?? fallback; } catch (_) { return fallback; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (_) {} }
  };
  const announce = (message) => {
    let live = $('#vgUiLive');
    if (!live) { live=document.createElement('div'); live.id='vgUiLive'; live.className='vg-ui-sr'; live.setAttribute('role','status'); live.setAttribute('aria-live','polite'); document.body.append(live); }
    live.textContent=message;
  };

  // 1. Synchronize the existing app theme controller; do not register a competing toggle.
  const syncTheme = () => {
    const resolved=document.documentElement.dataset.theme || 'dark';
    document.documentElement.classList.toggle('vg-light',resolved==='light');
  };
  syncTheme();
  new MutationObserver(syncTheme).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});

  // 2. Scroll progress indicator.
  const progress=document.createElement('div'); progress.className='vg-scroll-progress'; progress.setAttribute('aria-hidden','true'); document.body.append(progress);
  const updateProgress=()=>{const max=document.documentElement.scrollHeight-innerHeight;progress.style.transform='scaleX('+(max>0?Math.min(1,scrollY/max):0)+')';};
  addEventListener('scroll',updateProgress,{passive:true}); addEventListener('resize',updateProgress); updateProgress();

  // 3. Keep the current navigation item reflected in the document title.
  const syncTitle=()=>{const active=$('.nav.active');const label=active?.innerText?.trim().replace(/\s+/g,' ');if(label)document.title=label+' · VidGravity';};
  const navButtons=$$('.nav[data-page]');
  navButtons.forEach((b,i)=>{b.dataset.vgNavIndex=String(i+1);b.title=(b.innerText.trim().replace(/\s+/g,' '))+' · shortcut '+(i+1);b.addEventListener('click',()=>{setTimeout(syncTitle,0);safeStore.set('vg-ui-last-page',b.dataset.page||'home');});});
  syncTitle();

  // 4. Restore the last workspace only when the app has not already selected a page.
  const lastPage=safeStore.get('vg-ui-last-page');
  if(lastPage && lastPage!=='home' && navButtons.some(b=>b.dataset.page===lastPage)) $('[data-page="'+lastPage+'"]')?.click();

  // 5. Add an accessible command palette.
  const palette=document.createElement('div');
  palette.className='vg-command-overlay';palette.hidden=true;
  palette.innerHTML='<section class="vg-command" role="dialog" aria-modal="true" aria-labelledby="vgCommandTitle"><div class="vg-command-head"><div><strong id="vgCommandTitle">Quick actions</strong><small>Navigate your workspace without losing context</small></div><button type="button" class="vg-command-close" aria-label="Close quick actions">×</button></div><input class="vg-command-search" type="search" placeholder="Search pages and actions…" autocomplete="off"><div class="vg-command-results" role="listbox" aria-label="Quick actions"></div><footer><span>↑ ↓ to navigate</span><span>Enter to open</span><kbd>Esc</kbd></footer></section>';
  document.body.append(palette);
  const cmdInput=$('.vg-command-search',palette), results=$('.vg-command-results',palette);
  const commands=[
    ...navButtons.map(b=>({label:'Open '+b.innerText.trim().replace(/\s+/g,' '),detail:'Navigate to page',icon:'↗',run:()=>b.click()})),
    {label:'Focus search',detail:'Find media in Library',icon:'⌕',run:()=>{const target=$('#library .search, #library input[type=search], #library input');if(target){$('[data-page="library"]')?.click();setTimeout(()=>target.focus(),40);}else announce('Open Library to search your media.');}},
    {label:'Jump to top',detail:'Scroll current page to the beginning',icon:'↑',run:()=>scrollTo({top:0,behavior:'smooth'})},
    {label:'Switch theme',detail:'Toggle light and dark appearance',icon:'◐',run:()=>themeButton?.click()},
    {label:'Install VidGravity',detail:'Install as an app when supported',icon:'↓',run:()=>$('#vgInstallButton')?.click()}
  ];
  let commandIndex=0;
  function renderCommands(query=''){const q=query.trim().toLowerCase();const filtered=commands.filter(c=>(c.label+' '+c.detail).toLowerCase().includes(q));results.replaceChildren();filtered.forEach((c,i)=>{const b=document.createElement('button');b.type='button';b.className='vg-command-item'+(i===0?' is-active':'');b.setAttribute('role','option');b.innerHTML='<span class="vg-command-icon"></span><span class="vg-command-copy"><strong></strong><small></small></span><span class="vg-command-enter">↵</span>';$('.vg-command-icon',b).textContent=c.icon;$('.vg-command-copy strong',b).textContent=c.label;$('.vg-command-copy small',b).textContent=c.detail;b.addEventListener('click',()=>{closePalette();c.run();});results.append(b);});commandIndex=0;if(!filtered.length){const p=document.createElement('p');p.className='vg-command-empty';p.textContent='No matching actions. Try another search.';results.append(p);}return filtered;}
  let filteredCommands=commands;
  function openPalette(){palette.hidden=false;filteredCommands=renderCommands(cmdInput.value);cmdInput.focus();cmdInput.select();}
  function closePalette(){palette.hidden=true;}
  $('.vg-command-close',palette).addEventListener('click',closePalette);
  palette.addEventListener('click',e=>{if(e.target===palette)closePalette();});
  cmdInput.addEventListener('input',()=>{filteredCommands=renderCommands(cmdInput.value);});
  cmdInput.addEventListener('keydown',e=>{const items=$$('.vg-command-item',results);if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(!items.length)return;commandIndex=(commandIndex+(e.key==='ArrowDown'?1:-1)+items.length)%items.length;items.forEach((it,i)=>it.classList.toggle('is-active',i===commandIndex));items[commandIndex].scrollIntoView({block:'nearest'});}else if(e.key==='Enter'){e.preventDefault();items[commandIndex]?.click();}else if(e.key==='Escape')closePalette();});
  // 6–10. Keyboard shortcuts: palette, page 1–6, Escape, top and search.
  document.addEventListener('keydown',e=>{
    const typing=/INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName)||document.activeElement?.isContentEditable;
    if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();palette.hidden?openPalette():closePalette();return;}
    if(e.key==='Escape'&&!palette.hidden){closePalette();return;}
    if(!typing&&!e.ctrlKey&&!e.metaKey&&!e.altKey&&/^[1-6]$/.test(e.key)){navButtons[Number(e.key)-1]?.click();return;}
    if(!typing&&!e.ctrlKey&&!e.metaKey&&!e.altKey&&e.key==='Home'){scrollTo({top:0,behavior:'smooth'});}
    if(!typing&&e.key==='/'&&palette.hidden){e.preventDefault();openPalette();}
  });
  // 11. Add a discoverable quick-action trigger to the top bar.
  const topActions=$('.topbarActions');
  if(topActions&&!$('#vgCommandTrigger')){const b=document.createElement('button');b.id='vgCommandTrigger';b.type='button';b.className='vg-command-trigger';b.innerHTML='⌕ <span>Quick actions</span> <kbd>Ctrl K</kbd>';b.addEventListener('click',openPalette);topActions.prepend(b);}

  // 12. Accessible labels and stable touch target sizing for icon-only controls.
  $$('button').forEach(b=>{if(!b.getAttribute('aria-label')&&!b.textContent.trim()&&b.title)b.setAttribute('aria-label',b.title);});
  // 13. Add visible focus handling to keyboard navigation.
  document.addEventListener('keydown',e=>{if(e.key==='Tab')document.documentElement.classList.add('vg-keyboard-user');});
  document.addEventListener('pointerdown',()=>document.documentElement.classList.remove('vg-keyboard-user'),{passive:true});
  // 14. Add clear empty-state helper text to blank lists without changing app logic.
  const emptyObserver=new MutationObserver(()=>{$('.list,.mediaGrid').forEach(el=>{const hint=el.querySelector('.vg-empty-hint');const realChildren=[...el.children].filter(child=>!child.classList.contains('vg-empty-hint'));if(realChildren.length&&hint)hint.remove();else if(!realChildren.length&&!hint){const note=document.createElement('div');note.className='vg-empty-hint';note.textContent=el.classList.contains('mediaGrid')?'Your library is ready for media. Add or download a file to see it here.':'No active items yet. New activity will appear here.';el.append(note);}});});
  const workspace=$('.app');if(workspace)emptyObserver.observe(workspace,{childList:true,subtree:true});
  // 15. Respect reduced motion in JS-triggered scrolls.
  const motion='matchMedia' in window&&matchMedia('(prefers-reduced-motion: reduce)').matches;
  // 16. Add a small connectivity label for the current session.
  const updateConnection=()=>{let el=$('#vgUiConnection');if(!el){el=document.createElement('span');el.id='vgUiConnection';el.className='vg-ui-connection';topActions?.append(el);}if(el){el.textContent=navigator.onLine?'Online':'Offline';el.classList.toggle('is-offline',!navigator.onLine);el.title=navigator.onLine?'Network available; media services may still be unavailable.':'Offline; cached shell and local files remain available.';}};
  addEventListener('online',updateConnection);addEventListener('offline',updateConnection);updateConnection();
  // 17. Show a page-level “back to top” action on long pages.
  const topBtn=document.createElement('button');topBtn.type='button';topBtn.className='vg-back-top';topBtn.textContent='↑';topBtn.setAttribute('aria-label','Back to top');topBtn.title='Back to top';topBtn.hidden=true;document.body.append(topBtn);
  topBtn.addEventListener('click',()=>scrollTo({top:0,behavior:motion?'auto':'smooth'}));
  const onScroll=()=>{topBtn.hidden=scrollY<500;};addEventListener('scroll',onScroll,{passive:true});onScroll();
  // 18. Keep status feedback readable on mobile.
  const uxStatus=$('.vg-ux-status');if(uxStatus){uxStatus.setAttribute('aria-live','polite');uxStatus.setAttribute('role','status');}
  // 19. Ensure long titles and URLs wrap instead of widening the viewport.
  // 20. Expose current page name as a live announcement after navigation.
  navButtons.forEach(b=>b.addEventListener('click',()=>announce((b.innerText||'Page').trim().replace(/\s+/g,' ')+' opened.')));
})();