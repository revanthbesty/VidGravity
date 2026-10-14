/* VidGravity QuickFlow: keyboard-first intake, safer queue handling, and fast filtering. */
(() => {
 'use strict';
 const $ = (selector, root=document) => root.querySelector(selector);
 const input = $('#urlInput'), inspect = $('#inspectBtn'), inspectBox = $('#inspectResult');
 if (!input || !inspect || !inspectBox) return;
 const announce = (message) => {
   let live = $('#vgQuickFlowLive');
   if (!live) { live=document.createElement('div'); live.id='vgQuickFlowLive'; live.className='vg-sr-only'; live.setAttribute('role','status'); live.setAttribute('aria-live','polite'); document.body.appendChild(live); }
   live.textContent=message;
 };
 // 1–4: quick paste, clear, keyboard submit, and global focus shortcut.
 const tools=document.createElement('div');
 tools.className='vg-quick-intake-tools';
 tools.innerHTML='<button type="button" class="smallBtn" id="vgPasteUrl">Paste link</button><button type="button" class="smallBtn" id="vgClearUrl">Clear link</button><span class="vg-shortcut-hint">Enter to inspect · Ctrl/⌘ K to focus</span>';
 input.closest('.urlRow')?.insertAdjacentElement('afterend',tools);
 $('#vgPasteUrl')?.addEventListener('click',async()=>{
   try {
     if (!navigator.clipboard?.readText) throw new Error('Clipboard access is not available in this browser.');
     const text=(await navigator.clipboard.readText()).trim();
     if (!text) throw new Error('Clipboard is empty.');
     input.value=text; input.dispatchEvent(new Event('input',{bubbles:true})); input.focus(); announce('Link pasted. Review it, then inspect.');
   } catch (error) { announce(error.message||'Could not read clipboard. Paste the link into the field instead.'); input.focus(); }
 });
 $('#vgClearUrl')?.addEventListener('click',()=>{input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();inspectBox.classList.add('hidden');announce('Link field cleared.');});
 input.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.isComposing){event.preventDefault();inspect.click();}});
 document.addEventListener('keydown',event=>{
   if ((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k') {event.preventDefault();input.focus();input.select();}
   if (event.key==='Escape'&&document.activeElement===input) input.blur();
 });
 // 5–7: trim pasted whitespace and provide immediate URL feedback without sending data.
 input.addEventListener('paste',()=>setTimeout(()=>{input.value=input.value.trim();},0));
 input.addEventListener('input',()=>input.setCustomValidity(''));
 inspect.addEventListener('click',event=>{
   const raw=input.value.trim();
   let parsed;
   try { parsed=new URL(raw); } catch { event.preventDefault();event.stopImmediatePropagation();input.setCustomValidity('Enter a complete URL, including https://'); input.reportValidity(); return; }
   if(!['http:','https:'].includes(parsed.protocol)){event.preventDefault();event.stopImmediatePropagation();input.setCustomValidity('Only http:// and https:// links are supported.');input.reportValidity();return;}
   input.value=raw;announce('Inspecting media link.');
 },true);
 // 8: make inspection changes available to assistive technology.
 const inspectObserver=new MutationObserver(()=>{if(!inspectBox.classList.contains('hidden')){const text=(inspectBox.innerText||'').trim();if(text)announce(text.slice(0,220));}});
 inspectObserver.observe(inspectBox,{childList:true,subtree:true,characterData:true});
 // 9: prevent accidental duplicate active queue entries; reuse the existing job.
 if(typeof window.serverDownload==='function'&&!window.serverDownload.__vgQuickFlow){
   const original=window.serverDownload;
   const wrapped=function(url,info={}) {
     const canonical=String(url||'').trim();
     const activeJobs=typeof jobs!=='undefined'&&Array.isArray(jobs)?jobs:[];
     const existing=activeJobs.find(job=>job.url===canonical&&['queued','running'].includes(job.status));
     if(existing){if(typeof window.toast==='function')window.toast('This link is already in your download queue.');announce('Duplicate active download avoided.');return existing;}
     return original.call(this,canonical,info);
   };
   wrapped.__vgQuickFlow=true;window.serverDownload=wrapped;
 }
 // 10–15: queue search, status filters, live counts, and quick recovery visibility.
 const list=$('#downloadList');
 if(list){
   const toolbar=document.createElement('section');
   toolbar.className='vg-queue-tools';
   toolbar.setAttribute('aria-label','Filter download activity');
   toolbar.innerHTML='<label class="vg-queue-search"><span>Search downloads</span><input id="vgQueueSearch" type="search" placeholder="Title or URL…" autocomplete="off"></label><div class="vg-queue-filters" role="group" aria-label="Queue status"><button type="button" data-vg-status="all" aria-pressed="true">All</button><button type="button" data-vg-status="queued">Queued</button><button type="button" data-vg-status="running">Running</button><button type="button" data-vg-status="failed">Failed</button><button type="button" data-vg-status="cancelled">Cancelled</button></div><p class="vg-queue-summary" id="vgQueueSummary" aria-live="polite"></p>';
   let activeStatus='all', query='';
   const applyFilters=()=>{
     const cards=[...list.querySelectorAll('.job')];
     const statusCounts={queued:0,running:0,failed:0,cancelled:0,recoverable:0};
     cards.forEach(card=>{
       const title=$('.jobTitle',card)?.textContent||'',url=$('.jobUrl',card)?.textContent||'';
       const statusNode=card.querySelector('.jobStatus');
       const jobStatus=Object.keys(statusCounts).find(s=>(statusNode?.textContent||'').toLowerCase().includes(s))||'';
       if(jobStatus in statusCounts)statusCounts[jobStatus]++;
       card.dataset.vgStatus=jobStatus;
       card.hidden=!(activeStatus==='all'||jobStatus===activeStatus||activeStatus==='failed'&&jobStatus==='recoverable')||!((title+' '+url).toLowerCase().includes(query));
     });
     const completed=[...list.querySelectorAll('.downloadItem')];
     completed.forEach(card=>{const text=(card.innerText||'').toLowerCase();card.hidden=!!query&&!text.includes(query);});
     const summary=$('#vgQueueSummary',toolbar);
     if(summary)summary.textContent=cards.length+' active/recent jobs · '+statusCounts.queued+' queued · '+statusCounts.running+' running · '+statusCounts.failed+' failed · '+completed.filter(card=>!card.hidden).length+' completed shown';
   };
   toolbar.addEventListener('click',event=>{const button=event.target.closest('[data-vg-status]');if(!button)return;activeStatus=button.dataset.vgStatus;toolbar.querySelectorAll('[data-vg-status]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));applyFilters();});
   $('#vgQueueSearch',toolbar).addEventListener('input',event=>{query=event.target.value.trim().toLowerCase();applyFilters();});
   const mountToolbar=()=>{if(!$('#vgQueueTools',list)){toolbar.id='vgQueueTools';list.prepend(toolbar);}applyFilters();};
   const queueObserver=new MutationObserver(()=>{if(!$('#vgQueueTools',list))mountToolbar();else applyFilters();});
   queueObserver.observe(list,{childList:true,subtree:false});
   mountToolbar();
 }
 // 16–18: accessible focus, offline feedback, and compact/mobile-safe controls.
 input.setAttribute('aria-keyshortcuts','Enter Control+K Meta+K');
 const connect=$('#vgConnectivityStatus');
 if(connect){const sync=()=>{if(!navigator.onLine){connect.hidden=false;connect.textContent='Offline: local library remains available; inspection and downloads need a connection.';}};window.addEventListener('offline',sync);sync();}
 // 19–20: respect reduced-motion and make controls keyboard-operable.
 document.documentElement.classList.add('vg-quickflow-ready');
 window.VidGravityQuickFlow={version:1,filterQueue:()=>{const search=$('#vgQueueSearch');if(search)search.focus();},focusUrl:()=>input.focus()};
 announce('Quick tools ready. Press Enter to inspect a link, or Control or Command plus K to focus the link field.');
})();