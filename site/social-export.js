/* Social export workflow for VidGravity's vanilla JS + Capacitor PWA. */
(() => {
  "use strict";
  const PRESETS = [
    {id:"whatsapp-status",label:"WhatsApp Status",w:1080,h:1920,shape:"story",safe:"whatsapp"},
    {id:"instagram-story",label:"Instagram Story",w:1080,h:1920,shape:"story",safe:"center"},
    {id:"instagram-reels",label:"Instagram Reels",w:1080,h:1920,shape:"story",safe:"center"},
    {id:"youtube-shorts",label:"YouTube Shorts",w:1080,h:1920,shape:"story",safe:"center"},
    {id:"tiktok",label:"TikTok",w:1080,h:1920,shape:"story",safe:"center"},
    {id:"instagram-feed-portrait",label:"Instagram In-Feed",w:1080,h:1350,shape:"portrait"},
    {id:"linkedin-portrait",label:"LinkedIn Portrait",w:1080,h:1350,shape:"portrait"},
    {id:"square",label:"Square Feed",w:1080,h:1080,shape:"square"},
    {id:"youtube-landscape",label:"YouTube Landscape",w:1920,h:1080,shape:"landscape"},
    {id:"x-landscape",label:"X Landscape",w:1920,h:1080,shape:"landscape"},
    {id:"custom",label:"Custom Ratio",w:1080,h:1920,shape:"story"}
  ];
  const $ = (s,r=document) => r.querySelector(s);
  let state={id:null,item:null,blob:null,sourceUrl:null,preset:PRESETS[0],outputMode:"optimized",framing:"smart-reframe",customW:1080,customH:1920,returnFocus:null,busy:false};
  const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const presetById=id=>PRESETS.find(p=>p.id===id)||PRESETS[0];
  const even=n=>Math.min(2160,Math.max(144,Math.floor((Number(n)||1080)/2)*2));
  function dims(){return state.preset.id==="custom"?{w:even(state.customW),h:even(state.customH)}:{w:state.preset.w,h:state.preset.h};}
  function graphFor(w,h,mode){
    if(!Number.isInteger(w)||!Number.isInteger(h)||w<144||h<144||w>2160||h>2160||w%2||h%2)throw Error("Invalid output dimensions");
    if(mode==="smart-reframe")return "scale="+w+":"+h+":force_original_aspect_ratio=increase,crop="+w+":"+h+",setsar=1,format=yuv420p";
    if(mode==="fit-letterbox")return "scale="+w+":"+h+":force_original_aspect_ratio=decrease,pad="+w+":"+h+":(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p";
    if(mode==="blurred-backdrop")return "split=2[bg][fg];[bg]scale="+w+":"+h+":force_original_aspect_ratio=increase,crop="+w+":"+h+",boxblur=20:10[blurred];[fg]scale="+w+":"+h+":force_original_aspect_ratio=decrease,setsar=1[foreground];[blurred][foreground]overlay=(W-w)/2:(H-h)/2,setsar=1,format=yuv420p";
    throw Error("Unsupported framing mode");
  }
  window.VidGravitySocialExport=Object.freeze({presets:PRESETS.map(p=>({...p})),buildFilterGraph:graphFor});
  function modalMarkup(){
    return '<div id="vgSocialExportModal" class="modal hidden" aria-hidden="true"><div class="modalBox vg-export-dialog" role="dialog" aria-modal="true" aria-labelledby="vgExportTitle">'+
      '<button class="close" id="vgExportClose" type="button" aria-label="Close social export">×</button>'+
      '<div class="vg-export-head"><div><h2 id="vgExportTitle">Share to Social Media</h2><p>Choose the original media or prepare a platform-optimized derivative.</p></div></div>'+
      '<div class="vg-export-layout"><section class="vg-export-preview-panel"><div class="vg-export-preview-top"><strong>Live framing preview</strong><span class="vg-export-chip" id="vgExportSizeChip">1080 × 1920</span></div>'+
      '<div class="vg-export-stage"><div class="vg-export-canvas" id="vgExportCanvas" data-mode="smart-reframe"><video id="vgExportVideo" playsinline muted loop></video><div class="vg-export-safe" id="vgExportSafe"></div></div></div>'+
      '<p class="vg-export-hint" id="vgExportPreviewHint">Preview is illustrative; final framing is applied by the FFmpeg engine.</p>'+
      '<div class="vg-export-row"><button type="button" class="smallBtn" id="vgExportPreviewPlay">▶ Preview</button><span class="vg-export-chip" id="vgExportDuration">Duration: —</span><span class="vg-export-chip" id="vgExportOriginalSize">Original: —</span></div></section>'+
      '<section class="vg-export-controls"><div class="vg-export-section"><h3>Output mode</h3><div class="vg-export-modes">'+
      '<button type="button" class="vg-export-mode" data-vg-output="original" aria-pressed="false"><span>◉</span><span><b>Original Format</b><small>Keep original resolution, aspect ratio and codec.</small></span></button>'+
      '<button type="button" class="vg-export-mode" data-vg-output="optimized" aria-pressed="true"><span>✦</span><span><b>Optimized for Platform</b><small>Reframe the canvas for the selected social platform.</small></span></button></div></div>'+
      '<div class="vg-export-section"><h3>Platform preset</h3><div class="vg-export-presets" id="vgExportPresets"></div></div>'+
      '<div class="vg-export-section"><h3>Framing</h3><div class="vg-export-row"><label class="vg-export-field">Adaptation<select id="vgExportMode"><option value="smart-reframe">Smart Reframe / Center Crop</option><option value="blurred-backdrop">Blurred Backdrop</option><option value="fit-letterbox">Fit / Letterbox</option></select></label>'+
      '<label class="vg-export-field">Trim duration (seconds)<input id="vgExportTrim" type="number" min="1" max="3600" step="1" placeholder="Full duration"></label></div>'+
      '<div class="vg-export-row" id="vgExportCustomFields" hidden><label class="vg-export-field">Canvas width<input id="vgExportWidth" type="number" min="144" max="2160" step="2" value="1080"></label><label class="vg-export-field">Canvas height<input id="vgExportHeight" type="number" min="144" max="2160" step="2" value="1920"></label></div></div>'+
      '<div class="vg-export-estimate"><span>Estimated output</span><strong id="vgExportEstimate">Calculating…</strong></div>'+
      '<div class="vg-export-actions"><button id="vgExportDirectShare" class="primary" type="button">↗ Direct Share</button><button id="vgExportSave" class="smallBtn" type="button">↓ Save Derivative</button><button id="vgExportCopy" class="smallBtn" type="button">▣ Copy to Clipboard</button></div>'+
      '<p id="vgExportStatus" class="vg-export-status" role="status" aria-live="polite"></p></section></div></div></div>';
  }
  function ensureModal(){if($("#vgSocialExportModal"))return;document.body.insertAdjacentHTML("beforeend",modalMarkup());bindModal();}
  function setStatus(message,kind){const el=$("#vgExportStatus");if(el){el.textContent=message;el.dataset.kind=kind||"";}}
  function busy(on){state.busy=on;["#vgExportDirectShare","#vgExportSave","#vgExportCopy"].forEach(s=>{const b=$(s);if(b)b.disabled=on;});}
  function renderPresets(){
    const root=$("#vgExportPresets");if(!root)return;
    root.innerHTML=PRESETS.map(p=>'<button type="button" class="vg-export-preset" data-vg-preset="'+p.id+'" aria-pressed="'+(state.preset.id===p.id)+'"><span class="vg-export-ratio '+p.shape+'"></span><span><b>'+esc(p.label)+'</b><small>'+p.w+' × '+p.h+'</small></span></button>').join("");
    root.querySelectorAll("[data-vg-preset]").forEach(b=>b.addEventListener("click",()=>{state.preset=presetById(b.dataset.vgPreset);renderPresets();updatePreview();}));
  }
  function updatePreview(){
    const d=dims(),canvas=$("#vgExportCanvas"),safe=$("#vgExportSafe");
    $("#vgExportSizeChip").textContent=d.w+" × "+d.h;
    canvas.style.aspectRatio=d.w+" / "+d.h;canvas.dataset.mode=state.framing;
    safe.hidden=state.outputMode==="original"||state.preset.shape!=="story";
    safe.classList.toggle("whatsapp",state.preset.safe==="whatsapp");
    $("#vgExportCustomFields").hidden=state.preset.id!=="custom";
    $("#vgExportMode").disabled=state.outputMode==="original";
    document.querySelectorAll("[data-vg-output]").forEach(b=>b.setAttribute("aria-pressed",String(b.dataset.vgOutput===state.outputMode)));
    const video=$("#vgExportVideo");video.style.objectFit=state.outputMode==="original"||state.framing==="fit-letterbox"||state.framing==="blurred-backdrop"?"contain": "cover";
    const duration=Number(state.item&&state.item.duration)||0;
    $("#vgExportDuration").textContent="Duration: "+(duration?formatTime(duration):"—");
    $("#vgExportOriginalSize").textContent="Original: "+(state.item&&state.item.width&&state.item.height?state.item.width+" × "+state.item.height:"metadata unavailable");
    $("#vgExportEstimate").textContent=state.blob?(Math.max(.1,state.blob.size/1048576*(state.outputMode==="original"?1:.72)).toFixed(1)+" MB approx."):"Size unavailable";
    $("#vgExportPreviewHint").textContent=state.preset.safe==="whatsapp"?"WhatsApp Status: keep faces and text away from top/bottom overlays; long clips may need trimming or splitting.":state.preset.shape==="story"?"The guide marks a central 1080 × 1350 safe area inside the vertical canvas.":"Preview guides show the output canvas; the source file remains unchanged.";
  }
  async function loadMedia(id){
    if(id==="__app__")return {id,title:"VidGravity",url:location.href,type:"Web app"};
    const db=await new Promise((resolve,reject)=>{const req=indexedDB.open("vidgravity",2);req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
    return new Promise((resolve,reject)=>{const req=db.transaction("media","readonly").objectStore("media").get(id);req.onsuccess=()=>resolve(req.result||null);req.onerror=()=>reject(req.error);});
  }
  function openModal(id, caller){
    ensureModal();state.id=id;state.item=null;state.blob=null;state.returnFocus=caller||document.activeElement;
    const modal=$("#vgSocialExportModal");modal.classList.remove("hidden");modal.setAttribute("aria-hidden","false");
    state.preset=PRESETS[0];state.outputMode="optimized";state.framing="smart-reframe";renderPresets();updatePreview();setStatus("Loading saved media…");
    loadMedia(id).then(item=>{
      if(!item){setStatus("This item is not available in the local Library.","error");return;}
      state.item=item;state.blob=item.blob instanceof Blob?item.blob:null;
      if(state.sourceUrl)URL.revokeObjectURL(state.sourceUrl);state.sourceUrl=null;
      const video=$("#vgExportVideo");video.pause();video.removeAttribute("src");
      if(state.blob){state.sourceUrl=URL.createObjectURL(state.blob);video.src=state.sourceUrl;video.load();}
      setStatus(state.blob?"Ready. Original media is immutable; exports create a separate Library version.":"No local file is stored. Direct Share can share a URL when available.");
      updatePreview();
    }).catch(e=>setStatus(e.message||"Could not load media.","error"));
    $("#vgExportClose").focus();
  }
  function closeModal(){
    const modal=$("#vgSocialExportModal");if(!modal)return;modal.classList.add("hidden");modal.setAttribute("aria-hidden","true");
    const video=$("#vgExportVideo");video.pause();video.removeAttribute("src");video.load();
    if(state.sourceUrl)URL.revokeObjectURL(state.sourceUrl);state.sourceUrl=null;
    if(state.returnFocus&&state.returnFocus.focus)state.returnFocus.focus();
  }
  function filename(title,type){let n=String(title||"vidgravity-media").replace(/[\\/:*?"<>|]+/g,"_").slice(0,120);if(!/\.[a-z0-9]{2,5}$/i.test(n))n+=type==="video/webm"?".webm":".mp4";return n;}
  async function directShare(){
    if(!state.item)return setStatus("No media is loaded yet.","error");
    try{
      const file=state.blob?new File([state.blob],filename(state.item.title,state.blob.type),{type:state.blob.type||"application/octet-stream"}):null;
      if(navigator.share){const payload={title:state.item.title||"VidGravity media",text:"Shared from VidGravity",url:state.item.url||undefined};if(file&&navigator.canShare&&navigator.canShare({files:[file]})){payload.files=[file];delete payload.url;}await navigator.share(payload);setStatus("Share sheet opened.","success");return;}
      if(state.item.url&&navigator.clipboard&&navigator.clipboard.writeText){await navigator.clipboard.writeText(state.item.url);setStatus("Sharing unavailable; media URL copied instead.","success");return;}
      setStatus("This browser does not support sharing local files. Save the derivative and share it from your device.","error");
    }catch(e){if(e.name!=="AbortError")setStatus(e.message||"Direct Share failed.","error");}
  }
  function saveBlob(blob,name){const url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}
  async function storeDerivative(blob,metadata){
    const db=await new Promise((resolve,reject)=>{const req=indexedDB.open("vidgravity",2);req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});
    const id="derivative-"+Date.now()+"-"+Math.random().toString(36).slice(2,8);
    const item=Object.assign({},state.item,{id,sourceId:state.item.id,versionType:"social-derivative",created:Date.now(),title:(state.item.title||"Media")+" · "+(metadata.preset||"Original copy"),type:blob.type||"video/mp4",size:blob.size,blob,export:metadata});
    await new Promise((resolve,reject)=>{const tx=db.transaction("media","readwrite");tx.objectStore("media").add(item);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});
    return item;
  }
  function apiUrl(path){let base=(localStorage.getItem("vg-api-base-url")||"").trim().replace(/\/+$/,"");try{if(base&&new URL(base).protocol!=="https:")base="";}catch(_){base="";}return (base||"https://vidgravity.onrender.com")+"/"+path.replace(/^\/+/,"");}
  async function saveDerivative(){
    if(!state.item||!state.blob)return setStatus("A locally saved media file is required for export.","error");
    if(state.outputMode==="original"){
      busy(true);try{await storeDerivative(state.blob,{preset:"original",mode:"original-format",width:state.item.width||null,height:state.item.height||null});saveBlob(state.blob,filename(state.item.title,state.blob.type));setStatus("Original-format copy saved as a separate Library version.","success");}catch(e){setStatus(e.message||"Could not save the Library version.","error");}finally{busy(false);}return;
    }
    const d=dims(),trimValue=$("#vgExportTrim").value,trim=trimValue===""?0:Number(trimValue);
    if(trimValue!==""&&(!Number.isFinite(trim)||trim<1||trim>3600))return setStatus("Trim must be between 1 and 3600 seconds.","error");
    let graph;try{graph=graphFor(d.w,d.h,state.framing);}catch(e){return setStatus(e.message,"error");}
    busy(true);setStatus("Exporting a new version with the FFmpeg engine…");
    try{
      const headers={"Content-Type":state.blob.type||"application/octet-stream","X-VG-Preset":state.preset.id,"X-VG-Mode":state.framing,"X-VG-Width":String(d.w),"X-VG-Height":String(d.h)};
      if(trim)headers["X-VG-Trim-Seconds"]=String(trim);
      const response=await fetch(apiUrl("/api/media/export"),{method:"POST",headers,body:state.blob});
      if(!response.ok){let msg="Export failed ("+response.status+").";try{msg=(await response.json()).error||msg;}catch(_){}throw Error(msg);}
      const output=await response.blob();if(!output.size)throw Error("The export engine returned an empty file.");
      const meta={mode:state.framing,preset:state.preset.id,width:d.w,height:d.h,sourceId:state.item.id,created:Date.now(),filterGraph:graph};
      await storeDerivative(output,meta);saveBlob(output,filename((state.item.title||"media")+"-"+state.preset.id,"video/mp4"));
      setStatus("Export complete: "+d.w+" × "+d.h+". Original remains unchanged.","success");
    }catch(e){setStatus(e.message||"Export failed. Original media was not modified.","error");}finally{busy(false);}
  }
  async function copyRendered(){
    if(state.blob&&window.ClipboardItem&&navigator.clipboard&&navigator.clipboard.write){try{await navigator.clipboard.write([new ClipboardItem({[state.blob.type||"application/octet-stream"]:state.blob})]);setStatus("Media copied to clipboard.","success");return;}catch(_){}}
    if(state.item&&state.item.url&&navigator.clipboard&&navigator.clipboard.writeText){try{await navigator.clipboard.writeText(state.item.url);setStatus("Shareable media URL copied.","success");return;}catch(_){}}
    setStatus("This platform cannot place this media file on the clipboard. Use Direct Share or Save Derivative.","error");
  }
  function bindModal(){
    $("#vgExportClose").addEventListener("click",closeModal);
    $("#vgSocialExportModal").addEventListener("click",e=>{if(e.target.id==="vgSocialExportModal")closeModal();});
    $("#vgExportMode").addEventListener("change",e=>{state.framing=e.target.value;updatePreview();});
    document.querySelectorAll("[data-vg-output]").forEach(b=>b.addEventListener("click",()=>{state.outputMode=b.dataset.vgOutput;updatePreview();}));
    $("#vgExportWidth").addEventListener("change",e=>{state.customW=even(e.target.value);e.target.value=state.customW;updatePreview();});
    $("#vgExportHeight").addEventListener("change",e=>{state.customH=even(e.target.value);e.target.value=state.customH;updatePreview();});
    $("#vgExportDirectShare").addEventListener("click",directShare);$("#vgExportSave").addEventListener("click",saveDerivative);$("#vgExportCopy").addEventListener("click",copyRendered);
    $("#vgExportPreviewPlay").addEventListener("click",()=>{const v=$("#vgExportVideo");if(v.paused)v.play().catch(()=>setStatus("Preview playback could not start.","error"));else v.pause();});
    document.addEventListener("keydown",e=>{if(e.key==="Escape"&&!$("#vgSocialExportModal")?.classList.contains("hidden"))closeModal();});
  }
  const oldOpenShare=window.openShare;
  if(typeof oldOpenShare==="function"){window.openShare=function(id){const caller=document.activeElement;oldOpenShare(id);$("#shareModal")?.classList.add("hidden");$("#shareModal")?.setAttribute("aria-hidden","true");openModal(id,caller);};}
  window.addEventListener("load",()=>{if(!$("#vgSocialExportModal"))ensureModal();});
})();