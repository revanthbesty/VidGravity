/* VidGravity UI batch 41–90: 50 additive, resilient PWA/web refinements. */
(() => {
 'use strict';
 const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
 const storage={get(k,d=''){try{return localStorage.getItem(k)??d}catch{return d}},set(k,v){try{localStorage.setItem(k,v)}catch{}},del(k){try{localStorage.removeItem(k)}catch{}}};
 const make=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e};
 const announce=(text)=>{let n=$('#vgUi90Live');if(!n){n=make('div','vg90-sr');n.id='vgUi90Live';n.setAttribute('role','status');n.setAttribute('aria-live','polite');document.body.append(n)}n.textContent=text};
 const toast=(text)=>{let t=$('#vgUi90Toast');if(!t){t=make('div','vg90-toast');t.id='vgUi90Toast';t.setAttribute('role','status');document.body.append(t)}t.textContent=text;t.classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>t.classList.remove('show'),2600)};
 const on=(el,event,fn)=>el&&el.addEventListener(event,fn);
 const safeFocus=el=>{if(el&&typeof el.focus==='function')el.focus({preventScroll:true})};
 const navButtons=()=>$$('.nav, [data-page], [data-nav]');
 // 41. Show a lightweight toast for copied text and UI actions.
 document.addEventListener('vg:toast',e=>toast(String(e.detail||'Done')));
 // 42. Add a skip-to-content link for keyboard users.
 if(!$('[data-vg-skip]')){const a=make('a','vg90-skip','Skip to main content');a.href='#mainContent';a.dataset.vgSkip='';document.body.prepend(a);const main=$('main')||$('.main')||$('#mainContent');if(main&&!main.id)main.id='mainContent';}
 // 43. Persist and restore a user's preferred reduced visual density.
 const density=storage.get('vg90-density','comfortable');document.documentElement.dataset.vgDensity=density;
 if(!$('#vgDensityToggle')){const host=$('.topbarActions')||$('.topbar')||$('.settingsGrid');if(host){const b=make('button','vg90-tool-button',density==='compact'?'Comfortable spacing':'Compact spacing');b.id='vgDensityToggle';b.type='button';b.setAttribute('aria-pressed',String(density==='compact'));b.addEventListener('click',()=>{const next=document.documentElement.dataset.vgDensity==='compact'?'comfortable':'compact';document.documentElement.dataset.vgDensity=next;storage.set('vg90-density',next);b.textContent=next==='compact'?'Comfortable spacing':'Compact spacing';b.setAttribute('aria-pressed',String(next==='compact'));announce('Spacing set to '+next);});host.append(b)}}
 // 44. Restore last focused input on each workspace visit where safe.
 document.addEventListener('focusin',e=>{const el=e.target;if(el.matches('input,textarea,select')&&el.id&&!/password|token|secret/i.test(el.id))storage.set('vg90-last-focus',el.id)});
 // 45. Provide an explicit jump back to the last focused field.
 if(!$('#vgRestoreFocus')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Return to last field');b.id='vgRestoreFocus';b.type='button';b.addEventListener('click',()=>{const id=storage.get('vg90-last-focus');const el=id?document.getElementById(id):null;if(el){safeFocus(el);el.scrollIntoView({block:'center',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth')}else toast('No recent field to return to');});host.append(b)}}
 // 46. Add a global close-dialog Escape handler for native-looking overlays.
 on(document,'keydown',e=>{if(e.key!=='Escape')return;const open=$$('.modal:not([hidden]),dialog[open],[role="dialog"]:not([hidden])').filter(x=>getComputedStyle(x).display!=='none');const last=open.at(-1);if(last){const close=$('button[aria-label*="close" i],.close,[data-close],.modalClose',last);if(close)close.click();else if(last.tagName==='DIALOG')last.close();}});
 // 47. Improve missing image thumbnails with a graceful fallback.
 on(document,'error',e=>{const img=e.target;if(img instanceof HTMLImageElement&&!img.dataset.vgFallback){img.dataset.vgFallback='1';img.alt=img.alt||'Media thumbnail unavailable';img.classList.add('vg90-image-failed');img.removeAttribute('src')}},true);
 // 48. Ensure icon-only controls expose accessible names.
 $$('button').forEach(b=>{if(!b.getAttribute('aria-label')&&!b.title&&!b.textContent.trim()){const hint=b.dataset.tooltip||b.dataset.action||b.className.split(/\s+/).find(Boolean)||'Action';b.setAttribute('aria-label',hint.replace(/[-_]/g,' '))}});
 // 49. Mark external links and avoid accidentally navigating the app away.
 $$('a[href^="http"]').forEach(a=>{try{if(new URL(a.href).origin!==location.origin){a.rel=[...new Set((a.rel+' noopener noreferrer').trim().split(/\s+/))].join(' ');if(!a.hasAttribute('aria-label')&&a.textContent.trim())a.setAttribute('aria-label',a.textContent.trim()+' (opens external site)')}}catch{}});
 // 50. Add visible character counts to labelled textareas with a maxlength.
 $$('textarea[maxlength]').forEach((field,i)=>{if(field.nextElementSibling?.classList.contains('vg90-charcount'))return;const count=make('small','vg90-charcount');count.id='vg90-charcount-'+i;count.setAttribute('aria-live','polite');field.setAttribute('aria-describedby',[field.getAttribute('aria-describedby'),count.id].filter(Boolean).join(' '));const update=()=>count.textContent=field.value.length+' / '+field.maxLength;field.insertAdjacentElement('afterend',count);on(field,'input',update);update()});
 // 51. Provide clearer invalid-field focus and announcements on form submit.
 $$('form').forEach(form=>on(form,'submit',()=>{const invalid=$(':invalid',form);if(invalid){announce('Please check the highlighted fields');setTimeout(()=>safeFocus(invalid),0)}}));
 // 52. Keep form controls from overflowing narrow screens.
 $$('input,select,textarea').forEach(el=>{el.style.maxWidth='100%';if(!el.getAttribute('autocomplete')&&el.type==='search')el.setAttribute('autocomplete','off')});
 // 53. Add keyboard-visible focus to custom clickable cards.
 $$('.media,.downloadItem,.job,.quickLink,[data-clickable="true"]').forEach(el=>{if(el.hasAttribute('onclick')||el.matches('a,button')){if(!el.hasAttribute('tabindex'))el.tabIndex=0;on(el,'keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target===el){e.preventDefault();el.click()}})}});
 // 54. Make headings and long names wrap instead of pushing layout wide.
 $$('h1,h2,h3,.mediaTitle,.jobTitle,.downloadItemTitle,.jobUrl').forEach(el=>el.classList.add('vg90-wrap'));
 // 55. Add accessible labels to unlabeled search inputs.
 $$('input[type="search"],input[placeholder*="search" i]').forEach(el=>{if(!el.labels?.length&&!el.getAttribute('aria-label'))el.setAttribute('aria-label',el.placeholder||'Search')});
 // 56. Persist dismissals for install/UX banners for this session.
 ['vgInstallDismiss'].forEach(id=>{const b=document.getElementById(id);on(b,'click',()=>storage.set('vg90-install-dismissed','1'));if(id==='vgInstallDismiss'&&storage.get('vg90-install-dismissed')==='1'){const banner=$('#vgInstallBanner');if(banner)banner.hidden=true}});
 // 57. Make online/offline status text explicit but never imply backend health.
 const net=()=>{const el=$('#vgConnectivityStatus');if(el){el.hidden=false;el.textContent=navigator.onLine?'Browser is online · service availability not checked':'Offline · network requests may not work';el.dataset.online=String(navigator.onLine)}};on(window,'online',net);on(window,'offline',net);
 // 58. Announce page title changes for assistive technology.
 let oldTitle=document.title;const titleObserver=new MutationObserver(()=>{if(document.title!==oldTitle){oldTitle=document.title;announce('Page: '+oldTitle)}});titleObserver.observe(document.querySelector('title')||document.head,{childList:true,subtree:true,characterData:true});
 // 59. Give the active navigation item a consistent aria-current marker.
 const syncNav=()=>{const active=$$('.nav.active,[data-page].active,[data-nav].active');navButtons().forEach(b=>{if(active.includes(b))b.setAttribute('aria-current','page');else b.removeAttribute('aria-current')})};syncNav();new MutationObserver(syncNav).observe(document.body,{subtree:true,attributes:true,attributeFilter:['class']});
 // 60. Add a persistent return-to-top button for long workspaces.
 if(!$('#vg90Top')){const b=make('button','vg90-top','↑ Top');b.id='vg90Top';b.type='button';b.setAttribute('aria-label','Back to top');document.body.append(b);on(b,'click',()=>window.scrollTo({top:0,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'}));on(window,'scroll',()=>b.classList.toggle('visible',window.scrollY>500),{passive:true})}
 // 61. Confirm destructive actions whose wording suggests permanent deletion.
 $$('button').forEach(b=>{if(b.dataset.vgConfirmBound||!/(delete all|delete selected|remove selected|clear history|clear all)/i.test(b.textContent))return;b.dataset.vgConfirmBound='1';b.addEventListener('click',e=>{if(!window.confirm('This action may permanently remove selected items. Continue?')){e.stopImmediatePropagation();e.preventDefault()}},true)});
 // 62. Avoid duplicate form submissions while a submit handler is running.
 $$('form').forEach(form=>on(form,'submit',()=>{const submit=$('button[type="submit"],input[type="submit"]',form);if(!submit)return;submit.dataset.vgOriginalLabel=submit.textContent;submit.setAttribute('aria-busy','true');setTimeout(()=>{submit.removeAttribute('aria-busy')},1800)}));
 // 63. Provide a clear-all filter action for common filter groups.
 $$('select').filter(s=>/filter|status|type|sort/i.test(s.id+' '+s.name+' '+s.getAttribute('aria-label'))).forEach((s,i)=>{if(s.dataset.vgClearBound)return;s.dataset.vgClearBound='1';const b=make('button','vg90-inline-clear','Reset');b.type='button';b.setAttribute('aria-label','Reset '+(s.getAttribute('aria-label')||s.name||'filter'));s.insertAdjacentElement('afterend',b);on(b,'click',()=>{s.selectedIndex=0;s.dispatchEvent(new Event('change',{bubbles:true}));announce('Filter reset')})});
 // 64. Add a one-click copy helper to URL-like readonly fields.
 $$('input[readonly]').forEach((field,i)=>{if(!/^https?:\/\//i.test(field.value)||field.dataset.vgCopyBound)return;field.dataset.vgCopyBound='1';const b=make('button','vg90-inline-clear','Copy URL');b.type='button';b.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(field.value);toast('URL copied')}catch{field.select();toast('Select and copy the URL')}});field.insertAdjacentElement('afterend',b)});
 // 65. Improve empty-state copy for common empty result containers without fabricating data.
 ['#libraryGrid','#downloadList','#recentGrid'].forEach(sel=>{const host=$(sel);if(!host)return;const update=()=>{const visible=[...host.children].filter(x=>getComputedStyle(x).display!=='none'&&!x.hidden);host.classList.toggle('vg90-empty',visible.length===0)};new MutationObserver(update).observe(host,{childList:true,subtree:true,attributes:true,attributeFilter:['hidden','style','class']});update()});
 // 66. Restore focus to the control that opened a modal when it closes.
 let opener=null;on(document,'click',e=>{const b=e.target.closest('button,[role="button"]');if(b&&/open|share|details|settings|preview|help/i.test(b.textContent+' '+b.dataset.action))opener=b});on(document,'click',e=>{const b=e.target.closest('.close,[data-close],button[aria-label*="close" i]');if(b&&opener)setTimeout(()=>safeFocus(opener),0)});
 // 67. Keep long modal content scrollable inside viewport.
 $$('.modalBox,.modal-content,[role="dialog"]').forEach(el=>{el.style.maxHeight='min(88dvh,900px)';el.style.overflowY='auto';el.style.overscrollBehavior='contain'});
 // 68. Add a small copy-current-page-link action to the top bar.
 if(!$('#vg90CopyPage')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Copy page link');b.id='vg90CopyPage';b.type='button';on(b,'click',async()=>{try{await navigator.clipboard.writeText(location.href);toast('Page link copied')}catch{toast('Clipboard permission unavailable')}});host.append(b)}}
 // 69. Let users toggle high-contrast borders for clearer separation.
 if(!$('#vg90Contrast')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Stronger contrast');b.id='vg90Contrast';b.type='button';const apply=v=>{document.documentElement.classList.toggle('vg90-contrast',v);b.setAttribute('aria-pressed',String(v));storage.set('vg90-contrast',v?'1':'0')};apply(storage.get('vg90-contrast')==='1');on(b,'click',()=>apply(!document.documentElement.classList.contains('vg90-contrast')));host.append(b)}}
 // 70. Remember user preference for larger text.
 if(!$('#vg90TextSize')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Larger text');b.id='vg90TextSize';b.type='button';const apply=v=>{document.documentElement.classList.toggle('vg90-large-text',v);b.setAttribute('aria-pressed',String(v));storage.set('vg90-large-text',v?'1':'0')};apply(storage.get('vg90-large-text')==='1');on(b,'click',()=>apply(!document.documentElement.classList.contains('vg90-large-text')));host.append(b)}}
 // 71. Expose a compact help panel with safe, non-destructive keyboard tips.
 if(!$('#vg90Help')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Keyboard help');b.id='vg90Help';b.type='button';on(b,'click',()=>{let p=$('#vg90HelpPanel');if(p){p.remove();return}p=make('section','vg90-help-panel');p.id='vg90HelpPanel';p.setAttribute('role','dialog');p.setAttribute('aria-label','Keyboard help');const h=make('h2','','Keyboard help');const ul=make('ul');[['Esc','Close the current dialog'],['Ctrl / Cmd + K','Open Quick Actions if available'],['Tab / Shift + Tab','Move between controls'],['Enter / Space','Activate a focused control']].forEach(([k,v])=>{const li=make('li');const key=make('kbd','',k);li.append(key,document.createTextNode(' '+v));ul.append(li)});const close=make('button','vg90-tool-button','Close help');close.type='button';on(close,'click',()=>p.remove());p.append(h,ul,close);document.body.append(p);safeFocus(close)});host.append(b)}}
 // 72. Respect reduced-motion preference for programmatic scrolling.
 if(matchMedia('(prefers-reduced-motion: reduce)').matches)document.documentElement.classList.add('vg90-reduced-motion');
 // 73. Avoid accidental duplicate activation from rapid double-clicks on destructive controls.
 $$('button').forEach(b=>{if(!/(delete|remove|reset|clear)/i.test(b.textContent)||b.dataset.vgDebounceBound)return;b.dataset.vgDebounceBound='1';on(b,'click',()=>{if(b.dataset.vgBusy==='1')return;b.dataset.vgBusy='1';setTimeout(()=>delete b.dataset.vgBusy,700)})});
 // 74. Add a reliable copy fallback for non-secure contexts.
 document.addEventListener('click',async e=>{const b=e.target.closest('[data-copy-value]');if(!b)return;const val=b.dataset.copyValue||'';try{await navigator.clipboard.writeText(val);toast('Copied')}catch{const t=make('textarea','');t.value=val;document.body.append(t);t.select();try{document.execCommand('copy');toast('Copied')}catch{toast('Copy unavailable')}t.remove()}});
 // 75. Keep tables and wide code snippets usable on phones.
 $$('table,pre,code').forEach(el=>{if(el.closest('.vg90-scroll-wrap'))return;const wrap=make('div','vg90-scroll-wrap');el.parentNode?.insertBefore(wrap,el);wrap.append(el)});
 // 76. Add a clear filter/search cue when search inputs have content.
 $$('input[type="search"],input[placeholder*="search" i]').forEach(el=>{const update=()=>el.classList.toggle('vg90-has-value',!!el.value);on(el,'input',update);update()});
 // 77. Preserve a user's last visited page hint for returning sessions.
 const pageObserver=new MutationObserver(()=>{const active=$$('.page.active,[data-page].active').at(-1);if(active)storage.set('vg90-last-page',active.id||active.dataset.page||active.textContent.trim().slice(0,40))});pageObserver.observe(document.body,{subtree:true,attributes:true,attributeFilter:['class']});
 // 78. Offer a return-to-last-page hint if one is available (no automatic forced navigation).
 const last=storage.get('vg90-last-page');if(last&&!$('#vg90LastPageHint')){const host=$('.topbarActions');if(host){const b=make('button','vg90-tool-button','Last workspace');b.id='vg90LastPageHint';b.type='button';b.title='Last workspace: '+last;on(b,'click',()=>{const target=document.getElementById(last)||$('[data-page="'+CSS.escape(last)+'"]');if(target){target.click();announce('Returned to last workspace')}else toast('Use the main navigation to open your last workspace')});host.append(b)}}
 // 79. Set minimum touch target size on interactive controls.
 $$('button,a,input[type="checkbox"],input[type="radio"],[role="button"]').forEach(el=>el.classList.add('vg90-touch-target'));
 // 80. Make the app's active section announce its heading when a nav item is clicked.
 navButtons().forEach(b=>on(b,'click',()=>{const label=b.getAttribute('aria-label')||b.textContent.trim();if(label)announce(label+' opened')}));
 // 81. Keep the current page's heading available to screen readers.
 const headingObserver=new MutationObserver(()=>{const page=$('.page.active')||$('main');if(page&&!page.hasAttribute('aria-label')){const h=$('h1,h2',page);if(h)page.setAttribute('aria-label',h.textContent.trim())}});headingObserver.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['class']});
 // 82. Show a gentle warning before leaving with an actively edited form.
 let dirtyForms=new WeakSet();$('form[data-warn-unsaved="true"]').forEach(form=>{on(form,'input',e=>{if(e.target.matches('input,textarea,select')&&!e.target.readOnly)dirtyForms.add(form)});on(form,'change',e=>{if(e.target.matches('input,textarea,select')&&!e.target.readOnly)dirtyForms.add(form)});on(form,'submit',()=>dirtyForms.delete(form));on(window,'beforeunload',e=>{if(dirtyForms.has(form)){e.preventDefault();e.returnValue=''}})});
 // 83. Ensure disabled controls visually communicate their state.
 $$('button:disabled,input:disabled,select:disabled,textarea:disabled').forEach(el=>el.classList.add('vg90-disabled'));
 // 84. Add a compact current-time-independent app version label for diagnostics.
 if(!$('#vg90UiVersion')){const foot=$('.sideFoot')||$('.sidebar')||$('.settingsGrid');if(foot){const v=make('small','vg90-version','UI polish · 90 improvements');v.id='vg90UiVersion';foot.append(v)}}
 // 85. Improve labels for icon buttons that have title text.
 $$('button[title]').forEach(b=>{if(!b.getAttribute('aria-label'))b.setAttribute('aria-label',b.title)});
 // 86. Prevent accidental text selection on repeated toolbar taps without affecting inputs.
 $$('.topbarActions button,.nav').forEach(el=>el.classList.add('vg90-no-select'));
 // 87. Make audio/video previews scale within the visible workspace.
 $$('video,audio,canvas,iframe').forEach(el=>el.classList.add('vg90-media-fit'));
 // 88. Keep file names and URLs readable without horizontal overflow.
 $$('[title],a[href]').forEach(el=>{if(el.matches('.jobUrl,.mediaTitle,.downloadItemTitle,.detailUrl'))el.classList.add('vg90-wrap')});
 // 89. Add a small visible status when the app is opened as an installed PWA.
 if(!$('#vg90PwaStatus')&&(matchMedia('(display-mode: standalone)').matches||navigator.standalone)){const s=make('span','vg90-pwa-status','Installed app');s.id='vg90PwaStatus';($('.topbarActions')||$('.topbar'))?.append(s)}
 // 90. Keep theme changes reflected in app meta theme-color.
 const themeMeta=$('meta[name="theme-color"]');const themeObserver=new MutationObserver(()=>{if(!themeMeta)return;const light=document.documentElement.dataset.theme==='light'||document.documentElement.classList.contains('vg-light');themeMeta.content=light?'#f7f8fc':'#171326'});themeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme','class']});
})();