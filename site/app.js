const DB_NAME='vidgravity', DB_VERSION=2, STORE='media';
let media=[], jobs=[];
const ACTIVE_LIMIT=2;
const activeControllers=new Map();
const $=s=>document.querySelector(s), $$=s=>document.querySelectorAll(s);
// Cloud-only API endpoint shared by web and Android clients.
const VG_CLOUD_API='https://vidgravity.onrender.com';
function vgApiUrl(path){
 const stored=(localStorage.getItem('vg-api-base-url')||'').trim().replace(/\/+$/,'');
 let base=stored;
 try{if(base){const u=new URL(base);if(u.protocol!=='https:'||['localhost','127.0.0.1','::1'].includes(u.hostname))base='';}}catch{base='';}
 base=base||VG_CLOUD_API;
 return base + '/' + String(path).replace(/^\//,'');
}

const dbp=new Promise((resolve,reject)=>{const r=indexedDB.open(DB_NAME,DB_VERSION);r.onupgradeneeded=()=>{const db=r.result;if(!db.objectStoreNames.contains(STORE))db.createObjectStore(STORE,{keyPath:'id'});};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)});
async function dbPut(item){const db=await dbp;return new Promise((res,rej)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).put(item);tx.oncomplete=res;tx.onerror=()=>rej(tx.error)})}
async function dbAll(){const db=await dbp;return new Promise((res,rej)=>{const r=db.transaction(STORE).objectStore(STORE).getAll();r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)})}
async function dbGet(id){const db=await dbp;return new Promise((res,rej)=>{const r=db.transaction(STORE).objectStore(STORE).get(id);r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)})}
async function dbDelete(id){const db=await dbp;return new Promise((res,rej)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(id);tx.oncomplete=res;tx.onerror=()=>rej(tx.error)})}
function toast(t){const x=$('#toast');x.textContent=t;x.classList.add('show');setTimeout(()=>x.classList.remove('show'),2400)}
async function load(){try{media=await dbAll()}catch(e){media=[];toast('Local storage unavailable')}try{jobs=JSON.parse(localStorage.getItem('vg-jobs')||'[]')}catch{jobs=[]}jobs=jobs.map(j=>j.status==='running'?{...j,status:'recoverable',error:'Recovered after browser reload'}:j);saveJobs();renderAll();pumpQueue()}
function saveJobs(){localStorage.setItem('vg-jobs',JSON.stringify(jobs))}
// Persist media metadata and tags to IndexedDB; blobs remain in the same store.
function save(){return Promise.all(media.map(item=>dbPut(item)))}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function nav(page){$$('.page').forEach(x=>x.classList.toggle('active',x.id===page));$$('.nav').forEach(x=>x.classList.toggle('active',x.dataset.page===page));$('#pageTitle').textContent=page==='ask'?'Ask My Media':page[0].toUpperCase()+page.slice(1);if(page==='library')renderLibrary();if(page==='downloads')renderJobs();$('.sidebar')?.classList.remove('open')}
$$('.nav').forEach(b=>b.onclick=()=>nav(b.dataset.page));$$('[data-page]').forEach(b=>b.onclick=()=>nav(b.dataset.page));
$('#menu').onclick=()=>$('.sidebar').classList.toggle('open');
const THEME_KEY='vg-theme';
function systemTheme(){return window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'}
function applyTheme(preference, persist=true){
 const selected=['light','dark','system'].includes(preference)?preference:'system';
 const resolved=selected==='system'?systemTheme():selected;
 document.documentElement.dataset.theme=resolved;
 document.documentElement.dataset.themePreference=selected;
 try{if(persist)localStorage.setItem(THEME_KEY,selected)}catch{}
 const button=$('#themeToggle');
 if(button){button.textContent=selected==='system'?'◐ System theme':selected==='dark'?'☀ Light mode':'☾ Dark mode';button.setAttribute('aria-label',selected==='system'?'Theme follows system. Activate to change theme.':selected==='dark'?'Switch to light mode':'Switch to dark mode');button.title='Current appearance: '+selected+'. Click to cycle Light, Dark, and System.'}
}
try{applyTheme(localStorage.getItem(THEME_KEY)||'system',false)}catch{applyTheme('system',false)}
$('#themeToggle')?.addEventListener('click',()=>{const current=document.documentElement.dataset.themePreference||'system';applyTheme(current==='dark'?'light':current==='light'?'system':'dark')});
window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change',()=>{if((document.documentElement.dataset.themePreference||'system')==='system')applyTheme('system',false)});
window.addEventListener('storage',event=>{if(event.key===THEME_KEY)applyTheme(event.newValue||'system',false)});
$('#clearAll').onclick=()=>{jobs=jobs.filter(j=>!['completed','failed'].includes(j.status));saveJobs();renderJobs();toast('Completed jobs cleared')};

$('#inspectBtn').onclick=async()=>{
 const url=$('#urlInput').value.trim();
 if(!/^https?:\/\//i.test(url))return toast('Enter a valid http(s) URL');
 const button=$('#inspectBtn'),box=$('#inspectResult');
 button.disabled=true;button.textContent='Inspecting…';box.classList.remove('hidden');
 box.innerHTML='<b>Inspecting media…</b><div class="mediaMeta">Resolving title, duration, and available formats.</div>';
 try{
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),45000);
  let response,data,usedLocalFallback=false;
  try{response=await fetch(vgApiUrl('/api/inspect?url='+encodeURIComponent(url)),{signal:controller.signal});data=await response.json().catch(()=>({}));}
  finally{clearTimeout(timer)}
  if(!response.ok){const message=[data.error||('Inspection failed (HTTP '+response.status+')'),data.details].filter(Boolean).join(' · ');throw Error(message);}
  const size=data.size&&data.size!=='unknown'&&Number.isFinite(Number(data.size))?formatBytes(Number(data.size)):(data.size||'size unknown');
  box.innerHTML=`<div class="inspectHeading"><span class="inspectMediaIcon" aria-hidden="true">${String(data.type||'').toLowerCase().includes('audio')?'♫':'🎬'}</span><div><b>${esc(data.title||'Media detected')}</b><div class="mediaMeta">${esc(data.type||'media')} · ${esc(size)} · ${esc(data.source||'server')}${data.duration?' · '+esc(formatDuration(data.duration)):''}</div></div></div><div class="formatChooserHead"><div><b>Choose audio or video</b><small>Switch between simple standard quality options. Duplicate codecs and formats are grouped to reduce confusion.</small></div><span class="formatCount">${Number(data.format_count||((data.formats||[]).length))} option(s)</span></div><div id="formatPanel"></div><div class="downloadActionRow"><div id="selectedFormatSummary" class="mediaMeta" aria-live="polite">Choose a format to continue.</div><button class="primary downloadBest" id="downloadNow" disabled>Download selected quality</button></div>`;
  renderAvailableFormats(box.querySelector('#formatPanel'),data.formats||[],url,data);
 }catch(error){
  box.innerHTML=`<b>Inspection failed</b><div class="mediaMeta">${esc(error.name==='AbortError'?'Inspection timed out after 45 seconds':error.message||'The cloud media service could not inspect this URL.')}</div><div class="mediaMeta">VidGravity runs online. Some providers block cloud servers or require sign-in; unavailable media cannot be downloaded until the provider allows access.</div><button class="smallBtn" id="retryInspect">Retry Inspect</button>`;
  $('#retryInspect').onclick=()=>$('#inspectBtn').click();
 }finally{button.disabled=false;button.textContent='Inspect'}
};

