/* VidGravity Create Studio: local import preview and device camera recording. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const fileInput = $('vgLocalMediaInput');
  const importStatus = $('vgImportStatus');
  const preview = $('vgLocalPreview');
  const previewName = $('vgLocalPreviewName');
  const previewStage = $('vgLocalPreviewStage');
  const cameraVideo = $('vgCameraPreview');
  const cameraPlaceholder = $('vgCameraPlaceholder');
  const cameraStatus = $('vgCameraStatus');
  const startBtn = $('vgCameraStart');
  const flipBtn = $('vgCameraFlip');
  const recordBtn = $('vgRecordToggle');
  const stopBtn = $('vgCameraStop');
  if (!fileInput || !cameraVideo) return;

  let previewUrls = [];
  let stream = null;
  let recorder = null;
  let chunks = [];
  let facingMode = 'user';
  let recording = false;

  const status = (el, message) => { if (el) el.textContent = message; };
  const revokePreviews = () => { previewUrls.forEach(url => URL.revokeObjectURL(url)); previewUrls = []; };
  const showFiles = files => {
    if (!files.length) return;
    revokePreviews();
    previewStage.replaceChildren();
    preview.hidden = false;
    const first = files[0];
    previewName.textContent = first.name;
    files.forEach(file => {
      const url = URL.createObjectURL(file);
      previewUrls.push(url);
      const media = document.createElement(file.type.startsWith('audio/') ? 'audio' : 'video');
      media.controls = true;
      media.preload = 'metadata';
      media.src = url;
      if (media.tagName === 'VIDEO') media.playsInline = true;
      media.setAttribute('aria-label', file.name);
      const title = document.createElement('p');
      title.textContent = file.name + ' · ' + (file.size < 1024 * 1024 ? Math.max(1, Math.round(file.size / 1024)) + ' KB' : (file.size / 1024 / 1024).toFixed(1) + ' MB');
      previewStage.append(title, media);
    });
    status(importStatus, files.length + ' file' + (files.length === 1 ? '' : 's') + ' ready for local preview.');
  };
  fileInput.addEventListener('change', () => showFiles(Array.from(fileInput.files || [])));
  $('vgLocalPreviewClose')?.addEventListener('click', () => { preview.hidden = true; previewStage.replaceChildren(); revokePreviews(); });
  window.addEventListener('pagehide', revokePreviews);

  async function stopCamera() {
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    if (stream) stream.getTracks().forEach(track => track.stop());
    stream = null;
    cameraVideo.srcObject = null;
    cameraPlaceholder.hidden = false;
    startBtn.disabled = false;
    flipBtn.disabled = true;
    recordBtn.disabled = true;
    stopBtn.disabled = true;
    status(cameraStatus, 'Camera is off.');
  }
  async function startCamera() {
    if (!navigator.mediaDevices?.getUserMedia) {
      status(cameraStatus, 'Camera access is not available here. Open the installed PWA over HTTPS on a supported device.');
      return;
    }
    try {
      if (stream) await stopCamera();
      stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: { facingMode: { ideal: facingMode }, width: { ideal: 1280 }, height: { ideal: 720 } }
      });
      cameraVideo.srcObject = stream;
      cameraPlaceholder.hidden = true;
      await cameraVideo.play().catch(() => {});
      startBtn.disabled = true;
      flipBtn.disabled = false;
      recordBtn.disabled = typeof MediaRecorder === 'undefined';
      stopBtn.disabled = false;
      status(cameraStatus, 'Camera ready · ' + (facingMode === 'user' ? 'front' : 'rear') + ' camera. Recording stays on this device.');
    } catch (error) {
      status(cameraStatus, error.name === 'NotAllowedError'
        ? 'Camera/microphone permission was denied. Allow access in your browser settings and try again.'
        : 'Could not start camera: ' + (error.message || 'device unavailable') + '. Close other camera apps and retry.');
      await stopCamera();
    }
  }
  function chooseMime() {
    if (typeof MediaRecorder === 'undefined' || !MediaRecorder.isTypeSupported) return '';
    return ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4']
      .find(type => MediaRecorder.isTypeSupported(type)) || '';
  }
  function saveRecording(blob) {
    if (!blob || !blob.size) { status(cameraStatus, 'No recording data was produced. Try recording again.'); return; }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const ext = blob.type.includes('mp4') ? 'mp4' : 'webm';
    link.download = 'VidGravity-clip-' + new Date().toISOString().replace(/[:.]/g, '-') + '.' + ext;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    status(cameraStatus, 'Recording saved to your device as ' + ext.toUpperCase() + '.');
  }
  function startRecording() {
    if (!stream || typeof MediaRecorder === 'undefined') return;
    try {
      chunks = [];
      const mimeType = chooseMime();
      recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      recorder.addEventListener('dataavailable', event => { if (event.data?.size) chunks.push(event.data); });
      recorder.addEventListener('stop', () => {
        const type = recorder?.mimeType || chunks[0]?.type || 'video/webm';
        saveRecording(new Blob(chunks, { type }));
        recording = false;
        recordBtn.textContent = 'Record';
        recordBtn.classList.remove('recording');
      }, { once: true });
      recorder.start(500);
      recording = true;
      recordBtn.textContent = 'Stop recording';
      recordBtn.classList.add('recording');
      status(cameraStatus, 'Recording… press “Stop recording” to save the clip.');
    } catch (error) {
      status(cameraStatus, 'Recording could not start: ' + (error.message || 'unsupported format'));
    }
  }
  function stopRecording() {
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    recording = false;
    recordBtn.textContent = 'Record';
    recordBtn.classList.remove('recording');
  }
  startBtn.addEventListener('click', startCamera);
  stopBtn.addEventListener('click', stopCamera);
  flipBtn.addEventListener('click', async () => {
    facingMode = facingMode === 'user' ? 'environment' : 'user';
    await startCamera();
  });
  recordBtn.addEventListener('click', () => recording ? stopRecording() : startRecording());
  document.addEventListener('visibilitychange', () => { if (document.hidden && stream && !recording) stopCamera(); });
})();
