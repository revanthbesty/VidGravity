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
  operations:ClipFilter[]; outputDirectoryId:string; includeThumbnail:boolean; includeMetadata:boolean;
  /** Output is a derivative; original media must remain immutable. */
  parentMediaId?: string; requestedOutputName?: string;
}
export interface SharePreviewMetadata { title:string; durationSeconds:number; thumbnailUrl?:string; mediaUrl?:string; mimeType:string; }
export function validateClipRequest(request: ClipExportRequest): void {
  if (!Number.isFinite(request.range.startSeconds) || !Number.isFinite(request.range.endSeconds) ||
      request.range.startSeconds < 0 || request.range.endSeconds <= request.range.startSeconds) throw new Error("Invalid clip time range.");
  if (!request.outputDirectoryId.trim()) throw new Error("A sandbox destination is required.");
  if (request.operations.length === 0 || request.operations.some(op=>typeof op !== "object" || !("op" in op))) throw new Error("A validated clip operation graph is required.");
  for (const op of request.operations) if (op.op === "encode-video" && (!Number.isInteger(op.crf) || op.crf < 0 || op.crf > 51)) throw new Error("Video CRF must be between 0 and 51.");
}