function renderAvailableFormats(panel,formats,url,info){
 const usable=(formats||[]).filter(f=>f&&f.id&&(f.has_video||f.has_audio));
 const video=usable.filter(f=>f.has_video),audio=usable.filter(f=>f.has_audio&&!f.has_video);
 const downloadButton=$('#downloadNow'),summary=$('#selectedFormatSummary');
 if(!usable.length){panel.innerHTML='<div class="mediaMeta formatEmpty">No selectable audio or video formats were returned for this URL.</div>';if(downloadButton)downloadButton.disabled=true;return}
 const heightOf=f=>{const r=String(f.resolution||'');const p=r.match(/(?:^|\D)(\d{3,4})p(?:\D|$)/i);if(p)return Number(p[1]);const xy=r.match(/(\d{3,4})\s*x\s*(\d{3,4})/i);if(xy)return Number(xy[2]);return Number(f.height)||0};
 const sizeKnown=f=>Number(f.filesize)>0?1:0;
 const byHeight=new Map();
 video.forEach(f=>{const h=heightOf(f);if(!h)return;const old=byHeight.get(h);const score=x=>(x.has_audio?100:0)+(String(x.ext).toLowerCase()==='mp4'?20:0)+sizeKnown(x)*10;if(!old||score(f)>score(old))byHeight.set(h,f)});
 const standardHeights=[4320,2160,1440,1080,720,480,360];
 const videoChoices=[{format:'bv*+ba/b',label:'Highest available',detail:'Maximum quality supported by this source · best compatible video and audio',kind:'video'},...standardHeights.filter(h=>byHeight.has(h)).map(h=>{const f=byHeight.get(h);return {format:f.has_audio?(f.selector||f.id):String(f.selector||f.id)+'+bestaudio/best',label:h===4320?'8K (4320p)':h===2160?'4K (2160p)':h+'p',detail:[h===4320?'Ultra-high detail for 8K displays':h===2160?'Large screens and high detail':h===1440?'High quality with less data than 4K':h===1080?'Standard Full HD choice':h===720?'Balanced quality and file size':h===480?'Smaller file for slower connections':'Low-bandwidth use',String(f.ext||'MP4').toUpperCase(),f.has_audio?'Video + audio':'Audio will be combined'].join(' · '),kind:'video'}})];
 const rank=q=>({'high':3,'medium':2,'low':1,'tiny':0,'small':0})[String(q||'').toLowerCase()]??-1;
 const audioByQuality=new Map();
 audio.forEach(f=>{const q=String(f.note||'').toLowerCase().match(/\b(high|medium|low|tiny|small)\b/)?.[1]||'';const key=q||'standard';const old=audioByQuality.get(key);const score=x=>(String(x.ext).toLowerCase()==='m4a'?30:0)+sizeKnown(x)*10;if(!old||score(f)>score(old))audioByQuality.set(key,f)});
 const audioChoices=[];if(audio.length)audioChoices.push({format:'bestaudio/best',label:'Best available audio',detail:'Automatically select the best compatible audio stream',kind:'audio'});
 [...audioByQuality.entries()].sort((a,b)=>rank(b[0])-rank(a[0])).forEach(([q,f])=>{const label=q==='high'?'High quality':q==='medium'?'Medium quality':q==='low'?'Low size':q==='tiny'||q==='small'?'Small file':'Standard quality';if(audioChoices.some(x=>x.label===label))return;audioChoices.push({format:f.selector||f.id,label,detail:[String(f.ext||'audio').toUpperCase(),sizeKnown(f)?formatBytes(Number(f.filesize)):'Size varies by source',f.acodec&&f.acodec!=='none'?f.acodec.toUpperCase():'Audio'].join(' · '),kind:'audio'})});
 let selected=null;
 panel.innerHTML='<div class="qualityColumns"><section class="qualityColumn" aria-label="Video quality"><div class="qualityColumnHeader"><span class="qualityColumnIcon">▶</span><div><b>Video</b><small>Download video with sound</small></div></div><div class="standardQualityList videoQualityList" id="videoQualityList"></div></section><section class="qualityColumn" aria-label="Audio quality"><div class="qualityColumnHeader"><span class="qualityColumnIcon">♫</span><div><b>Audio only</b><small>Save a separate audio file</small></div></div><div class="standardQualityList audioQualityList" id="audioQualityList"></div></section></div><div class="downloadBundleOption" id="downloadBundleOption" hidden><label class="downloadBundleLabel" for="downloadBothCheckbox"><input id="downloadBothCheckbox" type="checkbox"><span class="downloadBundleCheck"></span><span class="downloadBundleCopy"><b>Also download audio separately</b><small>Get the video with sound plus a separate audio file in the same queue.</small></span><span class="bundleBadge">2 FILES</span></label></div>';
 const bundleOption=panel.querySelector('#downloadBundleOption'),bundleCheckbox=panel.querySelector('#downloadBothCheckbox');
 const refreshDownloadAction=()=>{if(!selected)return;const both=selected.type==='video'&&!!bundleCheckbox?.checked;if(downloadButton){downloadButton.disabled=false;downloadButton.textContent=both?'Download video + audio files':selected.type==='audio'?'Download audio · '+selected.label:'Download video · '+selected.label}if(summary)summary.textContent=selected.type==='audio'?'Selected: Audio only — '+selected.label+'.':both?'Selected: '+selected.label+' video plus a separate audio file. Both will be added to Downloads.':'Selected: '+selected.label+' video with sound. You can also save audio separately.'};
 bundleCheckbox?.addEventListener('change',refreshDownloadAction);
 const renderChoices=(type)=>{const choices=type==='video'?videoChoices:audioChoices;const list=panel.querySelector(type==='video'?'#videoQualityList':'#audioQualityList');list.innerHTML=choices.length?choices.map((c,i)=>'<button type="button" class="standardQualityChoice" data-kind="'+type+'" data-index="'+i+'" aria-pressed="false"><span class="standardQualityIcon">'+(type==='video'?'▶':'♫')+'</span><span class="standardQualityText"><b>'+esc(c.label)+'</b><small>'+esc(c.detail)+'</small></span><span class="qualitySelectMark">Select</span></button>').join(''):'<div class="mediaMeta formatEmpty">No '+(type==='video'?'video':'audio-only')+' formats are available for this source.</div>';
  list.querySelectorAll('.standardQualityChoice').forEach(b=>b.addEventListener('click',()=>{const c=choices[Number(b.dataset.index)];selected={format:c.format,label:c.label,kind:c.kind,type};if(bundleOption)bundleOption.hidden=type!=='video';if(type!=='video'&&bundleCheckbox)bundleCheckbox.checked=false;panel.querySelectorAll('.standardQualityChoice').forEach(row=>{const active=row===b;row.classList.toggle('selected',active);row.setAttribute('aria-pressed',String(active));row.querySelector('.qualitySelectMark').textContent=active?'Selected':'Select'});refreshDownloadAction()}));
 };
 renderChoices('video');renderChoices('audio');
 if(downloadButton)downloadButton.onclick=()=>{if(!selected)return;const baseTitle=info.title||titleFrom(url);if(selected.type==='video'&&bundleCheckbox?.checked){serverDownload(url,{...info,format:selected.format,title:baseTitle+' — Video'});serverDownload(url,{...info,format:'bestaudio/best',title:baseTitle+' — Audio'});nav('downloads');toast('Video and separate audio added to Downloads')}else{serverDownload(url,{...info,format:selected.format,title:selected.type==='audio'?baseTitle+' — Audio':baseTitle+' — Video'});nav('downloads');toast('Download added to queue')}};
}
const activeJobRunners=new Set();
function makeJob(url,mode,info={}){
 const job={id:(crypto.randomUUID?crypto.randomUUID():String(Date.now())+Math.random().toString(16).slice(2)),
  url,title:info.title||titleFrom(url),status:"queued",progress:0,error:"",mode,
  type:info.type||"",format:info.format||null,created:Date.now()};
 jobs.unshift(job);saveJobs();renderJobs();pumpQueue();return job;
}
function serverDownload(url,info={}){return makeJob(url,"server",info)}
function startBrowserDownload(url,type=""){return makeJob(url,"browser",{type,title:titleFrom(url)})}
function pumpQueue(){
 const active=jobs.filter(j=>j.status==="running").length;
 const slots=Math.max(0,ACTIVE_LIMIT-active);
 const queued=jobs.filter(j=>j.status==="queued").slice(0,slots);
 queued.forEach(job=>{job.status="running";job.progress=0;job.error="";saveJobs();renderJobs();runDownloadLifecycle(job)});
}
async function runDownloadLifecycle(job){
 if(activeJobRunners.has(job.id))return;
 activeJobRunners.add(job.id);
 try{
  if(job.mode==="browser"){await startBrowserDownloadNow(job.url,job.type,job);return;}
  const response=await fetch(vgApiUrl("/api/download-jobs"),{method:"POST",headers:{"Content-Type":"application/json"},
    body:JSON.stringify({url:job.url,format:job.format||undefined})});
  const created=await response.json().catch(()=>({}));
  if(!response.ok)throw Error(created.error||`Could not create download job (${response.status})`);
  job.serverJobId=created.id;saveJobs();renderJobs();
  const started=Date.now(), maxWait=15*60*1000;
  while(Date.now()-started<maxWait){
   if(job.cancelRequested)return;
   const statusResponse=await fetch(vgApiUrl("/api/download-jobs/"+encodeURIComponent(job.serverJobId)));
   const state=await statusResponse.json().catch(()=>({}));
   if(!statusResponse.ok)throw Error(state.error||"Could not read download status");
   job.progress=Math.max(0,Math.min(100,Number(state.progress)||0));
   job.message=state.message||"";
   saveJobs();renderJobs();
   if(state.state==="failed")throw Error(state.error||state.message||"Provider download failed");
   if(state.state==="cancelled"){job.status="cancelled";job.error="Cancelled by user";saveJobs();renderJobs();return;}
   if(state.state==="expired")throw Error("Download output expired before retrieval");
   if(state.state==="completed"){
    const result=await fetch(vgApiUrl("/api/download-jobs/"+encodeURIComponent(job.serverJobId)+"/result"));
    if(!result.ok){const e=await result.json().catch(()=>({}));throw Error(e.error||"Could not retrieve completed download");}
    job.message="Transferring completed file to your device…";job.progress=0;saveJobs();renderJobs();
    const total=Number(result.headers.get("content-length")||0),reader=result.body?.getReader(),chunks=[];let received=0;
    if(reader){
     while(true){
      const part=await reader.read();if(part.done)break;
      chunks.push(part.value);received+=part.value.byteLength;
      job.progress=total?Math.min(99,Math.round(received/total*100)):Math.min(95,job.progress+2);
      job.message="Transferring file to your device…";saveJobs();renderJobs();
      if(job.cancelRequested)throw new DOMException("Download cancelled","AbortError");
     }
    }else{chunks.push(new Uint8Array(await result.arrayBuffer()));received=chunks[0].byteLength;}
    const blob=new Blob(chunks,{type:result.headers.get("content-type")||"application/octet-stream"});
    job.message="Verifying media file…";job.progress=99;saveJobs();renderJobs();
    await finishBlob(job,blob,blob.type);
    toast("Verified media saved to your device and Library");return;
   }
   await new Promise(resolve=>setTimeout(resolve,650));
  }
  throw Error("Download timed out while waiting for the provider");
 }catch(e){
  if(job.cancelRequested){job.status="cancelled";job.error="Cancelled by user";}
  else{job.status="failed";job.error=e?.message||"Download failed";job.progress=0;}
  saveJobs();renderJobs();
 }finally{
  activeJobRunners.delete(job.id);
  if(job.status==="running")job.status="failed";
  saveJobs();renderJobs();pumpQueue();
 }
}
async function serverDownloadNow(url,info={}){return serverDownload(url,info)}
async function startBrowserDownloadNow(url,type,providedJob){
 const job=providedJob||jobs.find(j=>j.url===url&&j.status==="running");if(!job)return;
 const controller=new AbortController();activeControllers.set(job.id,controller);
 try{
  const response=await fetch(url,{signal:controller.signal});if(!response.ok)throw Error(`HTTP ${response.status}`);
  const total=Number(response.headers.get("content-length")||0),reader=response.body?.getReader();
  const chunks=[];let received=0;
  if(reader){while(true){const {done,value}=await reader.read();if(done)break;chunks.push(value);received+=value.byteLength;
   job.progress=total?Math.min(99,Math.round(received/total*100)):Math.min(95,job.progress+3);job.message="Downloading media from source…";saveJobs();renderJobs();}}
  else chunks.push(new Uint8Array(await response.arrayBuffer()));
  if(job.cancelRequested)return;
  const blob=new Blob(chunks,{type:response.headers.get("content-type")||type||"application/octet-stream"});
  await finishBlob(job,blob,blob.type);toast("Verified media saved to your device and Library");
 }catch(e){
  if(e.name==="AbortError"||job.cancelRequested){job.status="cancelled";job.error="Cancelled by user";}
  else{job.status="failed";job.error=e.message||"Browser download failed";job.progress=0;}
  saveJobs();renderJobs();
 }finally{activeControllers.delete(job.id);activeJobRunners.delete(job.id);pumpQueue();}
}
async function cancelJob(id){
 const job=jobs.find(j=>j.id===id);if(!job)return;
 if(job.status==="queued"){job.status="cancelled";job.error="Cancelled before starting";saveJobs();renderAll();return;}
 if(job.status!=="running")return;
 job.cancelRequested=true;
 const controller=activeControllers.get(id);if(controller)controller.abort();
 if(job.serverJobId){try{await fetch(vgApiUrl("/api/download-jobs/"+encodeURIComponent(job.serverJobId)+"/cancel"),{method:"POST"});}catch{}}
 job.status="cancelled";job.error="Cancellation requested";saveJobs();renderJobs();pumpQueue();
}
async function retryJob(id){
 const job=jobs.find(j=>j.id===id);if(!job||!["failed","cancelled","recoverable"].includes(job.status))return;
 job.status="queued";job.progress=0;job.error="";job.serverJobId=null;job.cancelRequested=false;
 saveJobs();renderJobs();pumpQueue();
}
async function extractMetadata(blob,type,title){
 return await new Promise(resolve=>{
  const item={duration:0,width:0,height:0,thumbnail:''};
  if(!/^video\//i.test(blob.type||type||'')){resolve(item);return}
  let settled=false,objectUrl='';
  const video=document.createElement('video');
  const finish=()=>{if(settled)return;settled=true;clearTimeout(timer);if(objectUrl)URL.revokeObjectURL(objectUrl);video.removeAttribute('src');video.load();resolve(item)};
  const timer=setTimeout(finish,3500);
  try{
   objectUrl=URL.createObjectURL(blob);video.preload='metadata';video.muted=true;video.playsInline=true;
   video.onloadedmetadata=()=>{
    item.duration=Number.isFinite(video.duration)?video.duration:0;
    item.width=video.videoWidth||0;item.height=video.videoHeight||0;
    if(!item.width||!item.height||!Number.isFinite(video.duration)||video.duration<=0){finish();return}
    try{video.currentTime=Math.min(1,Math.max(0,video.duration/2))}catch{finish()}
   };
   video.onseeked=()=>{
    try{
     const canvas=document.createElement('canvas');canvas.width=320;
     canvas.height=Math.max(180,Math.round(320*(video.videoHeight/video.videoWidth||9/16)));
     const context=canvas.getContext('2d');if(context){context.drawImage(video,0,0,canvas.width,canvas.height);item.thumbnail=canvas.toDataURL('image/jpeg',.72)}
    }catch{}
    finish();
   };
   video.onerror=finish;video.src=objectUrl;
  }catch{finish()}
 });
}
async function detectMediaSignature(blob){
 const bytes=new Uint8Array(await blob.slice(0,32).arrayBuffer());
 const ascii=(start,end)=>String.fromCharCode(...bytes.slice(start,end));
 if(bytes.length>=12&&ascii(4,8)==="ftyp")return "video/mp4";
 if(bytes.length>=4&&bytes[0]===0x1A&&bytes[1]===0x45&&bytes[2]===0xDF&&bytes[3]===0xA3)return "video/webm";
 if(bytes.length>=12&&ascii(0,4)==="RIFF"&&ascii(8,12)==="AVI ")return "video/x-msvideo";
 if(bytes.length>=12&&ascii(0,4)==="RIFF"&&ascii(8,12)==="WAVE")return "audio/wav";
 if(bytes.length>=3&&ascii(0,3)==="ID3")return "audio/mpeg";
 if(bytes.length>=2&&bytes[0]===0xFF&&(bytes[1]&0xE0)===0xE0)return "audio/mpeg";
 if(bytes.length>=4&&ascii(0,4)==="OggS")return "audio/ogg";
 if(bytes.length>=4&&ascii(0,4)==="fLaC")return "audio/flac";
 return "";
}
function localFileName(title,type,url){
 let name=String(title||'vidgravity-media').replace(/[\\/:*?"<>|\\x00-\\x1f]/g,'_').trim()||'vidgravity-media';
 const extByMime={'video/mp4':'.mp4','video/webm':'.webm','video/quicktime':'.mov','video/x-matroska':'.mkv','video/x-msvideo':'.avi','audio/mpeg':'.mp3','audio/mp4':'.m4a','audio/aac':'.aac','audio/ogg':'.ogg','audio/wav':'.wav'};
 const mime=String(type||'').split(';')[0].toLowerCase();
 if(!/\\.[a-z0-9]{2,5}$/i.test(name))name+=extByMime[mime]||(()=>{try{return new URL(url,location.href).pathname.match(/\\.[a-z0-9]{2,5}$/i)?.[0]||'.media'}catch{return'.media'}})();
 return name;
}
function downloadBlobToDevice(blob,title,type,url){
 const objectUrl=URL.createObjectURL(blob),a=document.createElement('a');
 a.href=objectUrl;a.download=localFileName(title,type,url);a.style.display='none';
 document.body.appendChild(a);a.click();a.remove();
 setTimeout(()=>URL.revokeObjectURL(objectUrl),120000);
}
async function finishBlob(job,blob,type){
 const detectedType=await detectMediaSignature(blob);
 if(!detectedType)throw Error("The downloaded response is not a recognized media file. It was not marked completed.");
 const mediaType=detectedType,normalized=blob.type===mediaType?blob:blob.slice(0,blob.size,mediaType);
 if(!normalized.size)throw Error("The downloaded media file is empty.");
 job.message="Saving verified media to your device…";job.progress=99;saveJobs();renderJobs();
 const meta=await extractMetadata(normalized,mediaType,job.title);
 const item={id:job.id,url:job.url,type:mediaType,title:job.title,size:normalized.size,created:Date.now(),blob:normalized,...meta};
 await dbPut(item);
 downloadBlobToDevice(normalized,item.title,item.type,item.url);
 job.status='completed';job.progress=100;job.message='Verified and saved to device and Library';job.mediaId=item.id;saveJobs();media.unshift(item);renderAll();
}
function titleFrom(url){try{return decodeURIComponent(new URL(url).pathname.split('/').pop()||'Untitled media')}catch{return'Untitled media'}}
function renderAll(){renderStats();renderRecent();renderLibrary();renderJobs();$('#queueBadge').textContent=jobs.filter(j=>['running','queued'].includes(j.status)).length}
function renderStats(){$('#statLibrary').textContent=media.length;$('#statDownloads').textContent=jobs.filter(j=>j.status==='completed').length;$('#statStorage').textContent=formatBytes(media.reduce((n,m)=>n+(m.size||0),0))}
function card(m){const thumb=m.thumbnail?`<img src="${m.thumbnail}" alt="" loading="lazy">`:`<span>${iconFor(m.type)}</span>`;const detail=[m.duration?formatDuration(m.duration):'',m.width&&m.height?`${m.width}×${m.height}`:'',formatBytes(m.size)].filter(Boolean).join(' · ');return `<article class="media card"><div class="selectRow"><input type="checkbox" class="mediaSelect" data-id="${m.id}"></div><div class="thumb">${thumb}</div><div class="mediaBody"><div class="mediaTitle" title="${esc(m.title)}">${esc(m.title)}</div><div class="mediaMeta">${esc(m.type||'media')} · ${detail||'metadata pending'}</div><div class="actions"><button class="smallBtn play" data-id="${m.id}" type="button"><span class="actionIcon" aria-hidden="true">▶</span><span>Play</span></button><button class="smallBtn info" data-id="${m.id}" type="button"><span class="actionIcon" aria-hidden="true">ⓘ</span><span>Details</span></button><button class="smallBtn shareMedia" data-id="${m.id}" type="button"><span class="actionIcon" aria-hidden="true">↗</span><span>Share</span></button><button class="smallBtn del" data-id="${m.id}">Delete</button></div></div></article>`}
function iconFor(type=''){return /audio/i.test(type)?'♫':/image/i.test(type)?'▧':/video|mp4|webm|quicktime|matroska|avi/i.test(type)?'🎬':'🎞️'}
function renderRecent(){const x=$('#recentGrid');x.innerHTML=media.slice(0,4).map(card).join('')||empty('No media yet. Paste a direct media URL above.');bindCards(x)}

function orgState(){
  try{return JSON.parse(localStorage.getItem("vg-org")||'{"collections":["All"],"active":"All"}')}
  catch{return {collections:["All"],active:"All"}}
}
function saveOrg(s){localStorage.setItem("vg-org",JSON.stringify(s))}
function ensureOrg(m){if(m.favorite===undefined)m.favorite=false;if(!Array.isArray(m.tags))m.tags=[];if(!m.collection)m.collection="All"}
media.forEach(ensureOrg); save();

function renderCollections(){
 const s=orgState(); if(!s.collections.includes("All"))s.collections.unshift("All");
 const bar=$("#collectionBar"); if(!bar)return;
 bar.innerHTML=s.collections.map(c=>`<button class="collection ${c===s.active?"active":""}" data-c="${esc(c)}">${esc(c)}</button>`).join("");
 bar.querySelectorAll(".collection").forEach(b=>b.onclick=()=>{s.active=b.dataset.c;saveOrg(s);renderCollections();renderLibrary()});
}
function filteredMedia(){
 const q=($("#librarySearch")?.value||"").toLowerCase();
 const f=$("#libraryFilter")?.value||"all";
 const tag=($("#libraryTag")?.value||"").trim().toLowerCase();
 const s=orgState();
 return media.filter(m=>{
   ensureOrg(m);
   const searchable=(m.title+" "+m.url+" "+(m.tags||[]).join(" ")).toLowerCase();
   const type=(m.type||"").toLowerCase();
   return (!q||searchable.includes(q))
    && (!tag||(m.tags||[]).some(t=>t.toLowerCase().includes(tag)))
    && (f==="all"||(f==="favorites"&&m.favorite)||(f==="video"&&type.includes("video"))||(f==="audio"&&type.includes("audio")))
    && (s.active==="All"||m.collection===s.active);
 });
}
function toggleFavorite(id){const m=media.find(x=>x.id===id);if(!m)return;ensureOrg(m);m.favorite=!m.favorite;save();renderLibrary();toast(m.favorite?"Added to favorites":"Removed from favorites")}
function showDetailsModal(id){
 const m=media.find(x=>x.id===id);if(!m)return;ensureOrg(m);
 $("#detailsContent").innerHTML=`<div class="eyebrow" data-media-id="${m.id}">MEDIA DETAILS</div>
 <h2>${esc(m.title)}</h2>
 <div class="transcriptStatus">${transcriptFor(m.id)?`<span class="pill">TRANSCRIPT AVAILABLE</span> <button class="smallBtn" id="removeTranscript">Remove transcript</button>`:`<span class="pill muted">NO TRANSCRIPT</span>`}</div>
 <div class="detailGrid">
   <div><small>Type</small><b>${esc(m.type||"unknown")}</b></div>
   <div><small>Size</small><b>${formatBytes(m.size)}</b></div>
   <div><small>Collection</small><b>${esc(m.collection||"All")}</b></div>
   <div><small>Created</small><b>${new Date(m.created).toLocaleString()}</b></div>
 </div>
 <p class="detailUrl">${esc(m.url)}</p>
 <div class="tagEditor"><input id="detailTags" placeholder="Tags, comma separated" value="${esc((m.tags||[]).join(", "))}"><button class="primary" id="saveDetails">Save</button></div>`;
 $("#detailsModal").classList.remove("hidden");$("#detailsModal").setAttribute("aria-hidden","false");
 $("#saveDetails").onclick=()=>{m.tags=$("#detailTags").value.split(",").map(x=>x.trim()).filter(Boolean).slice(0,20);save();$("#detailsModal").classList.add("hidden");renderLibrary();toast("Media details saved")};
}
function renderLibrary(){ 
 renderCollections();
 const x=$("#libraryGrid"); x.innerHTML=filteredMedia().map(m=>{
   ensureOrg(m);
   return `<article class="media card"><div class="thumb">▶</div><div class="mediaBody"><div class="mediaTitle" title="${esc(m.title)}">${m.favorite?"★ ":""}${esc(m.title)}</div><div class="mediaMeta">${esc(m.type||"media")} · ${formatBytes(m.size)} · ${esc(m.collection||"All")}</div><div class="tagLine">${(m.tags||[]).slice(0,4).map(t=>`<span>#${esc(t)}</span>`).join("")}</div><div class="actions"><button class="smallBtn play" data-id="${m.id}">Play</button><button class="smallBtn favorite" data-id="${m.id}">${m.favorite?"Unfavorite":"Favorite"}</button><button class="smallBtn details" data-id="${m.id}">Details</button><button class="smallBtn shareMedia" data-id="${m.id}" type="button">Share</button></div></div></article>`
 }).join("")||empty("No media matches the current filters.");
 bindCards(x);
 x.querySelectorAll(".favorite").forEach(b=>b.onclick=()=>toggleFavorite(b.dataset.id));
 x.querySelectorAll(".details").forEach(b=>b.onclick=()=>showDetails(b.dataset.id));
}


document.addEventListener("click",event=>{const button=event.target.closest(".play");if(!button)return;event.preventDefault();event.stopImmediatePropagation();play(button.dataset.id)},true);

let shareMediaId=null;
function getShareItem(id){return id==='__app__'?{id:'__app__',title:'VidGravity',url:location.href,type:'Web app',size:0}:media.find(m=>m.id===id)}
function openShare(id){
 const item=getShareItem(id);if(!item)return toast('Media item was not found');
 shareMediaId=id;
 const modal=$('#shareModal');if(!modal)return shareMedia(id);
 $('#sharePreviewTitle').textContent=item.title||'Media';
 $('#sharePreviewMeta').textContent=[item.type||'Media',item.duration?formatDuration(item.duration):'',formatBytes(item.size)].filter(Boolean).join(' · ');
 const image=$('#sharePreviewImage');if(image){if(item.thumbnail){image.src=item.thumbnail;image.hidden=false}else{image.removeAttribute('src');image.hidden=true}}
 const url=$('#sharePreviewUrl');if(url)url.textContent=item.url||'Local media file';
 modal.classList.remove('hidden');modal.setAttribute('aria-hidden','false');
}
async function shareMedia(id=shareMediaId){
 const item=getShareItem(id);if(!item)return toast('Media item was not found');
 try{
  const stored=id==='__app__'?null:await dbGet(id);
  const file=stored?.blob instanceof Blob&&stored.blob.size?new File([stored.blob],(item.title||'vidgravity-media').replace(/[\\/:*?"<>|]+/g,'_'),{type:stored.blob.type||'application/octet-stream'}):null;
  if(navigator.share){
   const payload={title:item.title||'VidGravity media',text:'Shared from VidGravity',url:item.url||undefined};
   if(file&&navigator.canShare?.({files:[file]})){payload.files=[file];delete payload.url}
   await navigator.share(payload);return true;
  }
  if(item.url&&navigator.clipboard?.writeText){await navigator.clipboard.writeText(item.url);toast('Media link copied to the clipboard');return true}
  toast('System sharing is unavailable here. Use a supported browser or copy a shareable URL.');return false;
 }catch(error){if(error?.name==='AbortError')return false;toast(error?.message||'Could not share this media');return false}
}
function shareTo(target){
 const item=getShareItem(shareMediaId);if(!item)return;
 const url=item.url||'';const title=item.title||'VidGravity media';let targetUrl='';
 if(target==='x')targetUrl='https://twitter.com/intent/tweet?'+new URLSearchParams({text:title,url});
 if(target==='whatsapp')targetUrl='https://wa.me/?text='+encodeURIComponent([title,url].filter(Boolean).join(' '));
 if(target==='telegram')targetUrl='https://t.me/share/url?'+new URLSearchParams({url,text:title});
 if(target==='reddit')targetUrl='https://www.reddit.com/submit?'+new URLSearchParams({url,title});
 if(target==='linkedin')targetUrl='https://www.linkedin.com/sharing/share-offsite/?url='+encodeURIComponent(url);
 if(target!=='whatsapp'&&target!=='x'&&!url)return toast('This social target needs a shareable URL');
 window.open(targetUrl,'_blank','noopener,noreferrer');
}
$('#shareAppButton')?.addEventListener('click',()=>openShare('__app__'));
$('#shareDeviceButton')?.addEventListener('click',()=>shareMedia());
$('#shareCopyButton')?.addEventListener('click',async()=>{const item=media.find(m=>m.id===shareMediaId);if(!item?.url)return toast('No shareable URL is available for this media');try{await navigator.clipboard.writeText(item.url);toast('Link copied')}catch{toast('Clipboard access is unavailable in this context')}});
$$('[data-share-target]').forEach(button=>button.addEventListener('click',()=>shareTo(button.dataset.shareTarget)));
$('#closeShare')?.addEventListener('click',()=>{$('#shareModal')?.classList.add('hidden');$('#shareModal')?.setAttribute('aria-hidden','true')});
$('#shareModal')?.addEventListener('click',event=>{if(event.target===$('#shareModal')){$('#shareModal').classList.add('hidden');$('#shareModal').setAttribute('aria-hidden','true')}});
$('#playerShare')?.addEventListener('click',()=>{if(activeMediaId)openShare(activeMediaId);else toast('Open a saved media item before sharing')});

function bindCards(root){root.querySelectorAll('.play').forEach(b=>b.onclick=()=>play(b.dataset.id));root.querySelectorAll('.save').forEach(b=>b.onclick=()=>saveFile(b.dataset.id));root.querySelectorAll('.info').forEach(b=>b.onclick=()=>showDetails(b.dataset.id));root.querySelectorAll('.shareMedia').forEach(b=>b.onclick=()=>openShare(b.dataset.id));root.querySelectorAll('.del').forEach(b=>b.onclick=()=>removeMedia(b.dataset.id))}
function empty(t){return `<div class="card" style="padding:25px;color:#777083;grid-column:1/-1">${t}</div>`}
function renderJobs(){const x=$('#downloadList');const completed=media.slice().sort((a,b)=>(b.created||0)-(a.created||0));const finished=completed.map(m=>{const icon=m.thumbnail?`<img src="${esc(m.thumbnail)}" alt="" loading="lazy">`:`<span class="downloadFileIcon" aria-hidden="true">${iconFor(m.type)}</span>`;return `<article class="downloadItem card"><div class="downloadThumb">${icon}</div><div class="downloadItemMain"><div class="downloadItemTitle" title="${esc(m.title)}">${esc(m.title)}</div><div class="downloadItemMeta"><span class="downloadTypeIcon" aria-hidden="true">${/audio/i.test(m.type||"")?"♫":"▣"}</span><span>${esc((m.type||"media").split("/").pop().toUpperCase())}</span><span>·</span><strong>${formatBytes(m.size)}</strong>${m.duration?`<span>· ${formatDuration(m.duration)}</span>`:""}</div><div class="downloadItemDate">Downloaded ${new Date(m.created).toLocaleString()}</div><div class="actions downloadItemActions"><button class="smallBtn play" data-id="${m.id}" type="button"><span class="actionIcon">▶</span><span>Play</span></button><button class="smallBtn renameMedia" data-id="${m.id}" type="button"><span class="actionIcon">✎</span><span>Rename</span></button><button class="smallBtn info" data-id="${m.id}" type="button"><span class="actionIcon">ⓘ</span><span>Details</span></button><button class="smallBtn shareMedia" data-id="${m.id}" type="button"><span class="actionIcon">↗</span><span>Share</span></button><button class="smallBtn del" data-id="${m.id}" type="button"><span class="actionIcon">×</span><span>Delete</span></button></div></div></article>`}).join("");const active=jobs.filter(j=>!['completed'].includes(j.status)).map(j=>`<div class="job card"><div><div class="jobTitle">${esc(j.title)}</div><div class="jobUrl">${esc(j.url)}</div>${j.message?`<div class="jobUrl jobMessage">${esc(j.message)}</div>`:''}<div class="bar"><i style="width:${Math.max(0,Math.min(100,Number(j.progress)||0))}%"></i></div></div><div class="jobStatus">${esc(j.status)}${j.error?`<br>${esc(j.error)}`:''}</div><div class="jobStatus">${Math.max(0,Math.min(100,Number(j.progress)||0))}%<div class="actions jobActions">${['running','queued','recoverable'].includes(j.status)?`<button class="smallBtn cancelJob" data-id="${j.id}">Cancel</button>`:''}${['failed','cancelled','recoverable'].includes(j.status)?`<button class="smallBtn retryJob" data-id="${j.id}">Retry</button>`:''}</div></div></div>`).join("");x.innerHTML=`<div class="downloadsIntro"><div><div class="eyebrow">YOUR FILES</div><h2>Completed downloads <span class="downloadCount">${completed.length}</span></h2><p>Play, rename, inspect details, or remove any saved file.</p></div><div class="bulkBar"><button class="smallBtn" id="retryFailed">Retry failed</button><button class="smallBtn" id="cancelQueued">Cancel queued</button></div></div><div class="downloadsGrid">${finished||empty('Your completed videos and audio files will appear here with file size and format.')}</div>${active?`<div class="downloadsQueueTitle"><h2>Download activity</h2><span>Active and unsuccessful jobs</span></div><div class="list">${active}</div>`:""}`;bindCards(x);x.querySelectorAll('.renameMedia').forEach(b=>b.onclick=()=>renameMedia(b.dataset.id));x.querySelectorAll('.cancelJob').forEach(b=>b.onclick=()=>cancelJob(b.dataset.id));x.querySelectorAll('.retryJob').forEach(b=>b.onclick=()=>retryJob(b.dataset.id));$('#retryFailed')?.addEventListener('click',retryFailed);$('#cancelQueued')?.addEventListener('click',cancelQueued)}
async function renameMedia(id){const m=media.find(x=>x.id===id);if(!m)return;const previous=m.title||"Video";const proposed=prompt("Rename downloaded media:",previous);if(proposed===null)return;const title=proposed.trim();if(!title)return toast("Name cannot be empty");m.title=title;await dbPut(m);const job=jobs.find(j=>j.id===id||j.mediaId===id);if(job){job.title=title;saveJobs()}renderAll();toast("Media renamed")}

async function retryFailed(){const failed=jobs.filter(j=>j.status==='failed');if(!failed.length)return toast('No failed jobs');for(const j of failed){j.status='queued';j.error='';j.progress=0}saveJobs();renderJobs();pumpQueue();toast(`Retrying ${failed.length} job(s)`)}
function cancelQueued(){const before=jobs.length;jobs=jobs.filter(j=>!['queued','recoverable'].includes(j.status));saveJobs();renderAll();toast(`${before-jobs.length} queued job(s) cancelled`)}

let activePlayerObjectUrl=null;
let activeMediaId=null;
async function play(id,seek=0){
 const item=await dbGet(id);
 if(!item||!(item.blob instanceof Blob)||!item.blob.size)return toast('Saved media file was not found in the Library');
 const player=$("#player"),modal=$("#playerModal");
 if(!player||!modal)return toast('The media player is unavailable');
 player.pause();activeMediaId=id;
 if(activePlayerObjectUrl){URL.revokeObjectURL(activePlayerObjectUrl);activePlayerObjectUrl=null}
 activePlayerObjectUrl=URL.createObjectURL(item.blob);
 player.removeAttribute('src');
 player.src=activePlayerObjectUrl;
 player.load();
 $("#playerMeta").textContent=item.title||'Media';
 const transcript=transcriptFor(id);
 $("#playerTranscript").innerHTML=transcript?'<div class="ptHead"><b>Transcript</b><small>'+transcript.segments.length+' segments · '+esc(transcript.source)+'</small></div><div class="ptList">'+transcript.segments.map(s=>'<button class="ptSeg" data-time="'+Number(s.start||0)+'"><span>'+formatTime(s.start)+'</span>'+esc(s.text)+'</button>').join('')+'</div>':'<div class="ptEmpty">No transcript or captions available for this media.</div>';
 modal.classList.remove('hidden');
 modal.setAttribute('aria-hidden','false');
 player.preload='auto';
 player.onloadedmetadata=()=>{
   if(Number.isFinite(player.duration)&&player.duration>0)player.currentTime=Math.min(Math.max(0,Number(seek)||0),player.duration);
 };
 player.onerror=()=>{const code=player.error?.code;toast(code===4?'This file format or codec is not supported by this browser. Try downloading MP4/WebM.':'The saved media could not be decoded. Try another available quality.');};
 player.play().catch(error=>{toast(error?.name==='NotAllowedError'?'Press Play in the player to start playback.':'This format may not be supported by your browser. Try an MP4 or WebM version.');});
 $("#playerTranscript").querySelectorAll(".ptSeg").forEach(button=>button.onclick=()=>{
   player.currentTime=Number(button.dataset.time)||0;
   player.play().catch(()=>{});
 });
}
function closePlayer(){
 const player=$("#player"),modal=$("#playerModal");
 if(player){player.pause();player.onerror=null;player.onloadedmetadata=null;player.removeAttribute("src");player.srcObject=null;player.playbackRate=1;player.load();const speed=$("#playbackSpeed");if(speed)speed.value="1"}
 if(activePlayerObjectUrl){URL.revokeObjectURL(activePlayerObjectUrl);activePlayerObjectUrl=null;}
 if(modal){modal.classList.add("hidden");modal.setAttribute("aria-hidden","true");modal.style.display=""}
}
$("#closePlayer")?.addEventListener('click',closePlayer);
$("#playerModal")?.addEventListener("click",event=>{if(event.target===$("#playerModal"))closePlayer()});
$("#playerModal .modalBox")?.addEventListener("click",event=>event.stopPropagation());
$("#playbackSpeed")?.addEventListener("change",event=>{const speed=Number(event.target.value);if(Number.isFinite(speed)&&speed>0)$("#player").playbackRate=speed});
$("#playerFullscreen")?.addEventListener("click",async()=>{const video=$("#player");try{if(document.fullscreenElement)await document.exitFullscreen();else if(video.requestFullscreen)await video.requestFullscreen();else toast("Fullscreen is not supported in this browser")}catch{toast("Fullscreen could not be opened")}});
$("#playerPip")?.addEventListener("click",async()=>{const video=$("#player");try{if(document.pictureInPictureElement)await document.exitPictureInPicture();else if(document.pictureInPictureEnabled&&video.requestPictureInPicture)await video.requestPictureInPicture();else toast("Picture-in-picture is not supported on this device")}catch{toast("Picture-in-picture is unavailable for this file")}});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$("#playerModal")?.classList.contains('hidden'))closePlayer()});

async function saveFile(id){const m=await dbGet(id);if(!m||!(m.blob instanceof Blob))return toast('Saved media file was not found in the Library');downloadBlobToDevice(m.blob,m.title,m.type,m.url);toast('A copy is being saved to your device')}
async function removeMedia(id){if(!confirm('Delete this downloaded media from VidGravity and its Library?'))return;await dbDelete(id);media=media.filter(m=>m.id!==id);jobs=jobs.filter(j=>j.mediaId!==id&&j.id!==id);saveJobs();renderAll();toast('Media deleted')}
function formatBytes(n){if(!n)return'0 B';const u=['B','KB','MB','GB'];let i=0;while(n>=1024&&i<3){n/=1024;i++}return n.toFixed(i?1:0)+' '+u[i]}
function formatDuration(s){s=Math.max(0,Math.round(Number(s)||0));const h=Math.floor(s/3600),m=Math.floor(s%3600/60),sec=s%60;return h?`${h}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${m}:${String(sec).padStart(2,'0')}`}
async function showDetails(id){const m=media.find(x=>x.id===id)||await dbGet(id);if(!m)return;ensureOrg(m);$("#detailsContent").innerHTML=`<div class="eyebrow">DOWNLOADED MEDIA DETAILS</div><h2>${esc(m.title)}</h2><div class="detailGrid"><div><small>Media type</small><b>${esc(m.type||"unknown")}</b></div><div><small>File size</small><b>${formatBytes(m.size)}</b></div><div><small>Duration</small><b>${m.duration?formatDuration(m.duration):"Not available"}</b></div><div><small>Resolution</small><b>${m.width&&m.height?`${m.width} × ${m.height}`:"Not available"}</b></div><div><small>Downloaded</small><b>${new Date(m.created).toLocaleString()}</b></div><div><small>File format</small><b>${esc((m.type||"").split("/").pop()||"Unknown")}</b></div></div><p class="detailUrl">${esc(m.url||"")}</p><div class="tagEditor"><label for="detailTags">Tags</label><input id="detailTags" placeholder="Tags, comma separated" value="${esc((m.tags||[]).join(", "))}"><button class="primary" id="saveDetails">Save details</button></div>`;$("#detailsModal").classList.remove("hidden");$("#detailsModal").setAttribute("aria-hidden","false");$("#saveDetails").onclick=()=>{m.tags=$("#detailTags").value.split(",").map(x=>x.trim()).filter(Boolean).slice(0,20);dbPut(m).then(()=>{save();$("#detailsModal").classList.add("hidden");renderAll();toast("Media details saved")})}}
$('#librarySearch').oninput=renderLibrary;
$('#askBtn').onclick=ask;$('#askInput').onkeydown=e=>{if(e.key==='Enter')ask()}


function parseCaptionFile(text){
 const lines=text.replace(/\r/g,"").split("\n"), out=[]; let current=null;
 for(let i=0;i<lines.length;i++){
   const line=lines[i].trim();
   if(!line)continue;
   const tm=line.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2})(?::(\d{2}))?[,.](\d{3})/);
   if(tm){
     const a=tm[3]?Number(tm[1])*3600+Number(tm[2])*60+Number(tm[3])+Number(tm[4])/1000:Number(tm[1])*60+Number(tm[2])+Number(tm[4])/1000;
     current={start:a,text:""};out.push(current);continue;
   }
   if(current && !/^(WEBVTT|NOTE|\d+)$/.test(line))current.text+=(current.text?" ":"")+line;
 }
 return out.filter(x=>x.text).slice(0,2000);
}
function importCaptionFile(){
 const id=$("#transcriptMedia").value.trim();if(!id)return toast("Enter a media ID");
 if(!media.find(x=>x.id===id))return toast("Media ID not found");
 const input=document.createElement("input");input.type="file";input.accept=".vtt,.srt,.txt,text/vtt,text/plain";
 input.onchange=async()=>{const f=input.files?.[0];if(!f)return;const text=await f.text();const segments=/WEBVTT/i.test(text)||/-->/.test(text)?parseCaptionFile(text):parseTranscriptText(text);const s=transcriptStore();s[id]={mediaId:id,source:f.name,created:Date.now(),segments};saveTranscriptStore(s);toast(`Imported ${segments.length} caption segments`)};
 input.click();
}
function transcriptFor(id){return transcriptStore()[id]||null}
function deleteTranscript(id){const s=transcriptStore();if(!s[id])return;delete s[id];saveTranscriptStore(s);toast("Transcript removed")}


