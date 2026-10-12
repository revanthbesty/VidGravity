/** D24/D33 downloader contracts. Commands are typed operations, never shell strings. */
export type TaskId = string & { readonly __brand: "TaskId" };
export type Sha256 = string & { readonly __brand: "Sha256" };
export type MediaContainer = "mp4" | "webm" | "mkv" | "mp3" | "m4a" | "wav" | "flac";
export type StreamKind = "video" | "audio" | "muxed" | "subtitle";
export interface StreamCandidate {
  id: string; kind: StreamKind; codec: string; container?: MediaContainer;
  bitrateKbps?: number; width?: number; height?: number; fps?: number;
  language?: string; channels?: number; estimatedBytes?: number;
  sourceUrl: string; expiresAt?: string; protocol: "https" | "http" | "file";
}
export interface FormatFallback { streamIds: string[]; output: MediaContainer; reason: string; }
export interface SourceAnalysis {
  canonicalUrl: string; title?: string; durationSeconds?: number;
  streams: StreamCandidate[]; fallbacks: FormatFallback[];
  analyzedAt: string; providerId: string;
}
export type FfmpegFilter =
  | { type: "scale"; width: number; height: number; algorithm: "bilinear"|"bicubic"|"lanczos" }
  | { type: "deinterlace"; mode: "yadif"|"bwdif" }
  | { type: "loudnorm"; integratedLufs: number; truePeakDbtp: number; loudnessRangeLufs: number }
  | { type: "format"; pixelFormat: "yuv420p"|"yuv422p"|"yuv444p" };
export type FfmpegOperation =
  | { op: "input"; inputId: string }
  | { op: "map"; inputId: string; stream: "best-video"|"best-audio"|"all-subtitles" }
  | { op: "filter"; inputId: string; filters: FfmpegFilter[] }
  | { op: "copy"; inputId: string; outputId: string }
  | { op: "mux"; videoInputId?: string; audioInputId?: string; outputId: string; container: MediaContainer }
  | { op: "verify"; outputId: string; algorithm: "sha256"; expected?: Sha256 };
export interface FfmpegOperationGraph { version: 1; operations: FfmpegOperation[]; outputId: string; }
export interface BandwidthPolicy { maxBytesPerSecond?: number; burstBytes?: number; }
export interface DownloadSchedule { notBefore?: string; notAfter?: string; timezone?: string; allowedDays?: number[]; }
export interface IntegrityPolicy { algorithm: "sha256"; expectedHash?: Sha256; requireProviderChecksum: boolean; }
export interface DownloadRequest {
  taskId: TaskId; sourceUrl: string; selectedStreamIds?: string[];
  outputName: string; outputContainer: MediaContainer; outputDirectoryId: string;
  bandwidth?: BandwidthPolicy; schedule?: DownloadSchedule; sequentialPreview?: boolean;
  integrity: IntegrityPolicy; operationGraph?: FfmpegOperationGraph;
}
export type DownloadState =
  | "QUEUED" | "WAITING_FOR_WINDOW" | "ANALYZING" | "READY" | "DOWNLOADING"
  | "PAUSED" | "VERIFYING" | "MERGING" | "COMMITTING" | "COMPLETED"
  | "FAILED" | "CANCELLED";
export interface DownloadProgress {
  taskId: TaskId; state: DownloadState; bytesReceived: number; totalBytes?: number;
  percent?: number; bytesPerSecond: number; previewReadyBytes?: number;
  checksum?: Sha256; updatedAt: string; failureCode?: string;
}
export interface SafeDestination {
  /** Opaque ID minted by the native sandbox, not a user-controlled path. */
  directoryId: string; displayName: string;
}
export interface DownloadEngine {
  analyze(url: string, signal?: AbortSignal): Promise<SourceAnalysis>;
  enqueue(request: DownloadRequest): Promise<TaskId>;
  pause(id: TaskId): Promise<void>; resume(id: TaskId): Promise<void>;
  cancel(id: TaskId): Promise<void>;
  getProgress(id: TaskId): Promise<DownloadProgress>;
}
