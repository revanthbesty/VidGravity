export type AITaskStatus = "queued" | "running" | "completed" | "failed";
export interface AITaskSnapshot {
  id: string;
  kind: "inpaint" | "face-swap" | "motion-transfer";
  status: AITaskStatus;
  progress: number;
  message: string;
  error?: string;
}
export interface AITaskHandle {
  task: AITaskSnapshot;
  events: EventSource;
}
const API_BASE = (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_VIDGRAVITY_AI_API ?? "";

export async function submitAITask(path: string, payload: FormData): Promise<AITaskHandle> {
  const response = await fetch(`${API_BASE}${path}`, { method: "POST", body: payload, credentials: "include" });
  const body: unknown = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = body && typeof body === "object" && "error" in body ? String(body.error) : `AI task request failed (${response.status})`;
    throw new Error(detail);
  }
  if (!body || typeof body !== "object" || !("task" in body) || !("eventsUrl" in body)) throw new Error("The AI service returned an invalid task response.");
  const value = body as { task: AITaskSnapshot; eventsUrl: string };
  return { task: value.task, events: new EventSource(`${API_BASE}${value.eventsUrl}`, { withCredentials: true }) };
}

export async function saveResultToOPFS(blob: Blob, fileName: string): Promise<FileSystemFileHandle | null> {
  if (!("storage" in navigator) || !("getDirectory" in (navigator.storage as StorageManager & { getDirectory?: unknown }))) return null;
  const root = await (navigator.storage as StorageManager & { getDirectory: () => Promise<FileSystemDirectoryHandle> }).getDirectory();
  const safeName = fileName.replace(/[^a-z0-9._-]/gi, "_").slice(0, 120) || "vidgravity-ai-export.mp4";
  const handle = await root.getFileHandle(safeName, { create: true });
  const writable = await handle.createWritable();
  try { await writable.write(blob); } finally { await writable.close(); }
  return handle;
}

export async function fetchTaskResult(taskId: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/ai/tasks/${encodeURIComponent(taskId)}/result`, { credentials: "include" });
  if (!response.ok) throw new Error((await response.text()).slice(0, 300) || "The generated video is not available yet.");
  return response.blob();
}

export async function offerShare(blob: Blob, fileName: string): Promise<"shared" | "downloaded" | "unsupported"> {
  const file = new File([blob], fileName, { type: blob.type || "video/mp4" });
  if (navigator.share && (!navigator.canShare || navigator.canShare({ files: [file] }))) {
    try { await navigator.share({ files: [file], title: "VidGravity AI export" }); return "shared"; }
    catch (error) { if (error instanceof DOMException && error.name === "AbortError") return "unsupported"; throw error; }
  }
  const url = URL.createObjectURL(blob);
  try { const anchor = document.createElement("a"); anchor.href = url; anchor.download = fileName; anchor.click(); return "downloaded"; }
  finally { window.setTimeout(() => URL.revokeObjectURL(url), 30_000); }
}