function indexStore(){try{return JSON.parse(localStorage.getItem("vg-index")||"{}")}catch{return {}}}
function saveIndex(s){localStorage.setItem("vg-index",JSON.stringify(s))}
function buildIndex(){
 const ts=transcriptStore(), idx={version:1,builtAt:Date.now(),items:{}};
 media.forEach(m=>{
   ensureOrg(m);
   const t=ts[m.id];
   idx.items[m.id]={
     id:m.id,title:m.title||"",url:m.url||"",type:m.type||"",size:m.size||0,
     created:m.created||0,favorite:!!m.favorite,collection:m.collection||"All",
     tags:m.tags||[],transcript:!!t,transcriptSource:t?.source||"",segments:t?.segments?.length||0,
     text:t?t.segments.map(s=>s.text).join(" "):""
   };
 });
 saveIndex(idx);renderIndexStatus();return idx;
}
function renderIndexStatus(){
 const idx=indexStore(), n=Object.keys(idx.items||{}).length;
 const tx=Object.values(idx.items||{}).filter(x=>x.transcript).length;
 const el=$("#indexStatus");if(el)el.innerHTML=`<span>Index: ${n} media</span><span>${tx} with transcript</span><span>${idx.builtAt?new Date(idx.builtAt).toLocaleTimeString():"not built"}</span>`;
}
function indexedSearch(q){
 const idx=indexStore();if(!Object.keys(idx.items||{}).length)buildIndex();
 const i=indexStore(), tokens=q.toLowerCase().split(/[^a-z0-9]+/).filter(x=>x.length>1);
 return Object.values(i.items||{}).map(x=>{
   const hay=[x.title,x.url,x.type,x.collection,(x.tags||[]).join(" "),x.text].join(" ").toLowerCase();
   const score=tokens.reduce((n,t)=>n+(hay.includes(t)?1:0),0);
   return {x,score};
 }).filter(r=>r.score>0).sort((a,b)=>b.score-a.score).slice(0,12).map(r=>r.x);
}

