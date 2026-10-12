import type { MediaContainer } from "./downloader";
export interface TimeRangeRequest { startSeconds: number; endSeconds: number; }
export interface ByteRange { index: number; start: number; endInclusive: number; }
export interface ChunkPlan { totalBytes: number; chunkSizeBytes: number; maxConnections: number; }
export interface MetadataInjection {
  title?: string; artist?: string; album?: string; date?: string; comment?: string;
  coverArt?: { mimeType: "image/jpeg"|"image/png"; bytesBase64: string };
}
export interface SectionDownloadRequest {
  sourceUrl: string; range: TimeRangeRequest; outputContainer: MediaContainer;
  outputDirectoryId: string; outputName: string; metadata?: MetadataInjection;
  expectedSha256?: string; sequentialPreview?: boolean;
}
export function validateTimeRange(range: TimeRangeRequest, durationSeconds?: number): void {
  if (!Number.isFinite(range.startSeconds) || !Number.isFinite(range.endSeconds) || range.startSeconds < 0 || range.endSeconds <= range.startSeconds) throw new Error("Start and end timestamps must define a positive range.");
  if (durationSeconds !== undefined && range.endSeconds > durationSeconds) throw new Error("The end timestamp exceeds the media duration.");
}
export function planByteRanges(plan: ChunkPlan): ByteRange[] {
  if (!Number.isSafeInteger(plan.totalBytes) || plan.totalBytes <= 0 || !Number.isSafeInteger(plan.chunkSizeBytes) || plan.chunkSizeBytes <= 0 || !Number.isInteger(plan.maxConnections) || plan.maxConnections < 1 || plan.maxConnections > 16) throw new Error("Invalid byte-range plan.");
  const chunks: ByteRange[] = [];
  for (let start=0,index=0; start<plan.totalBytes; start+=plan.chunkSizeBytes,index++) chunks.push({index,start,endInclusive:Math.min(plan.totalBytes-1,start+plan.chunkSizeBytes-1)});
  return chunks;
}
