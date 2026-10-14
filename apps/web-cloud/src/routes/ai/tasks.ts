import { Router } from "express";
import { createReadStream } from "node:fs";
import { getTask, subscribeTask } from "../../lib/taskStore.js";
export const taskRouter = Router();
taskRouter.get("/:id/events", (req, res) => {
  const task = getTask(req.params.id);
  if (!task) { res.status(404).json({ error: "AI task not found or expired." }); return; }
  res.status(200).set({ "Content-Type": "text/event-stream", "Cache-Control": "no-cache, no-transform", Connection: "keep-alive", "X-Accel-Buffering": "no" });
  res.flushHeaders();
  const unsubscribe = subscribeTask(task.id, res);
  req.on("close", unsubscribe);
});
taskRouter.get("/:id/result", (req, res) => {
  const task = getTask(req.params.id);
  if (!task) { res.status(404).json({ error: "AI task not found or expired." }); return; }
  if (task.status !== "completed" || !task.resultPath) { res.status(409).json({ error: "The generated video is not ready yet." }); return; }
  res.type("video/mp4").set("Content-Disposition", `attachment; filename="vidgravity-${task.kind}.mp4"`);
  createReadStream(task.resultPath).on("error", () => { if (!res.headersSent) res.status(500).end("Could not read generated result."); }).pipe(res);
});