function transcriptStore(){try{return JSON.parse(localStorage.getItem("vg-transcripts")||"{}")}catch{return {}}}
function saveTranscriptStore(s){localStorage.setItem("vg-transcripts",JSON.stringify(s))}
function parseTranscriptText(text){return text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean).slice(0,500).map((line,i)=>{const m=line.match(/^\[(\d+):(\d+(?:\.\d+)?)\]\s*(.*)$/);return m?{start:Number(m[1])*60+Number(m[2]),text:m[3]}:{start:i,text:line}})}
function addTranscript(){const id=$("#transcriptMedia").value.trim();if(!id)return toast("Enter a media ID");const m=media.find(x=>x.id===id);if(!m)return toast("Media ID not found");const text=prompt("Paste transcript/caption text. Optional timestamps: [mm:ss] text");if(!text)return;const s=transcriptStore();s[id]={mediaId:id,source:"user/local",created:Date.now(),segments:parseTranscriptText(text)};saveTranscriptStore(s);$("#transcriptMedia").value="";toast("Transcript saved")}
function transcriptSearch(q){const s=transcriptStore(),hits=[];Object.values(s).forEach(t=>t.segments.forEach(seg=>{if(seg.text.toLowerCase().includes(q.toLowerCase()))hits.push({mediaId:t.mediaId,source:t.source,start:seg.start,text:seg.text})}));return hits.slice(0,12)}
function transcriptEvidence(hits){return hits.map(h=>{const m=media.find(x=>x.id===h.mediaId);return `<div class="evidence"><div><b>${esc(m?.title||h.mediaId)}</b><small>${formatTime(h.start)} · ${esc(h.text)} · ${esc(h.source)}</small></div><button class="smallBtn evidenceOpen" data-id="${esc(h.mediaId)}" data-time="${h.start}">Open</button></div>`}).join("")}
function formatTime(sec){sec=Math.max(0,Number(sec)||0);return `${String(Math.floor(sec/60)).padStart(2,"0")}:${String(Math.floor(sec%60)).padStart(2,"0")}`}

function classifyAsk(q){
 const l=q.toLowerCase();
 if(/how many|count|number/.test(l))return"count";
 if(/recent|latest|newest/.test(l))return"recent";
 if(/favorite|favourite/.test(l))return"favorite";
 if(/collection|folder/.test(l))return"collection";
 if(/transcript|caption|subtitle|said|mentioned|spoken/.test(l))return"transcript";
 if(/audio/.test(l))return"audio";
 if(/video/.test(l))return"video";
 return"search";
}
function rankedEvidence(q){
 const hits=indexedSearch(q);
 return hits.map(x=>({x,score:1}));
}
function answerFromIndex(q){
 const all=media.map(m=>{ensureOrg(m);return m});
 if(!all.length)return {text:"Your local library is empty. Download media first.",items:[]};
 const type=classifyAsk(q), l=q.toLowerCase();
 if(type==="recent"){
   const items=[...all].sort((a,b)=>b.created-a.created).slice(0,5);
   return {text:`Your ${items.length} most recent media item${items.length===1?"":"s"}:`,items};
 }
 if(type==="favorite"){
   const items=all.filter(m=>m.favorite);
   return {text:items.length?`You have ${items.length} favorite item${items.length===1?"":"s"}.`:"You don't have any favorite media yet.",items};
 }
 if(type==="video"||type==="audio"){
   const items=all.filter(m=>(m.type||"").toLowerCase().includes(type));
   return {text:`I found ${items.length} ${type} item${items.length===1?"":"s"} in your library.`,items};
 }
 if(type==="collection"){
   const groups={};all.forEach(m=>{const k=m.collection||"All";groups[k]=(groups[k]||0)+1});
   return {text:Object.entries(groups).map(([k,v])=>`${k}: ${v}`).join(" · "),items:all.slice(0,8)};
 }
 if(type==="transcript"){
   const hits=transcriptSearch(l.replace(/\b(transcript|caption|captions|subtitle|subtitles|said|mentioned|spoken)\b/g," ").trim());
   return {text:hits.length?`I found ${hits.length} transcript evidence match${hits.length===1?"":"es"}.`:"No matching local transcript evidence was found.",items:hits.map(h=>media.find(m=>m.id===h.mediaId)).filter(Boolean),transcriptHits:hits};
 }
 const items=rankedEvidence(q).map(r=>r.x);
 return {text:items.length?`I found ${items.length} relevant local media item${items.length===1?"":"s"}.`:"I couldn't find relevant media in the local index.",items};
}

function actionHistory(){try{return JSON.parse(localStorage.getItem("vg_action_history")||"[]")}catch(e){return[]}}
function recordAction(type,target,details={}){
 const h=actionHistory();
 h.unshift({id:(crypto.randomUUID?crypto.randomUUID():String(Date.now())),type,targetId:target?.id||null,title:target?.title||"",details,at:Date.now()});
 localStorage.setItem("vg_action_history",JSON.stringify(h.slice(0,50)));
}
function undoLastAction(){
 const h=actionHistory(), a=h[0]; if(!a)return null;
 const target=media.find(m=>m.id===a.targetId); if(!target)return null;
 if(a.type==="favorite"){target.favorite=a.details.previous;save();buildIndex();renderLibrary();}
 if(a.type==="collection"){target.collection=a.details.previous;save();buildIndex();renderLibrary();}
 h.shift();localStorage.setItem("vg_action_history",JSON.stringify(h));
 return {title:target.title,type:a.type};
}
function actionHistorySummary(){
 return actionHistory().slice(0,8).map(a=>`${new Date(a.at).toLocaleString()} — ${a.type} — ${a.title}`).join("\n")||"No media actions yet.";
}
function actionTarget(q){
 const l=q.toLowerCase();
 if(/\b(recent|latest|newest)\b/.test(l))return [...media].sort((a,b)=>(b.created||0)-(a.created||0))[0];
 return rankedEvidence(q).map(r=>r.x)[0]||media[0];
}
function aiInsightAction(q){
 const l=q.toLowerCase();
 if(/\b(summarize|summary|key points|main points|brief|what is this about)\b/.test(l)) return summaryForQuery(q);
 return null;
}
function executeMediaAction(q){
 const l=q.toLowerCase();
 if(/\b(undo|reverse)\b/.test(l)){
   const u=undoLastAction();
   return u?{text:`Undid the last ${u.type} action for “${u.title}”.`,action:"refresh"}:{text:"There is no reversible media action yet.",action:"none"};
 }
 if(/\b(action history|recent actions|what did i change)\b/.test(l))
   return {text:"Recent media actions:\n"+actionHistorySummary(),action:"none"};

 const target=actionTarget(q); if(!target)return {text:"I don't have any local media to act on.",action:"none"};
 ensureOrg(target);

 if(/\b(unfavorite|unfavourite|unstar)\b/.test(l)){
   const previous=!!target.favorite; target.favorite=false; save(); buildIndex(); renderLibrary(); recordAction("favorite",target,{previous});
   return {text:`Removed “${target.title}” from favorites.`,target,action:"refresh"};
 }
 if(/\b(favorite|favourite|star)\b/.test(l)){
   const previous=!!target.favorite; target.favorite=true; save(); buildIndex(); renderLibrary(); recordAction("favorite",target,{previous});
   return {text:`Added “${target.title}” to favorites.`,target,action:"refresh"};
 }
 if(/\b(move|add)\b.*\bcollection\b/.test(l)){
   const name=prompt("Collection name",target.collection||"All");
   if(!name)return {text:"Collection action cancelled.",action:"none"};
   const s=orgState(), previous=target.collection||"All";
   if(!s.collections.includes(name.trim()))s.collections.push(name.trim());
   target.collection=name.trim(); saveOrg(s); save(); buildIndex(); renderLibrary(); recordAction("collection",target,{previous});
   return {text:`Moved “${target.title}” to “${target.collection}”.`,target,action:"refresh"};
 }
 if(/\b(show|open)\b.*\b(transcript|captions?|subtitles?)\b/.test(l)){
   return transcriptFor(target.id)?{text:`Opening “${target.title}” with its transcript.`,target,action:"play"}:{text:`No transcript is available for “${target.title}”.`,target,action:"none"};
 }
 if(/\b(play|open)\b/.test(l))
   return {text:`Opening “${target.title}” in the player.`,target,action:"play"};
 return null;
}


