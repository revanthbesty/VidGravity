/** FR-PLAYER / D34 / D38 player contracts. Unsupported capabilities must be reported, not simulated. */
export type PlaybackSpeed = 0.25|0.5|0.75|1|1.25|1.5|1.75|2|2.5|3|3.5|4;
export interface ColorAdjustments { exposure: number; contrast: number; saturation: number; sharpen: number; }
export interface EnhancementCapabilities {
  shaderUpscale: boolean; sharpening: boolean; deinterlace: boolean;
  colorAdjustments: boolean; reasonUnavailable?: string;
}
export interface AudioTrack { id: string; label: string; language?: string; channels?: number; codec?: string; }
export interface SubtitleTrack { id: string; label: string; language?: string; source: "embedded"|"external"; }
export interface ChapterMarker { id: string; title: string; startSeconds: number; endSeconds?: number; }
export interface PlayerState {
  mediaId: string; playing: boolean; currentTime: number; duration: number;
  speed: PlaybackSpeed; pitchCorrection: boolean; loopA?: number; loopB?: number;
  activeAudioTrackId?: string; activeSubtitleTrackId?: string;
  enhancements: { upscale: boolean; sharpen: boolean; deinterlace: boolean; color: boolean; adjustments: ColorAdjustments };
  loudnessNormalization: boolean; chapters: ChapterMarker[];
}
export interface PlayerCapabilities {
  frameStepping: boolean; pictureInPicture: boolean; pitchCorrection: boolean;
  loudnessNormalization: boolean; audioTrackSelection: boolean; subtitleSelection: boolean;
  shaderEnhancements: EnhancementCapabilities;
}
export interface MediaInsightRequest { mediaId: string; currentTimeSeconds: number; query: string; }
export interface MediaInsightResult { answer: string; citations?: { label: string; url?: string; timestampSeconds?: number }[]; }
export interface TranscriptSegment { startSeconds: number; endSeconds: number; text: string; }
export interface MediaProHooks {
  askMyMedia(request: MediaInsightRequest, signal?: AbortSignal): Promise<MediaInsightResult>;
  lookupTranscript(mediaId: string, signal?: AbortSignal): Promise<TranscriptSegment[]>;
  generateChapters(mediaId: string, signal?: AbortSignal): Promise<ChapterMarker[]>;
}
