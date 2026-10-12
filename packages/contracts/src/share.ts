import type { MediaContainer } from "./downloader";
export type ClipAspectRatio = "9:16" | "4:5" | "1:1" | "16:9";
export type ClipFit = "cover" | "contain" | "blurred-backdrop";
export type ClipExportMode = "original-format" | "platform-optimized";
export interface ClipRange { startSeconds: number; endSeconds: number; }
export type ClipFilter =
 | { op:"trim"; startSeconds:number; endSeconds:number }
 | { op:"scale-crop"; width:number; height:number; fit:ClipFit; algorithm:"lanczos"|"bicubic" }
 | { op:"encode-video"; codec:"h264"|"hevc"|"vp9"; crf:number }
 | { op:"encode-audio"; codec:"aac"|"opus"; bitrateKbps:number }
 | { op:"mux"; container:MediaContainer };
export interface ClipExportRequest {
  mediaId:string; range:ClipRange; aspectRatio:ClipAspectRatio; outputContainer:MediaContainer; exportMode?:ClipExportMode;
  operations:ClipFilter[]; outputDirectoryId:string; includeThumbnail:boolean; includeMetadata:boolean; preserveSourceContainer?:boolean;
  /** Output is a derivative; original media must remain immutable. */
  parentMediaId?: string; requestedOutputName?: string;
}
export interface SharePreviewMetadata { title:string; durationSeconds:number; thumbnailUrl?:string; mediaUrl?:string; mimeType:string; }
export function validateClipRequest(request: ClipExportRequest): void {
  if (!Number.isFinite(request.range.startSeconds) || !Number.isFinite(request.range.endSeconds) ||
      request.range.startSeconds < 0 || request.range.endSeconds <= request.range.startSeconds) throw new Error("Invalid clip time range.");
  if (!request.outputDirectoryId.trim()) throw new Error("A sandbox destination is required.");
  if (request.operations.length === 0 || request.operations.some(op=>typeof op !== "object" || !("op" in op))) throw new Error("A validated clip operation graph is required.");
  for (const op of request.operations) {
    if (op.op === "encode-video" && (!Number.isInteger(op.crf) || op.crf < 0 || op.crf > 51)) throw new Error("Video CRF must be between 0 and 51.");
    if (op.op === "encode-audio" && (!Number.isFinite(op.bitrateKbps) || op.bitrateKbps < 32 || op.bitrateKbps > 512)) throw new Error("Audio bitrate must be between 32 and 512 kbps.");
    if (op.op === "scale-crop" && (![op.width,op.height].every(Number.isInteger) || op.width < 2 || op.height < 2 || op.width > 7680 || op.height > 4320)) throw new Error("Scale dimensions must be valid even-sized video dimensions.");
    if (op.op === "trim" && (!Number.isFinite(op.startSeconds) || !Number.isFinite(op.endSeconds) || op.startSeconds < 0 || op.endSeconds <= op.startSeconds)) throw new Error("Trim operation has an invalid time range.");
  }
  if (request.exportMode === "original-format") {
    if (request.preserveSourceContainer !== true || request.operations.some(op => op.op !== "trim")) throw new Error("Original-format exports must preserve source streams and cannot include scaling or re-encoding.");
  }
  if (request.exportMode === "platform-optimized") {
    if (request.preserveSourceContainer === true || !request.operations.some(op => op.op === "scale-crop") || !request.operations.some(op => op.op === "encode-video") || !request.operations.some(op => op.op === "encode-audio") || !request.operations.some(op => op.op === "mux")) throw new Error("Platform-optimized exports require scaling, video/audio encoding, and muxing.");
  }
}
