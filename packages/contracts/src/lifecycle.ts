import type { DownloadState } from "./downloader";

const transitions: Record<DownloadState, readonly DownloadState[]> = {
  QUEUED: ["WAITING_FOR_WINDOW","ANALYZING","CANCELLED","FAILED"],
  WAITING_FOR_WINDOW: ["ANALYZING","CANCELLED","FAILED"],
  ANALYZING: ["READY","FAILED","CANCELLED"],
  READY: ["DOWNLOADING","CANCELLED","FAILED"],
  DOWNLOADING: ["PAUSED","VERIFYING","CANCELLED","FAILED"],
  PAUSED: ["DOWNLOADING","CANCELLED","FAILED"],
  VERIFYING: ["MERGING","COMMITTING","FAILED","CANCELLED"],
  MERGING: ["COMMITTING","FAILED","CANCELLED"],
  COMMITTING: ["COMPLETED","FAILED"],
  COMPLETED: [], FAILED: [], CANCELLED: []
};
export function canTransition(from: DownloadState, to: DownloadState): boolean {
  return transitions[from].includes(to);
}
export function assertTransition(from: DownloadState, to: DownloadState): void {
  if (!canTransition(from, to)) throw new Error(`Invalid download state transition: ${from} -> ${to}`);
}
/** COMPLETED is only legal after checksum verification and atomic final rename. */
export function assertCompletionGate(input: { checksumVerified: boolean; atomicRenameSucceeded: boolean }): void {
  if (!input.checksumVerified || !input.atomicRenameSucceeded) {
    throw new Error("Completion requires verified integrity and successful atomic commit");
  }
}
