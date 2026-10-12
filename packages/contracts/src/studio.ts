/** Shared, validated creator-pipeline contracts. Original assets are never output targets. */
export type MediaId = string & { readonly __mediaId: unique symbol };
export type DerivativeKind = "frame" | "captioned" | "sticker" | "status-clip" | "discord" | "reaction" | "photo-dump" | "privacy-redacted" | "wallpaper" | "study-recap" | "audio-stem";
export interface MediaAsset { id: MediaId; uri: string; mimeType: string; durationMs?: number; width?: number; height?: number; sha256?: string; immutable: true; }
export interface DerivativeRequest { sourceId: MediaId; kind: DerivativeKind; outputId: MediaId; args: Readonly<Record<string, string | number | boolean>>; }
export interface MediaVersionGraph { source: MediaId; derivatives: readonly { id: MediaId; kind: DerivativeKind; parent: MediaId; createdAt: string }[]; }
export type PipelineState = "queued" | "running" | "committing" | "succeeded" | "failed" | "cancelled";
export interface PipelineTask { id: string; state: PipelineState; progress: number; sourceId: MediaId; outputId?: MediaId; errorCode?: string; }
export interface CaptionLayer { id: string; text: string; x: number; y: number; scale: number; rotationDeg: number; color: string; style: "classic" | "heavy" | "neon" | "typewriter" | "comic" | "pill"; }
export interface FrameCaptureRequest { sourceId: MediaId; timestampMs: number; outputId: MediaId; }
export interface StickerExportOptions { startMs: number; durationMs: 3000; width: 512; height: 512; maxBytes: 500_000; fps: 15; }
export interface DiscordExportOptions { durationSeconds: number; audioBitrateKbps: number; maxBytes: 8_000_000; videoCodec: "av1" | "h264"; }
export interface StudyFlashcard { front: string; back: string; timestamp: string; }
export interface StudySheetMarker { timestampMs: number; quote: string; frameDerivativeId: MediaId; }
export function assertDerivative(request: DerivativeRequest): void {
  if (!request.sourceId || !request.outputId || request.sourceId === request.outputId) throw new Error("DERIVATIVE_MUST_NOT_OVERWRITE_SOURCE");
  if (!Number.isFinite(request.args.timestampMs as number ?? 0)) throw new Error("INVALID_PIPELINE_ARGUMENT");
}
export function validateStickerOptions(o: StickerExportOptions): void {
  if (!Number.isFinite(o.startMs) || o.startMs < 0 || o.durationMs !== 3000 || o.width !== 512 || o.height !== 512 || o.maxBytes !== 500_000 || o.fps !== 15) throw new Error("INVALID_STICKER_SPEC");
}
export function discordVideoBitrateKbps(o: DiscordExportOptions): number {
  if (!(o.durationSeconds > 0) || !(o.audioBitrateKbps >= 0) || !Number.isFinite(o.durationSeconds + o.audioBitrateKbps)) throw new Error("INVALID_DISCORD_OPTIONS");
  return Math.max(64, Math.floor((o.maxBytes * 8 / o.durationSeconds / 1000) - o.audioBitrateKbps - 32));
}