function mediaBrief(target){
 const tr=transcriptFor(target.id);
 if(!tr)return null;
 const lines=(tr.cues||tr.segments||[]).map(x=>({start:Number(x.start||0),text:String(x.text||x.content||"").trim()})).filter(x=>x.text);
 if(!lines.length)return null;
 const words=lines.flatMap(x=>x.text.toLowerCase().replace(/[^\w\s'-]/g," ").split(/\s+/).filter(w=>w.length>3));
 const stop=new Set("this that with from have they their there about would could should into your you are was were been will what when where which while then than also just very more some like this these those".split(" "));
 const freq={}; words.forEach(w=>{if(!stop.has(w))freq[w]=(freq[w]||0)+1});
 const topics=Object.entries(freq).sort((a,b)=>b[1]-a[1]).slice(0,8).map(x=>x[0]);
 const take=Math.min(6,Math.max(3,Math.ceil(lines.length/5)));
 const points=[];
 for(let i=0;i<lines.length;i+=Math.max(1,Math.floor(lines.length/take))) points.push(lines[i]);
 return {title:target.title, duration:target.duration||0, wordCount:words.length, topics, points:points.slice(0,take), source:"local transcript"};
}
function summaryForQuery(q){
 const target=actionTarget(q)||media[0]; if(!target)return {text:"I don't have local media to summarize."};
 const b=mediaBrief(target);
 if(!b)return {text:`I can't summarize “${target.title}” yet because it has no usable local transcript.`,target};
 return {target,brief:b,text:`Summary for “${target.title}”\\n\\nKey points:\\n${b.points.map((p,i)=>`${i+1}. ${p.text}`).join("\\n")}\\n\\nMain topics: ${b.topics.join(", ")}`};
}


function transcriptQA(q){
 const target=actionTarget(q)||media[0];
 if(!target)return {text:"I don't have local media to answer from."};
 const tr=transcriptFor(target.id);
 if(!tr)return {text:`I can't answer from “${target.title}” because it has no local transcript.`,target};
 const cues=(tr.cues||tr.segments||[]).map(x=>({start:Number(x.start||0),text:String(x.text||x.content||"").trim()})).filter(x=>x.text);
 if(!cues.length)return {text:`The transcript for “${target.title}” is empty.`,target};
 const stop=new Set(["what","when","where","which","about","they","this","that","tell","show","said","say","does","did","from","with","have","there","their","video"]);
 const terms=q.toLowerCase().replace(/[^\w\s]/g," ").split(/\s+/).filter(w=>w.length>3&&!stop.has(w));
 const scored=cues.map(c=>{const t=c.text.toLowerCase();let score=0;terms.forEach(term=>{if(t.includes(term))score+=2});score+=terms.filter(term=>t.split(/\s+/).includes(term)).length;return {...c,score}}).filter(x=>x.score>0).sort((a,b)=>b.score-a.score);
 if(!scored.length)return {text:`I couldn't find a transcript passage that directly matches that question in “${target.title}”.`,target};
 const hits=scored.slice(0,5).sort((a,b)=>a.start-b.start);
 return {target,hits,text:`Based on the local transcript of “${target.title}”, the most relevant passage is around ${formatTime(hits[0].start)}.`};
}
function isTranscriptQuestion(q){
 return /\b(what did|what does|why did|why does|how did|how does|who|when did|where did|explain|tell me about|what was said|did they mention|was .* mentioned)\b/i.test(q);
}


function qaCandidates(q){
 const all=media.map(m=>{ensureOrg(m);return m});
 const l=q.toLowerCase();
 const scored=all.map(m=>{
   const hay=[m.title,m.url,m.collection,...(m.tags||[])].filter(Boolean).join(" ").toLowerCase();
   let score=0;
   l.split(/\s+/).filter(w=>w.length>3).forEach(w=>{if(hay.includes(w))score+=2});
   if(/recent|latest|newest/.test(l))score+=(m.created||0)/1e13;
   if(/favorite|favourite|starred/.test(l)&&m.favorite)score+=8;
   return {m,score};
 }).sort((a,b)=>b.score-a.score);
 return scored.slice(0,5).filter(x=>x.score>0).map(x=>x.m);
}
function enhancedTranscriptQA(q){
 const candidates=qaCandidates(q);
 const targets=candidates.length?candidates:media.slice(0,5);
 const stop=new Set(["what","when","where","which","about","they","this","that","tell","show","said","say","does","did","from","with","have","there","their","video","please","can","could"]);
 const terms=q.toLowerCase().replace(/[^\w\s]/g," ").split(/\s+/).filter(w=>w.length>3&&!stop.has(w));
 const results=[];
 for(const target of targets){
   const tr=transcriptFor(target.id); if(!tr)continue;
   const cues=(tr.cues||tr.segments||[]).map(x=>({start:Number(x.start||0),text:String(x.text||x.content||"").trim()})).filter(x=>x.text);
   const scored=cues.map(c=>{const t=c.text.toLowerCase();let score=0;terms.forEach(term=>{if(t.includes(term))score+=2});score+=terms.filter(term=>t.split(/\s+/).includes(term)).length;return {...c,score}}).filter(x=>x.score>0).sort((a,b)=>b.score-a.score);
   if(scored.length)results.push({target,hits:scored.slice(0,3),score:scored[0].score});
 }
 results.sort((a,b)=>b.score-a.score);
 if(!results.length)return {text:"I couldn't find a matching transcript passage in the available local media."};
 const best=results[0], confidence=Math.min(99,Math.round(55+best.score*9+Math.min(20,best.hits.length*5)));
 return {target:best.target,hits:best.hits,confidence,sources:results.slice(0,4),text:`The strongest local evidence is in “${best.target.title}”, around ${formatTime(best.hits[0].start)}.`};
}


function mediaNotes(){
 try{return JSON.parse(localStorage.getItem("vg_media_notes")||"[]")}catch(e){return[]}
}
function saveMediaNotes(notes){localStorage.setItem("vg_media_notes",JSON.stringify(notes.slice(0,300)))}
function addMediaNote(target,time,text,type="highlight"){
 const notes=mediaNotes();
 const note={id:(crypto.randomUUID?crypto.randomUUID():String(Date.now())),mediaId:target.id,title:target.title,time:Number(time||0),text:String(text||"").trim(),type,created:Date.now()};
 notes.unshift(note);saveMediaNotes(notes);return note;
}
function noteSearch(q){
 const terms=q.toLowerCase().split(/\s+/).filter(x=>x.length>2);
 return mediaNotes().filter(n=>terms.some(t=>(n.text+" "+n.title).toLowerCase().includes(t))).slice(0,20);
}
function notesForMedia(id){return mediaNotes().filter(n=>n.mediaId===id).sort((a,b)=>a.time-b.time)}
function highlightFromQuestion(q){
 if(!/\b(save|create|add|make)\b.*\b(note|highlight|bookmark)\b/i.test(q))return null;
 const qa=enhancedTranscriptQA(q);
 if(!qa.target||!qa.hits?.length)return {text:"I couldn't find a transcript moment to save as a note."};
 const h=qa.hits[0];
 const note=addMediaNote(qa.target,h.start,h.text,"highlight");
 return {text:`Saved a highlight for “${qa.target.title}” at ${formatTime(h.start)}.`,note,target:qa.target};
}
function notesQuestion(q){
 if(!/\b(notes|highlights|bookmarks)\b/i.test(q))return null;
 const hits=noteSearch(q);
 if(!hits.length)return {text:"I couldn't find any matching saved notes or highlights."};
 return {text:`Found ${hits.length} saved note${hits.length===1?"":"s"}.`,hits};
}


function unifiedKnowledgeSearch(q){
 const terms=q.toLowerCase().replace(/[^\w\s]/g," ").split(/\s+/).filter(x=>x.length>2);
 const results=[];
 const notes=mediaNotes();

 media.forEach(m=>{
   ensureOrg(m);
   const hay=[m.title,m.url,m.type,m.collection,...(m.tags||[])].filter(Boolean).join(" ").toLowerCase();
   let score=0;terms.forEach(t=>{if(hay.includes(t))score+=5});
   if(score)results.push({kind:"media",score,title:m.title,mediaId:m.id,time:0,snippet:m.title,meta:`${m.type||"media"} · ${m.collection||"All"}`});
 });

 media.forEach(m=>{
   const tr=transcriptFor(m.id);if(!tr)return;
   (tr.cues||tr.segments||[]).forEach(c=>{
     const text=String(c.text||c.content||"").trim();if(!text)return;
     const hay=text.toLowerCase();let score=0;terms.forEach(t=>{if(hay.includes(t))score+=4});
     if(score)results.push({kind:"transcript",score,title:m.title,mediaId:m.id,time:Number(c.start||0),snippet:text,meta:`Transcript · ${formatTime(Number(c.start||0))}`});
   });
 });

 notes.forEach(n=>{
   const hay=(n.title+" "+n.text+" "+n.type).toLowerCase();let score=0;terms.forEach(t=>{if(hay.includes(t))score+=6});
   if(score)results.push({kind:"note",score,title:n.title,mediaId:n.mediaId,time:n.time,snippet:n.text,meta:`${n.type||"note"} · ${formatTime(n.time)}`});
 });

 results.sort((a,b)=>b.score-a.score||a.title.localeCompare(b.title));
 return results.slice(0,30);
}
function unifiedSearchQuery(q){
 if(!/\b(search|find|look for|where is|show me)\b/i.test(q))return null;
 const results=unifiedKnowledgeSearch(q);
 if(!results.length)return {text:"I couldn't find matching knowledge in your local VidGravity library."};
 return {results,text:`Found ${results.length} relevant local result${results.length===1?"":"s"}.`};
}


function smartCollectionState(){
 try{return JSON.parse(localStorage.getItem("vg_smart_collections")||"{}")}catch(e){return{}}
}
function saveSmartCollectionState(s){localStorage.setItem("vg_smart_collections",JSON.stringify(s))}
function buildSmartCollections(){
 const groups={};
 const add=(name,m,reason)=>{if(!groups[name])groups[name]={name,items:[],reasons:{}};if(!groups[name].items.includes(m.id))groups[name].items.push(m.id);groups[name].reasons[m.id]=reason};
 media.forEach(m=>{
   ensureOrg(m);
   const text=[m.title,m.url,m.collection,(m.tags||[]).join(" ")].filter(Boolean).join(" ").toLowerCase();
   if(m.type==="video"||/video|movie|lecture|webinar/.test(text))add("Videos",m,"media type/topic");
   if(m.type==="audio"||/audio|podcast|music|song/.test(text))add("Audio & Podcasts",m,"media type/topic");
   if(m.favorite)add("Favorites",m,"favorite");
   if(/tutorial|course|learn|training|lesson|education|guide/.test(text))add("Learning",m,"topic");
   if(/meeting|demo|client|sales|business|project|work/.test(text))add("Work",m,"topic");
   const tr=transcriptFor(m.id);
   if(tr){
     const tx=(tr.cues||tr.segments||[]).map(x=>String(x.text||x.content||"")).join(" ").toLowerCase();
     if(/pricing|price|cost|subscription|payment|license/.test(tx))add("Pricing & Licensing",m,"transcript topic");
     if(/tutorial|how to|step|install|setup|configure/.test(tx))add("How-To",m,"transcript topic");
     if(/meeting|agenda|decision|action item|follow up/.test(tx))add("Meetings",m,"transcript topic");
   }
   notesForMedia(m.id).forEach(n=>{
     if(/important|priority|todo|action|follow/.test(n.text.toLowerCase()))add("Important Notes",m,"saved note");
   });
 });
 const state=smartCollectionState();state.groups=groups;state.generated=Date.now();saveSmartCollectionState(state);return groups;
}
function smartCollectionQuery(q){
 if(!/\b(smart collections|smart collection|auto collections|automatic collections|group my media|organize my media)\b/i.test(q))return null;
 const groups=buildSmartCollections();
 const list=Object.values(groups).filter(g=>g.items.length).sort((a,b)=>b.items.length-a.items.length);
 return {groups:list,text:`I organized your local library into ${list.length} smart collections.`};
}



function getAIActivity(){try{return JSON.parse(localStorage.getItem("vg_ai_activity")||"[]")}catch(e){return[]}}
function recordAIActivity(type,title,detail="",mediaId=""){const a=getAIActivity();a.unshift({id:"act_"+Date.now()+"_"+Math.random().toString(36).slice(2,7),type,title,detail,mediaId,at:new Date().toISOString()});localStorage.setItem("vg_ai_activity",JSON.stringify(a.slice(0,80)))}
function activityIcon(type){return({question:"?",summary:"✦",highlight:"★",search:"⌕",collection:"▦",action:"✓"})[type]||"•"}
function relativeActivityTime(iso){const m=Math.max(0,Math.floor((Date.now()-new Date(iso).getTime())/60000));if(m<1)return"just now";if(m<60)return`${m}m ago`;const h=Math.floor(m/60);if(h<24)return`${h}h ago`;return`${Math.floor(h/24)}d ago`}
function aiActivityCenter(){const a=getAIActivity(),media=JSON.parse(localStorage.getItem("vg_media")||"[]"),t=media.filter(x=>x.transcript||x.transcriptText||x.transcriptId).length,recs=[];if(media.length&&t<media.length)recs.push({label:`Add transcripts to ${media.length-t} media item${media.length-t===1?"":"s"}`,kind:"transcript"});if(a.some(x=>x.type==="highlight"))recs.push({label:"Review your saved highlights",kind:"highlights"});if(a.some(x=>x.type==="question"))recs.push({label:"Continue asking questions about your media",kind:"questions"});if(!recs.length)recs.push({label:"Import media and start building your local knowledge base",kind:"start"});return{items:a.slice(0,12),recs}}
function renderAIActivityCenter(){const root=document.getElementById("aiActivityCenter");if(!root)return;const d=aiActivityCenter();root.innerHTML=`<div class="ai-center-head"><div><div class="eyebrow">AI ACTIVITY</div><h2>Media Intelligence Center</h2><p>Your recent local AI work, evidence, and next best actions.</p></div><button class="ghost-btn" id="clearAIActivity">Clear activity</button></div><div class="ai-center-grid"><section class="activity-panel"><div class="panel-title"><span>Recent activity</span><span class="count-pill">${d.items.length}</span></div><div class="activity-list">${d.items.length?d.items.map(x=>`<button class="activity-row" data-activity-id="${x.id}"><span class="activity-icon">${activityIcon(x.type)}</span><span class="activity-copy"><strong>${escapeHtml(x.title)}</strong><small>${escapeHtml(x.detail||x.type)} · ${relativeActivityTime(x.at)}</small></span><span class="activity-arrow">›</span></button>`).join(""):`<div class="empty-activity"><div class="empty-glyph">✦</div><strong>No AI activity yet</strong><span>Ask a question, search your media, or save a highlight to start.</span></div>`}</div></section><section class="activity-panel recommendations"><div class="panel-title"><span>Recommended next</span><span class="pulse-dot"></span></div>${d.recs.map((r,i)=>`<button class="recommendation" data-rec="${r.kind}"><span class="rec-num">${i+1}</span><span><strong>${escapeHtml(r.label)}</strong><small>Based only on your local VidGravity library</small></span><span>→</span></button>`).join("")}<div class="local-badge">⌁ Local-only intelligence</div></section></div>`;document.getElementById("clearAIActivity")?.addEventListener("click",()=>{localStorage.removeItem("vg_ai_activity");renderAIActivityCenter()})}




function getMediaContext(){
  try{return JSON.parse(localStorage.getItem("vg_media_context")||"null")}catch(e){return null}
}
function setMediaContext(ctx){
  if(!ctx){localStorage.removeItem("vg_media_context");return null}
  const safe={mediaId:ctx.mediaId||"",title:ctx.title||"Untitled media",timestamp:Number(ctx.timestamp)||0,transcriptId:ctx.transcriptId||"",noteId:ctx.noteId||"",highlightId:ctx.highlightId||"",source:ctx.source||"library",updatedAt:new Date().toISOString()};
  localStorage.setItem("vg_media_context",JSON.stringify(safe)); return safe;
}
function mediaContextFromId(id){
  const media=JSON.parse(localStorage.getItem("vg_media")||"[]");
  return media.find(x=>String(x.id)===String(id))||null;
}
function contextLabel(){
  const c=getMediaContext();
  return c?`Current media: ${c.title}${c.timestamp?` · ${Math.floor(c.timestamp/60)}:${String(Math.floor(c.timestamp%60)).padStart(2,"0")}`:""}`:"No media is currently selected.";
}
function renderMediaContext(){
  const root=document.getElementById("mediaContextBar"); if(!root)return;
  const c=getMediaContext();
  root.innerHTML=c?`<div class="context-icon">◉</div><div><span>ACTIVE MEDIA CONTEXT</span><strong>${escapeHtml(c.title)}</strong><small>${c.timestamp?`At ${Math.floor(c.timestamp/60)}:${String(Math.floor(c.timestamp%60)).padStart(2,"0")} · `:""}${escapeHtml(c.source||"library")}</small></div><button id="clearMediaContext">Clear</button>`:`<div class="context-icon muted">○</div><div><span>MEDIA CONTEXT</span><strong>No media selected</strong><small>Open a media item to give Copilot precise context.</small></div>`;
  root.querySelector("#clearMediaContext")?.addEventListener("click",()=>{setMediaContext(null);renderMediaContext();});
}
function activateMediaContext(mediaId,timestamp=0,source="library"){
  const m=mediaContextFromId(mediaId); if(!m)return null;
  const c=setMediaContext({mediaId:m.id,title:m.title||"Untitled media",timestamp,transcriptId:m.transcriptId||"",source});
  renderMediaContext(); renderUnifiedMediaWorkspace(); renderUnifiedIntelligencePanel(); renderReleaseWorkspaceRail(); return c;
}
function copilotContextAnswer(query){
  const q=(query||"").toLowerCase(), c=getMediaContext();
  if(!c)return null;
  if(/(what|which).*(media|video|audio).*(this|here)|what is this/.test(q))return{kind:"context",text:`You’re currently working with “${c.title}”.`,context:c};
  if(/(what|which).*(said|saying|happened).*(here|now|this moment)/.test(q))return{kind:"context",text:`The active context is “${c.title}”${c.timestamp?` at ${Math.floor(c.timestamp/60)}:${String(Math.floor(c.timestamp%60)).padStart(2,"0")}`:""}. I can use transcript evidence for this moment when transcript data is available.`,context:c};
  return null;
}



function findActiveMediaElement(){
  const mediaEls=[...document.querySelectorAll("video,audio")];
  if(!mediaEls.length)return null;
  return mediaEls.find(x=>!x.paused&&!x.ended)||mediaEls[0];
}
function bindRealPlayerContext(){
  const el=findActiveMediaElement(); if(!el)return false;
  const id=el.dataset.mediaId||el.getAttribute("data-media-id")||el.closest("[data-media-id]")?.dataset.mediaId;
  if(!id)return false;
  const update=()=>{const c=mediaContextFromId(id);if(c){setMediaContext({mediaId:id,title:c.title||el.getAttribute("aria-label")||"Current media",timestamp:Number(el.currentTime)||0,source:"player"});renderMediaContext();}};
  ["play","pause","timeupdate","seeked","loadedmetadata"].forEach(ev=>el.addEventListener(ev,update,{passive:true}));
  update(); return true;
}
function getPlayerContext(){
  const el=findActiveMediaElement(); if(!el)return getMediaContext();
  const id=el.dataset.mediaId||el.getAttribute("data-media-id")||el.closest("[data-media-id]")?.dataset.mediaId;
  if(!id)return getMediaContext();
  const c=mediaContextFromId(id); if(!c)return getMediaContext();
  return setMediaContext({mediaId:id,title:c.title||"Current media",timestamp:Number(el.currentTime)||0,source:"player"});
}
function jumpPlayerToContext(timestamp){
  const el=findActiveMediaElement(); if(!el)return false;
  try{el.currentTime=Math.max(0,Number(timestamp)||0);el.play?.().catch(()=>{});getPlayerContext();return true}catch(e){return false}
}

function getContextTranscript(mediaId){
  const m=mediaContextFromId(mediaId);
  if(!m)return[];
  const raw=m.transcript||m.transcriptText||m.transcriptSegments||"";
  if(Array.isArray(raw))return raw.map(x=>({text:x.text||x.content||"",start:Number(x.start||x.startTime||0),end:Number(x.end||x.endTime||0)})).filter(x=>x.text);
  const text=String(raw||"");
  if(!text)return[];
  const lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean), out=[];
  let t=0;
  for(const line of lines){
    const mm=line.match(/^\[(\d{1,2}):(\d{2})(?:\.(\d+))?\]\s*(.*)$/);
    if(mm){t=Number(mm[1])*60+Number(mm[2]);out.push({text:mm[4],start:t,end:t+8});}
    else out.push({text:line,start:t,end:t+8});
    t+=8;
  }
  return out;
}
function contextualTranscriptAnswer(query){
  const liveContext=getPlayerContext();
  const c=liveContext||getMediaContext();
  if(!c)return null;
  const q=(query||"").toLowerCase();
  const segs=getContextTranscript(c.mediaId);
  if(!segs.length)return null;
  const at=Number(c.timestamp)||0;
  const nearby=segs.filter(x=>x.start<=at+15&&x.end>=Math.max(0,at-15));
  if(/(what|which).*(said|saying|happened).*(here|now|this moment)/.test(q)){
    const best=(nearby.length?nearby:segs.slice(0,3)).slice(0,4);
    return {kind:"evidence",text:`At this point in “${c.title}”, I found ${best.length} nearby transcript passage${best.length===1?"":"s"}.`,items:best,context:c};
  }
  if(/summar(y|ize).*(this|here|section|moment)/.test(q)){
    const best=(nearby.length?nearby:segs.slice(0,5)).slice(0,5);
    return {kind:"summary",text:`This section contains ${best.length} transcript passage${best.length===1?"":"s"} around the active position.`,items:best,context:c};
  }
  if(/(save|create).*(this|here).*(moment|highlight)/.test(q)){
    return {kind:"highlight_context",text:`I can save the current position in “${c.title}” as a highlight.`,context:c};
  }
  return null;
}
function saveContextHighlight(){
  const c=getMediaContext();
  if(!c)return{ok:false,text:"Select a media item first."};
  const key="vg_highlights", list=JSON.parse(localStorage.getItem(key)||"[]");
  const item={id:"hl_"+Date.now(),mediaId:c.mediaId,title:c.title,timestamp:c.timestamp||0,createdAt:new Date().toISOString(),source:"copilot"};
  list.unshift(item);localStorage.setItem(key,JSON.stringify(list.slice(0,200)));
  recordAIActivity("highlight",`Saved highlight: ${c.title}`,`At ${Math.floor((c.timestamp||0)/60)}:${String(Math.floor((c.timestamp||0)%60)).padStart(2,"0")}`,c.mediaId);
  return{ok:true,text:"Saved this moment as a local highlight.",item};
}

function copilotActionPlan(query){
  const q=(query||"").trim().toLowerCase();
  const context=getMediaContext();
  const action=(name,label,target,needsConfirm=true)=>({kind:"action",name,label,target,needsConfirm});
  if(/^(favorite|favourite)\\s+(this|it)$/.test(q)) return action("favorite","Favorite the current media","library");
  if(/^(unfavorite|unfavourite)\\s+(this|it)$/.test(q)) return action("unfavorite","Remove the current media from favorites","library");
  if(/^(open|show)\\s+(the\\s+)?(library|media library)$/.test(q)) return action("open_library","Open Media Library","library",false);
  if(/^(open|show)\\s+(the\\s+)?(transcript|transcripts)$/.test(q)) return action("open_transcript","Open transcript workspace","transcript",false);
  if(/^(show|open)\\s+(my\\s+)?highlights?$/.test(q)) return action("highlights","Show saved highlights","highlights",false);
  if(/^(show|open)\\s+(my\\s+)?notes?$/.test(q)) return action("notes","Show saved notes","notes",false);
  if(/(generate|create)\\s+(smart\\s+)?collections?/.test(q)) return action("smart_collections","Generate smart collections","smartCollections",true);
  if(/(save|create)\\s+(a\\s+)?highlight/.test(q)) return action("highlight","Save a media highlight","highlights",true);
  if(/(save|create)\\s+(a\\s+)?note/.test(q)) return action("note","Save a media note","notes",true);
  if(/(add|move).*collection/.test(q)) return action("collection","Move media to a collection","collections",true);
  if(/(favorite|favourite)/.test(q)) return action("favorite","Favorite matching media","library",true);
  return null;
}
function executeCopilotAction(plan){
  if(!plan)return{ok:false,text:"I couldn't identify a supported media action."};
  if(plan.needsConfirm){recordAIActivity("action",`Prepared: ${plan.label}`,"Waiting for confirmation");return{ok:true,confirm:true,text:`I’m ready to ${plan.label.toLowerCase()}. Please confirm this action.`,plan};}
  recordAIActivity("action",plan.label,"Navigation");return{ok:true,confirm:false,text:`Opening ${plan.label.toLowerCase()}.`,plan};
}
function confirmCopilotAction(plan){
  recordAIActivity("action",`Confirmed: ${plan.label}`,"User-approved local action");
  const target=document.getElementById(plan.target);
  if(target){target.scrollIntoView({behavior:"smooth",block:"start"});target.classList.add("cmd-focus");setTimeout(()=>target.classList.remove("cmd-focus"),1200)}
  return{ok:true,text:`Confirmed. ${plan.label} is ready in the workspace.`};
}

function mediaCopilotAnswer(query){
  const q=(query||"").trim().toLowerCase();
  const d=mediaCommandCenter();
  if(!q)return{kind:"empty",text:"Ask me about your media, transcripts, notes, highlights, or collections."};
  if(/^(hi|hello|hey)\b/.test(q))return{kind:"chat",text:"Hi! I can search your local media, answer transcript questions, open evidence, and help organize your library."};
  if(q.includes("how many")||q.includes("count")){
    if(q.includes("favorite"))return{kind:"metric",text:`You have ${d.favorites} favorite media item${d.favorites===1?"":"s"}.`};
    if(q.includes("transcript"))return{kind:"metric",text:`${d.transcripts} of ${d.media.length} media item${d.media.length===1?"":"s"} have transcript data (${d.coverage}%).`};
    return{kind:"metric",text:`Your local library contains ${d.media.length} media item${d.media.length===1?"":"s"}.`};
  }
  if(q.includes("favorite"))return{kind:"list",text:`I found ${d.favorites} favorite item${d.favorites===1?"":"s"}. Open Media Library to review them.`};
  if(q.includes("transcript"))return{kind:"list",text:`Transcript coverage is ${d.coverage}%. I can help you find transcript-backed media.`};
  if(q.includes("recent"))return{kind:"activity",text:d.activity.length?`Your latest AI activity is “${d.activity[0].title}”.`:"There is no recorded AI activity yet."};
  if(q.includes("smart collection")||q.includes("collection"))return{kind:"action",text:"I can take you to Smart Collections so you can review or generate local groups.",target:"smartCollections"};
  if(q.includes("library")||q.includes("media"))return{kind:"action",text:"Opening your local Media Library.",target:"library"};
  if(q.includes("dashboard")||q.includes("overview"))return{kind:"action",text:"Opening the Intelligence Dashboard.",target:"intelligenceDashboard"};
  const words=q.split(/\s+/).filter(x=>x.length>2).slice(0,6);
  const hits=d.media.filter(x=>words.some(w=>(`${x.title||""} ${x.url||""} ${x.tags||""} ${x.collection||""}`).toLowerCase().includes(w)));
  if(hits.length)return{kind:"results",text:`I found ${hits.length} matching local media item${hits.length===1?"":"s"}.`,items:hits.slice(0,5)};
  return{kind:"fallback",text:"I couldn't find grounded evidence for that in your local library. Try a title, topic, tag, transcript phrase, or ask a count question."};
}
function renderMediaCopilot(){
  const root=document.getElementById("mediaCopilot");
  if(!root)return;
  root.innerHTML=`
    <div class="copilot-head"><div><div class="eyebrow">LOCAL AI COPILOT</div><h2>Ask VidGravity</h2><p>Talk to your media library. Answers stay grounded in local data.</p></div><span class="copilot-private">⌁ Private by default</span></div>
    <div class="copilot-suggestions">
      <button data-prompt="How many media do I have?">How many media?</button>
      <button data-prompt="How many favorites do I have?">My favorites</button>
      <button data-prompt="What is my transcript coverage?">Transcript coverage</button>
      <button data-prompt="Show my recent activity">Recent activity</button>
    </div>
    <div class="copilot-chat" id="copilotChat"><div class="copilot-welcome"><b>✦</b><span><strong>What would you like to know?</strong><small>Ask naturally. I’ll use only information available in VidGravity’s local workspace.</small></span></div></div>
    <form class="copilot-form" id="copilotForm"><input id="copilotInput" autocomplete="off" placeholder="Ask about your media…"><button>Ask</button></form>`;
  const chat=root.querySelector("#copilotChat"), input=root.querySelector("#copilotInput");
  function ask(q){
    const user=document.createElement("div");user.className="copilot-msg user";user.textContent=q;chat.appendChild(user);
    const plan=copilotActionPlan(q); const contextAnswer=copilotContextAnswer(q); const transcriptAnswer=contextualTranscriptAnswer(q); const a=plan?executeCopilotAction(plan):(transcriptAnswer||contextAnswer||mediaCopilotAnswer(q)), bot=document.createElement("div");bot.className="copilot-msg assistant";
    bot.innerHTML=`<div class="copilot-avatar">✦</div><div><strong>${escapeHtml(a.text)}</strong>${a.kind==="evidence"||a.kind==="summary"?`<div class="copilot-evidence">${a.items.map(x=>`<div><span>${escapeHtml(x.text)}</span><small>${Math.floor(Number(x.start||0)/60)}:${String(Math.floor(Number(x.start||0)%60)).padStart(2,"0")}</small></div>`).join("")}</div>`:""}${a.kind==="highlight_context"?`<div class="copilot-confirm"><button data-context-highlight="yes">Save moment</button></div>`:""}${a.confirm?`<div class="copilot-confirm><button data-confirm-action="yes">Confirm</button><button data-confirm-action="no">Cancel</button></div>`:""}${a.items?`<div class="copilot-results">${a.items.map(x=>`<button class="copilot-result" data-id="${escapeHtml(x.id||"")}"><span>${escapeHtml(x.title||"Untitled media")}</span><small>${escapeHtml(x.type||"media")}</small></button>`).join("")}</div>`:""}${a.target?`<button class="copilot-open" data-target="${a.target}">Open workspace →</button>`:""}</div>`;
    chat.appendChild(bot);chat.scrollTop=chat.scrollHeight;
    bot.querySelector('[data-context-highlight="yes"]')?.addEventListener("click",()=>{const r=saveContextHighlight();bot.querySelector(".copilot-confirm")?.remove();bot.insertAdjacentHTML("beforeend",`<div class="copilot-result-note">${escapeHtml(r.text)}</div>`)});
    bot.querySelector('[data-confirm-action="yes"]')?.addEventListener("click",()=>{const r=confirmCopilotAction(plan);bot.querySelector(".copilot-confirm")?.remove();bot.insertAdjacentHTML("beforeend",`<div class="copilot-result-note">${escapeHtml(r.text)}</div>`)});
    bot.querySelector('[data-confirm-action="no"]')?.addEventListener("click",()=>{recordAIActivity("action",`Cancelled: ${plan.label}`,"User declined action");bot.querySelector(".copilot-confirm")?.remove();bot.insertAdjacentHTML("beforeend",`<div class="copilot-result-note">Cancelled. No media change was made.</div>`)});

    recordAIActivity("question",`Copilot: ${q}`,a.text);
    bot.querySelector("[data-target]")?.addEventListener("click",e=>{const el=document.getElementById(e.currentTarget.dataset.target);el?.scrollIntoView({behavior:"smooth"})});
    bot.querySelectorAll(".copilot-result").forEach(b=>b.addEventListener("click",()=>{activateMediaContext(b.dataset.id,0,"copilot");const el=document.getElementById("library");el?.scrollIntoView({behavior:"smooth"});}));
  }
  root.querySelector("#copilotForm").addEventListener("submit",e=>{e.preventDefault();const q=input.value.trim();if(q){ask(q);input.value=""}});
  root.querySelectorAll(".copilot-suggestions button").forEach(b=>b.addEventListener("click",()=>{ask(b.dataset.prompt)}));
}

function mediaCommandCenter(){
  const media=JSON.parse(localStorage.getItem("vg_media")||"[]");
  const activity=getAIActivity();
  const favorites=media.filter(x=>x.favorite).length;
  const transcripts=media.filter(x=>x.transcript||x.transcriptText||x.transcriptId).length;
  const notes=media.reduce((n,x)=>n+(Array.isArray(x.notes)?x.notes.length:0),0);
  return {
    media,activity,favorites,transcripts,notes,
    coverage:media.length?Math.round(transcripts/media.length*100):0
  };
}
function renderMediaCommandCenter(){
  const root=document.getElementById("mediaCommandCenter");
  if(!root)return;
  const d=mediaCommandCenter();
  root.innerHTML=`
    <div class="cmd-head">
      <div>
        <div class="eyebrow">VIDGRAVITY INTELLIGENCE</div>
        <h2>AI Media Command Center</h2>
        <p>Search, understand, organize and act on your local media from one workspace.</p>
      </div>
      <div class="cmd-status"><span></span> Local & private</div>
    </div>
    <div class="cmd-actions">
      <button class="cmd-action primary" data-cmd="ask"><b>?</b><span><strong>Ask My Media</strong><small>Question your transcripts</small></span></button>
      <button class="cmd-action" data-cmd="search"><b>⌕</b><span><strong>Knowledge Search</strong><small>Find anything across media</small></span></button>
      <button class="cmd-action" data-cmd="library"><b>▣</b><span><strong>Media Library</strong><small>Open your collection</small></span></button>
      <button class="cmd-action" data-cmd="collections"><b>▦</b><span><strong>Smart Collections</strong><small>Organize automatically</small></span></button>
    </div>
    <div class="cmd-metrics">
      <div><strong>${d.media.length}</strong><span>Media</span></div>
      <div><strong>${d.transcripts}</strong><span>Transcribed</span></div>
      <div><strong>${d.favorites}</strong><span>Favorites</span></div>
      <div><strong>${d.notes}</strong><span>Notes</span></div>
      <div><strong>${d.coverage}%</strong><span>Coverage</span></div>
    </div>
    <div class="cmd-columns">
      <section class="cmd-panel">
        <div class="cmd-panel-head"><span>Recent intelligence</span><button class="cmd-mini" data-cmd="activity">View all</button></div>
        <div class="cmd-feed">
          ${d.activity.slice(0,6).map(x=>`<div class="cmd-feed-row"><span class="cmd-feed-icon">${activityIcon(x.type)}</span><div><strong>${escapeHtml(x.title)}</strong><small>${escapeHtml(x.detail||x.type)} · ${relativeActivityTime(x.at)}</small></div></div>`).join("")||`<div class="cmd-empty">Your AI activity will appear here as you work with media.</div>`}
        </div>
      </section>
      <section class="cmd-panel">
        <div class="cmd-panel-head"><span>Workspace health</span><span class="health-dot"></span></div>
        <div class="health-line"><span>Transcript coverage</span><strong>${d.coverage}%</strong></div>
        <div class="health-track"><i style="width:${d.coverage}%"></i></div>
        <div class="health-tip">${d.media.length===0?"Import media to start building your local intelligence layer.":d.coverage<100?`${d.media.length-d.transcripts} media item${d.media.length-d.transcripts===1?"":"s"} can still be enriched with transcripts.`:"Your current media library has full transcript coverage."}</div>
        <button class="cmd-wide" data-cmd="details">Open intelligence dashboard →</button>
      </section>
    </div>`;
  root.querySelectorAll("[data-cmd]").forEach(btn=>btn.addEventListener("click",()=>{
    const cmd=btn.dataset.cmd;
    const targets={ask:["askMedia","Ask My Media"],search:["knowledgeSearch","Knowledge Search"],library:["library","Media Library"],collections:["smartCollections","Smart Collections"],activity:["aiActivityCenter","AI Activity"],details:["intelligenceDashboard","Intelligence Dashboard"]};
    const t=targets[cmd];
    if(t){const el=document.getElementById(t[0]);if(el){el.scrollIntoView({behavior:"smooth",block:"start"});el.classList.add("cmd-focus");setTimeout(()=>el.classList.remove("cmd-focus"),1200)}} 
  }));
}

function intelligenceDashboard(){
 const total=media.length;
 const videos=media.filter(m=>m.type==="video").length;
 const audio=media.filter(m=>m.type==="audio").length;
 const favorites=media.filter(m=>m.favorite).length;
 const withTranscript=media.filter(m=>!!transcriptFor(m.id)).length;
 const notes=mediaNotes().length;
 const smart=Object.values(smartCollectionState().groups||{}).filter(g=>g.items?.length);
 const tags=new Set(media.flatMap(m=>m.tags||[])).size;
 const collections=new Set(media.map(m=>m.collection).filter(Boolean)).size;
 const transcriptCoverage=total?Math.round(withTranscript/total*100):0;
 return {total,videos,audio,favorites,withTranscript,notes,smartCollections:smart.length,tags,collections,transcriptCoverage};
}
function renderIntelligenceDashboard(){
 const d=intelligenceDashboard();
 const el=$("#intelligenceDashboard"); if(!el)return;
 el.innerHTML=`
 <div class="dashHeader"><div><b>Media Intelligence</b><span>Local library health and AI activity</span></div><button id="dashRefresh" class="smallBtn">Refresh</button></div>
 <div class="dashGrid">
  <div class="dashCard"><b>${d.total}</b><span>Total media</span></div>
  <div class="dashCard"><b>${d.videos}</b><span>Videos</span></div>
  <div class="dashCard"><b>${d.audio}</b><span>Audio</span></div>
  <div class="dashCard"><b>${d.favorites}</b><span>Favorites</span></div>
  <div class="dashCard"><b>${d.withTranscript}</b><span>Transcribed</span></div>
  <div class="dashCard"><b>${d.notes}</b><span>Notes & highlights</span></div>
 </div>
 <div class="dashHealth"><div><b>Transcript coverage</b><span>${d.transcriptCoverage}% of media has local transcript data</span></div><div class="dashMeter"><i style="width:${d.transcriptCoverage}%"></i></div></div>
 <div class="dashStats"><span>Collections <b>${d.collections}</b></span><span>Tags <b>${d.tags}</b></span><span>Smart groups <b>${d.smartCollections}</b></span></div>`;
 $("#dashRefresh").onclick=renderIntelligenceDashboard;
}

function ask(){
 const q=$("#askInput").value.trim();if(!q)return;
 $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble user">${esc(q)}</div>`);

 const smart=smartCollectionQuery(q);
 if(smart){
   const cards=smart.groups.map(g=>`<button class="smartCard" data-ids="${g.items.join(",")}"><b>${esc(g.name)}</b><span>${g.items.length} media item${g.items.length===1?"":"s"}</span><small>${esc(Object.values(g.reasons)[0]||"automatic grouping")}</small></button>`).join("");
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai"><div>${esc(smart.text)}</div><div class="smartGrid">${cards}</div></div>`);
   $("#chat").querySelectorAll(".smartCard").forEach(b=>b.onclick=()=>{
     const ids=b.dataset.ids.split(",");
     const first=media.find(m=>m.id===ids[0]);if(first)play(first.id);
   });
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const search=unifiedSearchQuery(q);
 if(search){
   const cards=search.results.map(r=>`<button class="knowledgeCard" data-id="${r.mediaId}" data-time="${r.time}"><div><b>${esc(r.title)}</b><span class="knowledgeKind">${esc(r.kind)}</span></div><p>${esc(r.snippet)}</p><small>${esc(r.meta)} · relevance ${r.score}</small></button>`).join("");
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai"><div>${esc(search.text)}</div><div class="knowledgeResults">${cards}</div></div>`);
   $("#chat").querySelectorAll(".knowledgeCard").forEach(b=>b.onclick=()=>play(b.dataset.id,Number(b.dataset.time||0)));
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const saved=highlightFromQuestion(q);
 if(saved){
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai">${esc(saved.text)}<button class="smallBtn noteOpen" data-id="${saved.target.id}" data-time="${saved.note.time}">Open highlight</button></div>`);
   $("#chat").querySelector(".noteOpen").onclick=e=>play(e.currentTarget.dataset.id,Number(e.currentTarget.dataset.time||0));
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const nq=notesQuestion(q);
 if(nq){
   const cards=nq.hits?`<div class="noteCards">${nq.hits.map(n=>`<button class="noteCard" data-id="${n.mediaId}" data-time="${n.time}"><b>${esc(n.title)}</b><span>${formatTime(n.time)} · ${esc(n.text)}</span></button>`).join("")}</div>`:"";
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai">${esc(nq.text)}${cards}</div>`);
   $("#chat").querySelectorAll(".noteCard").forEach(b=>b.onclick=()=>play(b.dataset.id,Number(b.dataset.time||0)));
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 if(isTranscriptQuestion(q)){
   const qa=enhancedTranscriptQA(q);
   const evidence=qa.hits?`<div class="qaEvidence">${qa.hits.map(h=>`<button class="qaHit" data-id="${qa.target.id}" data-time="${h.start}"><span>${formatTime(h.start)}</span><span>${esc(h.text)}</span></button>`).join("")}</div>`:"";
   const sources=qa.sources?`<div class="qaSources"><b>Sources</b>${qa.sources.map(s=>`<div><span>${esc(s.target.title)}</span><small>${s.hits.length} matching passage${s.hits.length===1?"":"s"}</small></div>`).join("")}</div>`:"";
   const conf=qa.confidence?`<div class="qaConfidence">Local evidence confidence <b>${qa.confidence}%</b></div>`:"";
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai"><div>${esc(qa.text)}</div>${conf}${evidence}${sources}${qa.target?`<button class="smallBtn qaOpen" data-id="${qa.target.id}" data-time="${qa.hits?.[0]?.start||0}">Open strongest evidence</button>`:""}</div>`);
   $("#chat").querySelectorAll(".qaHit,.qaOpen").forEach(b=>b.onclick=()=>play(b.dataset.id,Number(b.dataset.time||0)));
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const insight=aiInsightAction(q);
 if(insight){
   const b=insight.brief;
   const body=b?`<b>${esc(insight.text.split("\n")[0])}</b><div class="aiSection"><b>Key points</b>${b.points.map((p,i)=>`<div class="aiPoint"><span>${i+1}</span><span>${esc(p.text)}</span></div>`).join("")}<div class="aiTopics"><b>Main topics:</b> ${b.topics.map(esc).join(", ")}</div><small>Grounded in the local transcript · ${b.wordCount} words analyzed</small></div>`:esc(insight.text);
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai">${body}${insight.target?`<button class="smallBtn insightOpen" data-id="${insight.target.id}">Open media</button>`:""}</div>`);
   $("#chat").querySelectorAll(".insightOpen").forEach(b=>b.onclick=()=>play(b.dataset.id));
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const action=executeMediaAction(q);
 if(action){
   $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai">${esc(action.text)}${action.target?`<div class="actionResult"><b>${esc(action.target.title)}</b><small>${esc(action.target.type||"media")} · ${formatBytes(action.target.size)}</small></div>`:""}</div>`);
   if(action.action==="play")play(action.target.id);
   $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;return;
 }

 const r=answerFromIndex(q);
 const evidence=r.items.length?`<div class="answerEvidence">${r.items.slice(0,8).map(m=>`<div class="answerItem"><div><b>${m.favorite?"★ ":""}${esc(m.title)}</b><small>${esc(m.type||"media")} · ${esc(m.collection||"All")} · ${formatBytes(m.size)}</small></div><button class="smallBtn answerOpen" data-id="${m.id}" data-time="${r.transcriptHits?.find(h=>h.mediaId===m.id)?.start||0}">Open</button></div>`).join("")}</div>`:"";
 const tx=r.transcriptHits?.length?`<div class="transcriptQuote">${r.transcriptHits.slice(0,5).map(h=>`<div><span>${formatTime(h.start)}</span> ${esc(h.text)}</div>`).join("")}</div>`:"";
 $("#chat").insertAdjacentHTML("beforeend",`<div class="bubble ai"><div>${esc(r.text)}</div>${evidence}${tx}</div>`);
 $("#chat").querySelectorAll(".answerOpen").forEach(b=>b.onclick=()=>play(b.dataset.id,Number(b.dataset.time||0)));
 $("#askInput").value="";$("#chat").scrollTop=$("#chat").scrollHeight;
}

if('serviceWorker'in navigator)window.addEventListener('load',()=>navigator.serviceWorker.register('sw.js').catch(()=>{}));
load();document.querySelector('#bulkDelete')?.addEventListener('click',deleteSelected);

document.addEventListener('change',e=>{if(e.target.matches('.mediaSelect'))updateSelection()});
function updateSelection(){const n=document.querySelectorAll('.mediaSelect:checked').length;const b=document.querySelector('#bulkDelete');if(b)b.textContent=`Delete selected (${n})`;}
async function deleteSelected(){const ids=[...document.querySelectorAll('.mediaSelect:checked')].map(x=>x.dataset.id);if(!ids.length)return toast('Select media first');if(!confirm(`Delete ${ids.length} selected item(s)?`))return;for(const id of ids)await dbDelete(id);media=media.filter(m=>!ids.includes(m.id));jobs=jobs.filter(j=>!ids.includes(j.mediaId));saveJobs();renderAll();toast(`${ids.length} media item(s) deleted`)}

$("#libraryFilter")?.addEventListener("change",renderLibrary);
$("#libraryTag")?.addEventListener("input",renderLibrary);
$("#newCollection")?.addEventListener("click",()=>{const name=prompt("Collection name");if(!name)return;const s=orgState();const n=name.trim().slice(0,30);if(!n)return;if(!s.collections.includes(n))s.collections.push(n);s.active=n;saveOrg(s);renderCollections();renderLibrary();toast("Collection created")});
function closeDetails(){const modal=$("#detailsModal");if(modal){modal.classList.add("hidden");modal.setAttribute("aria-hidden","true")}}
$("#closeDetails")?.addEventListener("click",event=>{event.preventDefault();event.stopPropagation();closeDetails()});
$("#detailsModal")?.addEventListener("click",event=>{if(event.target===$("#detailsModal"))closeDetails()});

document.querySelector("#addTranscript")?.addEventListener("click",addTranscript);document.querySelector("#findTranscript")?.addEventListener("click",()=>{const q=prompt("Search local transcripts/captions");if(q){$("#askInput").value="transcript "+q;ask()}});

$("#importVtt")?.addEventListener("click",importCaptionFile);
document.addEventListener("click",e=>{if(e.target.id==="removeTranscript"){const id=$("#detailsContent").querySelector("[data-media-id]")?.dataset.mediaId;if(id)deleteTranscript(id);$("#detailsModal").classList.add("hidden")}})

$("#player").addEventListener("timeupdate",()=>{
 const t=$("#player").currentTime;
 $("#playerTranscript .ptSeg").forEach(b=>{
   const n=Number(b.dataset.time), next=b.nextElementSibling?.dataset.time;
   b.classList.toggle("current",t>=n && (!next||t<Number(next)));
   if(b.classList.contains("current"))b.scrollIntoView({block:"nearest"});
 });
});

document.querySelector("#rebuildIndex")?.addEventListener("click",()=>{buildIndex();toast("Media index rebuilt")});
window.addEventListener("load",()=>{buildIndex();renderIndexStatus()});

document.querySelectorAll(".quickAsk button").forEach(b=>b.onclick=()=>{$("#askInput").value=b.dataset.q;ask()});

window.addEventListener("load",()=>{try{renderIntelligenceDashboard()}catch(e){}});

window.addEventListener("vg:activity-refresh",renderAIActivityCenter);

window.addEventListener("load",()=>{try{renderAIActivityCenter()}catch(e){}});

window.addEventListener("load",()=>{try{renderMediaCommandCenter()}catch(e){}});

window.addEventListener("load",()=>{try{renderMediaCopilot()}catch(e){}});

window.addEventListener("load",()=>{try{bindRealPlayerContext()}catch(e){}});

new MutationObserver(()=>{try{bindRealPlayerContext()}catch(e){}}).observe(document.documentElement,{childList:true,subtree:true});

function renderUnifiedMediaWorkspace(){
  const root=document.getElementById("unifiedMediaWorkspace"); if(!root)return;
  const c=getMediaContext(), segs=c?getContextTranscript(c.mediaId):[];
  root.innerHTML=`<div class="umw-head"><div><div class="eyebrow">MEDIA WORKSPACE</div><h2>${c?escapeHtml(c.title):"Select a media item"}</h2><p>${c?"Playback, transcript, Copilot and saved intelligence in one place.":"Open a media item from your library to begin."}</p></div><span class="umw-live">${c?"● CONTEXT ACTIVE":"○ WAITING"}</span></div>
  <div class="umw-grid">
    <section class="umw-player">${c?`<div class="umw-player-shell"><video controls data-media-id="${escapeHtml(c.mediaId)}"></video><div class="umw-player-meta"><span>${Math.floor((c.timestamp||0)/60)}:${String(Math.floor((c.timestamp||0)%60)).padStart(2,"0")}</span><button id="umwSaveMoment">Save moment</button></div></div>`:`<div class="umw-empty">Choose media from your library to activate the workspace.</div>`}</section>
    <section class="umw-transcript"><div class="umw-title">Transcript <span>${segs.length} passages</span></div><div class="umw-segments">${segs.slice(0,60).map((x,i)=>`<button class="umw-segment" data-t="${Number(x.start||0)}"><small>${Math.floor(Number(x.start||0)/60)}:${String(Math.floor(Number(x.start||0)%60)).padStart(2,"0")}</small><span>${escapeHtml(x.text)}</span></button>`).join("")||`<div class="umw-empty">No transcript available for this media.</div>`}</div></section>
  </div>`;
  const video=root.querySelector("video");
  if(video&&c){video.addEventListener("loadedmetadata",()=>{try{video.currentTime=Number(c.timestamp)||0}catch(e){}});video.addEventListener("timeupdate",()=>{setMediaContext({...c,timestamp:video.currentTime,source:"player"});renderMediaContext()});}
  root.querySelectorAll(".umw-segment").forEach(b=>b.addEventListener("click",()=>jumpPlayerToContext(Number(b.dataset.t))));
  root.querySelector("#umwSaveMoment")?.addEventListener("click",()=>saveContextHighlight());
}

window.addEventListener("load",()=>{try{renderUnifiedMediaWorkspace()}catch(e){}});

function renderUnifiedIntelligencePanel(){
  const root=document.getElementById("unifiedIntelligencePanel"); if(!root)return;
  const c=getMediaContext();
  const notes=(typeof getContextNotes==="function"&&c)?(getContextNotes(c.mediaId)||[]):[];
  const highlights=(typeof getContextHighlights==="function"&&c)?(getContextHighlights(c.mediaId)||[]):[];
  root.innerHTML=`<div class="uip-head"><div><div class="eyebrow">MEDIA INTELLIGENCE</div><h3>${c?"Copilot for this media":"Media Copilot"}</h3><p>${c?"Ask, capture, and revisit intelligence without leaving the player.":"Open media to activate contextual intelligence."}</p></div><span class="uip-private">LOCAL CONTEXT</span></div>
  <div class="uip-grid">
    <section class="uip-copilot"><div class="uip-label">Quick actions</div><div class="uip-actions">
      <button data-q="Summarize this media">Summarize</button><button data-q="What are the key points?">Key points</button><button data-q="What was said here?">What was said here?</button><button data-q="Save this moment">Save moment</button>
    </div><div class="uip-answer" id="uipAnswer">Choose a question to work with the current media context.</div></section>
    <section class="uip-memory"><div class="uip-label">Saved intelligence</div><div class="uip-memory-row"><b>${highlights.length}</b><span>highlights</span><b>${notes.length}</b><span>notes</span></div><div class="uip-items">${highlights.slice(0,3).map(x=>`<button class="uip-item" data-t="${Number(x.timestamp||x.start||0)}"><b>Highlight</b><span>${escapeHtml(x.text||x.title||"Saved moment")}</span></button>`).join("")||`<div class="uip-empty">Your saved moments will appear here.</div>`}</div></section>
  </div>`;
  root.querySelectorAll("[data-q]").forEach(b=>b.addEventListener("click",()=>{
    const q=b.dataset.q;
    if(q==="Save this moment"){try{vgUXSetBusy(root,true); saveContextHighlight(); vgUXNotify("Moment saved","success");}catch(e){vgUXNotify("Could not save this moment","error")}finally{vgUXSetBusy(root,false)} renderUnifiedIntelligencePanel(); return;}
    const ans=document.getElementById("uipAnswer");
    try{vgUXSetBusy(root,true); ans.textContent=(typeof answerContextQuestion==="function")?answerContextQuestion(q):"Context question ready — connect Copilot to generate the grounded answer."; vgUXNotify("Context updated","success")}catch(e){ans.textContent="This action is unavailable right now. Try again."; vgUXNotify("Copilot action unavailable","error")}finally{vgUXSetBusy(root,false)}
    if(typeof logMediaActivity==="function")try{logMediaActivity({type:"copilot_quick_action",query:q,mediaId:c?.mediaId||null})}catch(e){}
  }));
  root.querySelectorAll(".uip-item").forEach(b=>b.addEventListener("click",()=>jumpPlayerToContext(Number(b.dataset.t))));
}

function vgUXNotify(message,type="info"){
  let n=document.getElementById("vgUxToast");
  if(!n){n=document.createElement("div");n.id="vgUxToast";n.className="vg-ux-toast";document.body.appendChild(n);}
  n.dataset.type=type;n.textContent=message;n.classList.add("show");
  clearTimeout(window.__vgUxToastTimer);window.__vgUxToastTimer=setTimeout(()=>n.classList.remove("show"),2600);
}
function vgUXSetBusy(root,busy){
  if(!root)return; root.classList.toggle("is-busy",!!busy);
  root.setAttribute("aria-busy",busy?"true":"false");
}
function vgUXInstallKeyboard(){
  document.addEventListener("keydown",e=>{
    if(e.key==="Escape"){document.querySelectorAll(".vg-ux-dialog,.vg-ux-menu").forEach(x=>x.remove());}
    if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="k"){e.preventDefault();document.querySelector('[data-q="Summarize this media"]')?.focus();}
  });
}
window.addEventListener("error",e=>{if(e?.message)vgUXNotify("Something went wrong. Your media is safe.","error")});
window.addEventListener("unhandledrejection",()=>vgUXNotify("The action could not be completed. Try again.","error"));
vgUXInstallKeyboard();

function renderReleaseWorkspaceRail(){
  const root=document.getElementById("releaseWorkspaceRail"); if(!root)return;
  const c=typeof getMediaContext==="function"?getMediaContext():null;
  const hasTranscript=!!(c&&typeof getContextTranscript==="function"&&getContextTranscript(c.mediaId).length);
  root.innerHTML=`<div class="rwr-left"><span class="rwr-dot"></span><div><b>${c?"Workspace ready":"Workspace waiting"}</b><small>${c?"Player • Transcript • Copilot • Highlights":"Select media to activate the full workspace"}</small></div></div><div class="rwr-right"><span>${hasTranscript?"TRANSCRIPT READY":"NO TRANSCRIPT"}</span><span>${c?"CONTEXT LIVE":"OFFLINE"}</span><button id="rwrFocus">Focus player</button></div>`;
  root.querySelector("#rwrFocus")?.addEventListener("click",()=>document.querySelector(".umw-player video")?.focus());
}
window.addEventListener("load",()=>{try{renderReleaseWorkspaceRail()}catch(e){}});


// P159: account/device activation UI. Device ID stays in browser-local storage.
function vgLicenseDeviceId(){
 let id=localStorage.getItem("vg-license-device-id");
 if(!id){id=(crypto.randomUUID?crypto.randomUUID():"vg-"+Date.now()+"-"+Math.random().toString(36).slice(2));localStorage.setItem("vg-license-device-id",id)}
 return id;
}
function vgLicenseRender(status,message){
 const state=document.getElementById("licenseState"),msg=document.getElementById("licenseMessage");
 const dev=document.getElementById("licenseDevice"),deactivate=document.getElementById("deactivateLicenseBtn");
 if(state){state.textContent=status.active?"ACTIVE":status.reason==="not_activated"?"NOT ACTIVATED":"UNAVAILABLE";state.className="pill "+(status.active?"":"muted")}
 if(msg)msg.textContent=message||(status.active?"License is active on this device.":"Enter your license key to activate this device.");
 if(dev)dev.textContent="Device ID: "+vgLicenseDeviceId();
 if(deactivate)deactivate.disabled=!status.active;
}
async function vgLicenseRefresh(){
 try{const r=await fetch(vgApiUrl("/api/license/status?device_id="+encodeURIComponent(vgLicenseDeviceId())));const d=await r.json();if(!r.ok)throw Error(d.error||"Status check failed");vgLicenseRender(d,"")}
 catch(e){vgLicenseRender({active:false,reason:"unavailable"},"Cloud license service unavailable. Please retry when the online service is available.")}
}
async function vgLicenseAction(action){
 const key=document.getElementById("licenseKeyInput")?.value?.trim()||"";
 if(!key)return vgLicenseRender({active:false,reason:"not_activated"},"Enter your license key first.");
 const button=document.getElementById(action==="activate"?"activateLicenseBtn":"deactivateLicenseBtn");if(button)button.disabled=true;
 try{
  const r=await fetch(vgApiUrl("/api/license/"+action),{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({license_key:key,device_id:vgLicenseDeviceId()})});
  const d=await r.json().catch(()=>({}));if(!r.ok)throw Error(d.error||"License request failed");
  vgLicenseRender({active:!!d.active,reason:d.active?"active":"not_activated"},d.message||"License updated.");
  if(action==="deactivate"){const input=document.getElementById("licenseKeyInput");if(input)input.value=""}
 }catch(e){vgLicenseRender({active:false,reason:"error"},e.message||"License request failed.")}
 finally{if(button)button.disabled=false}
}
window.addEventListener("load",()=>{
 document.getElementById("activateLicenseBtn")?.addEventListener("click",()=>vgLicenseAction("activate"));
 document.getElementById("deactivateLicenseBtn")?.addEventListener("click",()=>vgLicenseAction("deactivate"));
 if(document.getElementById("licenseState"))vgLicenseRefresh();
});
