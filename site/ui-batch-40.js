/* VidGravity UI batch 21–40: practical page-level workflow refinements. */
(() => {
 'use strict';
 const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
 const store={get(k,d=''){try{return localStorage.getItem(k)??d}catch{return d}},set(k,v){try{localStorage.setItem(k,v)}catch{}}};
 const button=(label,cls='smallBtn')=>{const b=document.createElement('button');b.type='button';b.className=cls;b.textContent=label;return b};
 const note=(target,text)=>{if(target)target.textContent=text};
 const announce=text=>{let el=$('#vgUiBatch2Live');if(!el){el=document.createElement('div');el.id='vgUiBatch2Live';el.className='vg-ui-sr';el.setAttribute('role','status');el.setAttribute('aria-live','polite');document.body.append(el)}el.textContent=text};

 // 21. Add a one-click clear control to Library search.
 const search=$('#librarySearch');
 if(search&&!$('#vgClearLibrarySearch')){const clear=button('Clear','vg-ui-secondary');clear.id='vgClearLibrarySearch';clear.setAttribute('aria-label','Clear library search');search.insertAdjacentElement('afterend',clear);clear.addEventListener('click',()=>{search.value='';search.dispatchEvent(new Event('input',{bubbles:true}));search.focus();});search.addEventListener('input',()=>{clear.hidden=!search.value;});clear.hidden=!search.value;}

 // 22. Add visible Library result count, updated as the app renders/filtering changes.
 const grid=$('#libraryGrid');
 if(grid&&!$('#vgLibraryCount')){const count=document.createElement('div');count.id='vgLibraryCount';count.className='vg-ui-count';count.setAttribute('aria-live','polite');grid.insertAdjacentElement('beforebegin',count);
 const updateCount=()=>{const cards=$$('.media',grid).filter(el=>getComputedStyle(el).display!=='none');count.textContent=cards.length+' visible item'+(cards.length===1?'':'s');};
 new MutationObserver(updateCount).observe(grid,{childList:true,subtree:true,attributes:true,attributeFilter:['style','class']});search?.addEventListener('input',()=>setTimeout(updateCount,0));$('#libraryFilter')?.addEventListener('change',()=>setTimeout(updateCount,0));$('#libraryTag')?.addEventListener('input',()=>setTimeout(updateCount,0));updateCount();}

 // 23. Add an optional list view for Library cards.
 if(grid&&!$('#vgLibraryViewToggle')){const bar=$('#library .libraryTools');if(bar){const toggle=button(store.get('vg-ui-library-view','grid')==='list'?'List view: On':'List view');toggle.id='vgLibraryViewToggle';toggle.setAttribute('aria-pressed',String(store.get('vg-ui-library-view','grid')==='list'));bar.append(toggle);const apply=()=>{const list=store.get('vg-ui-library-view','grid')==='list';grid.classList.toggle('vg-library-list-view',list);toggle.textContent=list?'List view: On':'List view';toggle.setAttribute('aria-pressed',String(list));};toggle.addEventListener('click',()=>{store.set('vg-ui-library-view',store.get('vg-ui-library-view','grid')==='list'?'grid':'list');apply();});apply();}}

 // 24. Add library sort options without changing the underlying stored media.
 if(grid&&!$('#vgLibrarySort')){const bar=$('#library .libraryTools');if(bar){const sort=document.createElement('select');sort.id='vgLibrarySort';sort.className='vg-ui-select';sort.setAttribute('aria-label','Sort visible library items');sort.innerHTML='<option value="default">Default order</option><option value="name">Name A–Z</option><option value="name-desc">Name Z–A</option>';bar.append(sort);sort.addEventListener('change',()=>{const cards=$$('.media',grid);if(sort.value==='default'){announce('Default library order selected. Reload the Library to restore its original order if needed.');return;}cards.sort((a,b)=>{const an=(a.querySelector('.mediaTitle')?.textContent||'').trim().toLocaleLowerCase();const bn=(b.querySelector('.mediaTitle')?.textContent||'').trim().toLocaleLowerCase();return sort.value==='name'?an.localeCompare(bn):bn.localeCompare(an)}).forEach(el=>grid.append(el));announce('Library sorted by '+(sort.value==='name'?'name A to Z':'name Z to A'));});}}

 // 25. Make Library filter/search state survive page changes and reloads.
 [['librarySearch','vg-ui-library-search'],['libraryTag','vg-ui-library-tag'],['libraryFilter','vg-ui-library-filter']].forEach(([id,key])=>{const el=$('#'+id);if(!el)return;const saved=store.get(key);if(saved&&!el.value){el.value=saved;el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));}el.addEventListener(el.tagName==='SELECT'?'change':'input',()=>store.set(key,el.value));});

 // 26. Add Downloads search.
 const downloads=$('#downloadList');
 if(downloads&&!$('#vgDownloadSearch')){const title=$('#downloads .sectionTitle');const tools=document.createElement('div');tools.className='vg-download-tools';const input=document.createElement('input');input.id='vgDownloadSearch';input.type='search';input.placeholder='Search downloads…';input.setAttribute('aria-label','Search downloads by title or URL');input.value=store.get('vg-ui-download-search');tools.append(input);title?.insertAdjacentElement('afterend',tools);input.addEventListener('input',()=>{store.set('vg-ui-download-search',input.value);applyDownloadFilters();});}
 // 27. Add Downloads status filter.
 if(downloads&&!$('#vgDownloadStatus')){const tools=$('.vg-download-tools');if(tools){const sel=document.createElement('select');sel.id='vgDownloadStatus';sel.className='vg-ui-select';sel.setAttribute('aria-label','Filter downloads by status');sel.innerHTML='<option value="all">All statuses</option><option value="active">Active</option><option value="completed">Completed</option><option value="failed">Failed</option>';sel.value=store.get('vg-ui-download-status','all');tools.append(sel);sel.addEventListener('change',()=>{store.set('vg-ui-download-status',sel.value);applyDownloadFilters();});}}
 // 28. Add a hide-completed toggle for a cleaner queue.
 if(downloads&&!$('#vgHideCompleted')){const tools=$('.vg-download-tools');if(tools){const label=document.createElement('label');label.className='vg-ui-check';const check=document.createElement('input');check.type='checkbox';check.id='vgHideCompleted';check.checked=store.get('vg-ui-hide-completed')==='true';label.append(check,document.createTextNode(' Hide completed'));tools.append(label);check.addEventListener('change',()=>{store.set('vg-ui-hide-completed',String(check.checked));applyDownloadFilters();});}}
 // 29. Keep queue filters applied as download rows are re-rendered.
 function applyDownloadFilters(){if(!downloads)return;const q=($('#vgDownloadSearch')?.value||'').trim().toLowerCase();const status=$('#vgDownloadStatus')?.value||'all';const hide=$('#vgHideCompleted')?.checked||false;$$('.job',downloads).forEach(row=>{const text=(row.innerText||'').toLowerCase();const s=(row.querySelector('.jobStatus')?.innerText||'').toLowerCase();const done=/completed|complete|finished|success/.test(s);const failed=/failed|error/.test(s);const active=!done&&!failed;const matchesText=!q||text.includes(q);const matchesStatus=status==='all'||(status==='active'&&active)||(status==='completed'&&done)||(status==='failed'&&failed);row.hidden=!(matchesText&&matchesStatus&&!(hide&&done));});}
 if(downloads){const obs=new MutationObserver(()=>applyDownloadFilters());obs.observe(downloads,{childList:true,subtree:true});const input=$('#vgDownloadSearch');if(input){input.value=store.get('vg-ui-download-search');input.addEventListener('input',applyDownloadFilters)}$('#vgDownloadStatus')?.addEventListener('change',applyDownloadFilters);$('#vgHideCompleted')?.addEventListener('change',applyDownloadFilters);applyDownloadFilters();}

 // 30. Add a visible queue count that reflects filtered rows.
 if(downloads&&!$('#vgDownloadCount')){const count=document.createElement('div');count.id='vgDownloadCount';count.className='vg-ui-count';downloads.insertAdjacentElement('beforebegin',count);const update=()=>{const rows=$$('.job',downloads);const visible=rows.filter(r=>!r.hidden).length;count.textContent=visible+' of '+rows.length+' download'+(rows.length===1?'':'s')+' shown';};new MutationObserver(update).observe(downloads,{childList:true,subtree:true,attributes:true,attributeFilter:['hidden']});$('#vgDownloadSearch')?.addEventListener('input',update);$('#vgDownloadStatus')?.addEventListener('change',update);$('#vgHideCompleted')?.addEventListener('change',update);update();}

 // 31. Add a visible, collapsible settings search.
 const settings=$('#settings');
 if(settings&&!$('#vgSettingsSearch')){const input=document.createElement('input');input.id='vgSettingsSearch';input.type='search';input.placeholder='Find a setting…';input.setAttribute('aria-label','Search settings');input.className='vg-settings-search';const title=$('#settings .sectionTitle');title?.insertAdjacentElement('afterend',input);input.addEventListener('input',()=>{const q=input.value.trim().toLowerCase();$$('.settingsGrid .setting').forEach(card=>{card.hidden=!!q&&!card.innerText.toLowerCase().includes(q)});});}

 // 32. Add section-level jump links.
 const sectionTitle=$('.sectionTitle');
 if(sectionTitle&&!$('#vgWorkspaceJump')){const jump=document.createElement('select');jump.id='vgWorkspaceJump';jump.className='vg-ui-select';jump.setAttribute('aria-label','Jump to workspace');jump.innerHTML='<option value="">Jump to workspace…</option>'+$$('.nav[data-page]').map(b=>'<option value="'+b.dataset.page+'">'+(b.innerText||'').trim().replace(/\s+/g,' ')+'</option>').join('');const target=$('.topbarActions');target?.append(jump);jump.addEventListener('change',()=>{if(jump.value){$('[data-page="'+jump.value+'"]')?.click();jump.value='';}});}

 // 33. Add a persistent density control: comfortable vs compact spacing.
 if(!$('#vgDensityToggle')){const target=$('.topbarActions');if(target){const b=button('Compact view','vg-ui-secondary');b.id='vgDensityToggle';const apply=()=>{const compact=store.get('vg-ui-density','comfortable')==='compact';document.documentElement.classList.toggle('vg-compact-ui',compact);b.textContent=compact?'Compact: On':'Compact view';b.setAttribute('aria-pressed',String(compact));};b.addEventListener('click',()=>{store.set('vg-ui-density',store.get('vg-ui-density','comfortable')==='compact'?'comfortable':'compact');apply();});target.append(b);apply();}}

 // 34. Make Create Studio import area accept drag-and-drop as well as picker.
 const fileInput=$('#vgLocalMediaInput');
 if(fileInput){const card=fileInput.closest('.createFeature');if(card){card.classList.add('vg-dropzone');card.addEventListener('dragover',e=>{e.preventDefault();card.classList.add('vg-dragover')});card.addEventListener('dragleave',e=>{if(!card.contains(e.relatedTarget))card.classList.remove('vg-dragover')});card.addEventListener('drop',e=>{e.preventDefault();card.classList.remove('vg-dragover');const files=[...(e.dataTransfer?.files||[])].filter(f=>/^video\//.test(f.type)||/^audio\//.test(f.type));if(!files.length){announce('Drop a supported audio or video file.');return;}try{const dt=new DataTransfer();files.forEach(f=>dt.items.add(f));fileInput.files=dt.files;fileInput.dispatchEvent(new Event('change',{bubbles:true}));announce(files.length+' media file'+(files.length===1?'':'s')+' added to Create Studio.')}catch{announce('Drag and drop is not supported here. Use Choose files instead.')}});}}

 // 35. Add file count and total size near Create Studio import status.
 if(fileInput&&!$('#vgImportSummary')){const summary=document.createElement('small');summary.id='vgImportSummary';summary.className='vg-ui-count';$('#vgImportStatus')?.insertAdjacentElement('afterend',summary);fileInput.addEventListener('change',()=>{const files=[...(fileInput.files||[])];const bytes=files.reduce((n,f)=>n+f.size,0);const size=bytes<1048576?(bytes/1024).toFixed(0)+' KB':(bytes/1048576).toFixed(1)+' MB';summary.textContent=files.length?files.length+' file'+(files.length===1?'':'s')+' · '+size+' total':'No files selected';});}

 // 36. Add a "copy current API address" convenience button in Settings.
 const apiInput=$('#vgApiBaseInput');
 if(apiInput&&!$('#vgCopyApiAddress')){const b=button('Copy address','vg-ui-secondary');b.id='vgCopyApiAddress';$('#vgApiTestButton')?.insertAdjacentElement('afterend',b);b.addEventListener('click',async()=>{const value=apiInput.value.trim()||apiInput.placeholder;try{await navigator.clipboard.writeText(value);announce('Cloud API address copied.');}catch{apiInput.focus();apiInput.select();announce('Clipboard access is unavailable. Select and copy the address manually.')}});}

 // 37. Add one-click reset for UI preferences only (does not clear media, jobs or license).
 if(settings&&!$('#vgResetUiPrefs')){const b=button('Reset UI preferences','vg-ui-secondary');b.id='vgResetUiPrefs';const card=document.createElement('div');card.className='vg-ui-reset';const copy=document.createElement('span');copy.textContent='Resets this browser’s saved UI filters and density only. Media, downloads and license data are untouched.';card.append(copy,b);$('.settingsGrid')?.append(card);b.addEventListener('click',()=>{if(!confirm('Reset VidGravity UI preferences? Your media, downloads and license are not deleted.'))return;['vg-ui-library-search','vg-ui-library-tag','vg-ui-library-filter','vg-ui-library-view','vg-ui-download-search','vg-ui-download-status','vg-ui-hide-completed','vg-ui-density'].forEach(k=>{try{localStorage.removeItem(k)}catch{}});location.reload();});}

 // 38. Add a page-specific keyboard shortcut hint.
 if(!$('#vgShortcutHint')){const hint=document.createElement('span');hint.id='vgShortcutHint';hint.className='vg-ui-shortcut-hint';hint.textContent='Tip: Ctrl/⌘ K for Quick Actions · / to search actions';$('.topbar .crumb')?.insertAdjacentElement('afterend',hint);}

 // 39. Announce navigation and make the page heading focusable for screen-reader context.
 $$('.nav[data-page]').forEach(b=>b.addEventListener('click',()=>{setTimeout(()=>{const page=$('#'+b.dataset.page);const h=page?.querySelector('h1');if(h){h.setAttribute('tabindex','-1');announce((h.textContent||b.innerText).trim()+' workspace opened.');}},0)}));

 // 40. Add a polite status when online/offline changes, without implying API health.
 addEventListener('online',()=>announce('Network connection restored. This does not guarantee the VidGravity cloud API is available.'));
 addEventListener('offline',()=>announce('Network connection lost. Local media may remain available; cloud actions need a connection.'));
})();