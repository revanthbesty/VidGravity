import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { Express } from "express";
import { createTask, updateTask, type AITask, type TaskKind } from "./taskStore.js";

interface ProviderCreated { id?: string; taskId?: string; }
interface ProviderStatus { status?: string; progress?: number; message?: string; error?: string; resultUrl?: string; outputUrl?: string; }
export interface TaskInput { kind: TaskKind; files: Record<string, Express.Multer.File>; metadata: Record<string, unknown>; }

function providerConfig(): { base: URL; token: string } {
  const raw = process.env.VIDGRAVITY_AI_PROVIDER_URL;
  const token = process.env.VIDGRAVITY_AI_PROVIDER_TOKEN;
  if (!raw || !token) throw new Error("AI generation is not configured. Set VIDGRAVITY_AI_PROVIDER_URL and VIDGRAVITY_AI_PROVIDER_TOKEN to a compatible model gateway.");
  const base = new URL(raw);
  if (base.protocol !== "https:" && base.hostname !== "localhost") throw new Error("AI provider URL must use HTTPS.");
  return { base, token };
}
async function providerRequest<T>(url: URL, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, headers: { Authorization: `Bearer ${token}`, ...(init?.headers ?? {}) }, signal: AbortSignal.timeout(60_000) });
  if (!response.ok) throw new Error(`AI provider returned HTTP ${response.status}: ${(await response.text()).slice(0, 240)}`);
  return response.json() as Promise<T>;
}
export async function queueProviderTask(input: TaskInput): Promise<AITask> {
  const { base, token } = providerConfig();
  const task = createTask(input.kind, "Preparing secure upload to the configured AI provider");
  void runProviderTask(task, input, base, token);
  return task;
}
async function runProviderTask(task: AITask, input: TaskInput, base: URL, token: string): Promise<void> {
  try {
    updateTask(task.id, { status: "running", progress: 3, message: "Uploading source media to the model provider" });
    const form = new FormData();
    form.set("kind", input.kind); form.set("metadata", JSON.stringify(input.metadata));
    for (const [field, file] of Object.entries(input.files)) {
      form.set(field, new Blob([new Uint8Array(file.buffer)], { type: file.mimetype }), file.originalname.replace(/[^a-z0-9._-]/gi, "_").slice(0, 100));
    }
    const created = await providerRequest<ProviderCreated>(new URL("/v1/tasks", base), token, { method: "POST", body: form });
    const providerId = created.id ?? created.taskId;
    if (!providerId || !/^[a-zA-Z0-9_-]{1,200}$/.test(providerId)) throw new Error("AI provider response did not contain a valid task ID.");
    let finished: ProviderStatus | undefined;
    const deadline = Date.now() + 30 * 60 * 1000;
    while (Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 2_000));
      const status = await providerRequest<ProviderStatus>(new URL(`/v1/tasks/${encodeURIComponent(providerId)}`, base), token);
      const progress = Math.max(4, Math.min(96, Math.round(Number(status.progress ?? 0))));
      updateTask(task.id, { progress, message: status.message?.slice(0, 240) || "AI model is generating frames" });
      if (["failed", "error", "cancelled"].includes((status.status ?? "").toLowerCase())) throw new Error(status.error?.slice(0, 300) || "AI provider reported that generation failed.");
      if (["completed", "succeeded", "success"].includes((status.status ?? "").toLowerCase())) { finished = status; break; }
    }
    if (!finished) throw new Error("AI provider timed out after 30 minutes.");
    const rawResult = finished.resultUrl ?? finished.outputUrl;
    if (!rawResult) throw new Error("AI provider finished without a result URL.");
    const resultUrl = new URL(rawResult, base);
    if (resultUrl.origin !== base.origin || resultUrl.protocol !== "https:" && resultUrl.hostname !== "localhost") throw new Error("AI provider result URL was outside the configured provider origin.");
    updateTask(task.id, { progress: 98, message: "Downloading generated video" });
    const result = await fetch(resultUrl, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(10 * 60_000) });
    if (!result.ok) throw new Error(`Could not download provider result (HTTP ${result.status}).`);
    const type = result.headers.get("content-type") ?? "";
    if (!type.startsWith("video/") && type !== "application/octet-stream") throw new Error("AI provider result is not a video file.");
    const bytes = new Uint8Array(await result.arrayBuffer());
    if (!bytes.length || bytes.length > 1024 * 1024 * 1024) throw new Error("AI provider result is empty or exceeds the 1 GiB limit.");
    const directory = await mkdtemp(join(tmpdir(), "vidgravity-ai-"));
    const resultPath = join(directory, `${task.id}.mp4`);
    await writeFile(resultPath, bytes, { mode: 0o600 });
    updateTask(task.id, { status: "completed", progress: 100, message: "Video generation complete", resultPath }, "completed");
  } catch (error) {
    updateTask(task.id, { status: "failed", progress: 100, error: error instanceof Error ? error.message : "AI provider task failed.", message: "Generation failed" }, "failed");
  }
}
