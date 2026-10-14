import { EventEmitter } from "node:events";
import { randomUUID } from "node:crypto";
import type { Response } from "express";

export type TaskKind = "inpaint" | "face-swap" | "motion-transfer";
export type TaskStatus = "queued" | "running" | "completed" | "failed";
export interface AITask {
  id: string;
  kind: TaskKind;
  status: TaskStatus;
  progress: number;
  message: string;
  createdAt: number;
  updatedAt: number;
  error?: string;
  resultPath?: string;
}
const tasks = new Map<string, AITask>();
const events = new Map<string, EventEmitter>();
const TTL_MS = 2 * 60 * 60 * 1000;
function emitter(id: string): EventEmitter {
  let value = events.get(id);
  if (!value) { value = new EventEmitter(); value.setMaxListeners(100); events.set(id, value); }
  return value;
}
function publicTask(task: AITask) {
  const { resultPath: _privatePath, ...safe } = task;
  return safe;
}
export function createTask(kind: TaskKind, message = "Queued for processing"): AITask {
  const now = Date.now();
  const task: AITask = { id: randomUUID(), kind, status: "queued", progress: 0, message, createdAt: now, updatedAt: now };
  tasks.set(task.id, task); emitter(task.id);
  return task;
}
export function getTask(id: string): AITask | undefined { return tasks.get(id); }
export function updateTask(id: string, patch: Partial<AITask>, event = "progress"): void {
  const task = tasks.get(id); if (!task) return;
  Object.assign(task, patch, { updatedAt: Date.now() });
  emitter(id).emit(event, publicTask(task));
}
export function subscribeTask(id: string, response: Response): () => void {
  const task = tasks.get(id); if (!task) throw new Error("TASK_NOT_FOUND");
  const bus = emitter(id);
  const write = (event: string, data: unknown) => response.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  write("snapshot", publicTask(task));
  if (task.status === "completed") write("completed", publicTask(task));
  if (task.status === "failed") write("failed", publicTask(task));
  const onProgress = (data: unknown) => write("progress", data);
  const onCompleted = (data: unknown) => write("completed", data);
  const onFailed = (data: unknown) => write("failed", data);
  bus.on("progress", onProgress); bus.on("completed", onCompleted); bus.on("failed", onFailed);
  const heartbeat = setInterval(() => { if (!response.writableEnded) response.write(": keep-alive\n\n"); }, 15_000);
  return () => { clearInterval(heartbeat); bus.off("progress", onProgress); bus.off("completed", onCompleted); bus.off("failed", onFailed); };
}
setInterval(() => {
  const cutoff = Date.now() - TTL_MS;
  for (const [id, task] of tasks) if (task.updatedAt < cutoff) { tasks.delete(id); events.get(id)?.removeAllListeners(); events.delete(id); }
}, 10 * 60 * 1000).unref();
