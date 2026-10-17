/* Player chrome interactions and single-owner shortcut registry. */
(() => {
  "use strict";
  const $=s=>document.querySelector(s);
  const player=()=>$("#player");
  let pointA=null,pointB=null,idleHandle=null;
  const cueBaselines=new WeakMap();
  const shortcuts=new Map([["f","fullscreen"]]);
  function modalOpen(){return !$("#playerModal")?.classList.contains("hidden");}
  function settings(open){
    const pop=$("#playerSettingsPopover"),button=$("#playerSettingsButton");
    if(!pop||!button)return;
    const next=typeof open==="boolean"?open:pop.hidden;
    pop.hidden=!next;button.setAttribute("aria-expanded",String(next));
  }
  function renderLoopStatus(){
    const el=$("#playerABStatus");if(!el)return;
    el.textContent=pointA===null?"Loop disabled":pointB===null?"A set at "+pointA.toFixed(2)+"s · choose B":"Loop "+pointA.toFixed(2)+"s → "+pointB.toFixed(2)+"s";
  }
  function populateTracks(){
    const v=player();if(!v)return;
    const audio=$("#playerAudioTrack");
    if(audio&&v.audioTracks&&v.audioTracks.length){
      audio.innerHTML='<option value="auto">Default audio</option>';
      Array.from(v.audioTracks).forEach((track,index)=>{const o=document.createElement("option");o.value=String(index);o.textContent=track.label||track.language||("Audio "+(index+1));audio.appendChild(o);});
      audio.disabled=false;
      audio.onchange=()=>Array.from(v.audioTracks).forEach((track,index)=>{track.enabled=audio.value==="auto"?index===0:index===Number(audio.value);});
    }else if(audio){audio.innerHTML='<option value="auto">Browser default</option>';audio.disabled=true;audio.title="This browser does not expose selectable audio tracks for this file";}
    const subs=$("#playerSubtitleTrack");
    if(subs){
      subs.innerHTML='<option value="-1">Off / automatic</option>';
      Array.from(v.textTracks||[]).forEach((track,index)=>{const o=document.createElement("option");o.value=String(index);o.textContent=track.label||track.language||("Subtitle "+(index+1));subs.appendChild(o);});
      subs.disabled=!v.textTracks||!v.textTracks.length;
      subs.onchange=()=>Array.from(v.textTracks||[]).forEach((track,index)=>{track.mode=subs.value===String(index)?"showing":"disabled";});
    }
  }
  function bindLoop(){
    $("#playerSetA")?.addEventListener("click",()=>{const v=player();if(!v)return;pointA=v.currentTime;pointB=null;renderLoopStatus();});
    $("#playerSetB")?.addEventListener("click",()=>{const v=player();if(!v)return;if(pointA===null){pointA=v.currentTime;pointB=null;}else if(v.currentTime>pointA)pointB=v.currentTime;else{pointA=v.currentTime;pointB=null;}renderLoopStatus();});
    $("#playerClearAB")?.addEventListener("click",()=>{pointA=null;pointB=null;renderLoopStatus();});
    player()?.addEventListener("timeupdate",()=>{const v=player();if(v&&pointA!==null&&pointB!==null&&v.currentTime>=pointB){v.currentTime=pointA;v.play().catch(()=>{});}});
    player()?.addEventListener("loadedmetadata",()=>{pointA=null;pointB=null;renderLoopStatus();populateTracks();});
  }
  function applySubtitleDelay(){
    const v=player(),input=$("#playerSubtitleDelay");if(!v||!input)return;
    const ms=Number(input.value);if(!Number.isFinite(ms)||ms < -10000||ms > 10000)return;
    const delta=ms/1000;
    Array.from(v.textTracks||[]).forEach(track=>Array.from(track.cues||[]).forEach(cue=>{
      if(!cueBaselines.has(cue))cueBaselines.set(cue,{start:cue.startTime,end:cue.endTime});
      const base=cueBaselines.get(cue);cue.startTime=Math.max(0,base.start+delta);cue.endTime=Math.max(cue.startTime+.01,base.end+delta);
    }));
  }
  function resetIdle(){
    const box=$("#playerModal .playerBox");if(!box)return;
    box.classList.remove("vg-controls-idle");if(idleHandle)clearTimeout(idleHandle);
    if(modalOpen()&&!player()?.paused)idleHandle=setTimeout(()=>{if(modalOpen()&&!player()?.paused)box.classList.add("vg-controls-idle");},2500);
  }
  function bindIdle(){
    const box=$("#playerModal .playerBox");if(!box)return;
    ["mousemove","pointerdown","touchstart","keydown"].forEach(event=>box.addEventListener(event,resetIdle,{passive:true}));
    player()?.addEventListener("play",resetIdle);player()?.addEventListener("pause",()=>{if(idleHandle)clearTimeout(idleHandle);box.classList.remove("vg-controls-idle");});
    $("#closePlayer")?.addEventListener("click",()=>{if(idleHandle)clearTimeout(idleHandle);box.classList.remove("vg-controls-idle");settings(false);});
  }
  $("#playerSettingsButton")?.addEventListener("click",()=>settings());
  document.addEventListener("click",event=>{const wrap=$(".vg-player-settings");if(wrap&&!wrap.contains(event.target))settings(false);});
  $("#playerSubtitleDelay")?.addEventListener("change",applySubtitleDelay);
  $("#playerFullscreen")?.setAttribute("title","Full screen (F)");
  document.addEventListener("keydown",async event=>{
    if(!modalOpen()||event.ctrlKey||event.altKey||event.metaKey)return;
    if(/^(INPUT|TEXTAREA|SELECT)$/.test(event.target?.tagName||""))return;
    if(shortcuts.get(event.key.toLowerCase())!=="fullscreen")return;
    event.preventDefault();
    try{const v=player();if(document.fullscreenElement)await document.exitFullscreen();else if(v?.requestFullscreen)await v.requestFullscreen();else if(v?.webkitEnterFullscreen)v.webkitEnterFullscreen();}catch(_){}
  });
  window.addEventListener("vg:library-updated",()=>{if(typeof dbAll==="function"&&typeof renderAll==="function")dbAll().then(items=>{media=items;renderAll();}).catch(()=>{});});
  window.addEventListener("load",()=>{bindLoop();bindIdle();populateTracks();});
})();