import { useCallback, useEffect, useMemo, useState } from "react";
import type { RefObject } from "react";
import type { PlaybackSpeed, PlayerCapabilities } from "../../../packages/contracts/src/player";

const SPEEDS: PlaybackSpeed[] = [0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3,3.5,4];
export function useMediaPlayer(videoRef: RefObject<HTMLVideoElement | null>) {
  const [speed, setSpeed] = useState<PlaybackSpeed>(1);
  const [loopA, setLoopA] = useState<number | null>(null);
  const [loopB, setLoopB] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pip, setPip] = useState(false);
  const capabilities: PlayerCapabilities = useMemo(() => ({
    frameStepping: true,
    pictureInPicture: typeof document !== "undefined" && "pictureInPictureEnabled" in document && document.pictureInPictureEnabled,
    pitchCorrection: typeof document !== "undefined" && "preservesPitch" in document.createElement("video"),
    loudnessNormalization: false,
    audioTrackSelection: false,
    subtitleSelection: !!videoRef.current?.textTracks,
    shaderEnhancements: { shaderUpscale: false, sharpening: false, deinterlace: false, colorAdjustments: false,
      reasonUnavailable: "GPU shader pipeline is not connected in this build." }
  }), [videoRef]);
  const applySpeed = useCallback((value: PlaybackSpeed) => {
    const video = videoRef.current; if (!video) return;
    video.playbackRate = value;
    if ("preservesPitch" in video) (video as HTMLVideoElement & {preservesPitch:boolean}).preservesPitch = true;
    setSpeed(value);
  }, [videoRef]);
  const stepFrame = useCallback((direction: -1 | 1) => {
    const video = videoRef.current; if (!video) return;
    video.pause();
    const frame = 1 / 30;
    video.currentTime = Math.max(0, Math.min(video.duration || Infinity, video.currentTime + direction * frame));
  }, [videoRef]);
  const setA = useCallback(() => { if (videoRef.current) setLoopA(videoRef.current.currentTime); }, [videoRef]);
  const setB = useCallback(() => {
    const video = videoRef.current;
    if (!video || loopA === null || video.currentTime <= loopA) { setMessage("Set point A, then set point B later in the media."); return; }
    setLoopB(video.currentTime);
  }, [videoRef, loopA]);
  const togglePip = useCallback(async () => {
    const video = videoRef.current;
    if (!video || !document.pictureInPictureEnabled) { setMessage("Picture-in-picture is not supported by this browser."); return; }
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await video.requestPictureInPicture();
    } catch { setMessage("Picture-in-picture could not be started for this media."); }
  }, [videoRef]);
  useEffect(() => {
    const video = videoRef.current; if (!video) return;
    const enforceLoop = () => { if (loopA !== null && loopB !== null && video.currentTime >= loopB) video.currentTime = loopA; };
    const onEnter = () => setPip(true), onLeave = () => setPip(false);
    video.addEventListener("timeupdate", enforceLoop);
    video.addEventListener("enterpictureinpicture", onEnter); video.addEventListener("leavepictureinpicture", onLeave);
    return () => { video.removeEventListener("timeupdate", enforceLoop); video.removeEventListener("enterpictureinpicture", onEnter); video.removeEventListener("leavepictureinpicture", onLeave); };
  }, [videoRef, loopA, loopB]);
  return { speed, speeds: SPEEDS, applySpeed, stepFrame, loopA, loopB, setA, setB, clearLoop: () => { setLoopA(null); setLoopB(null); }, togglePip, pip, message, clearMessage: () => setMessage(null), capabilities };
}
