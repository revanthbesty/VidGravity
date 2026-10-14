import { Router, type Request } from "express";
import multer from "multer";
import { queueProviderTask } from "../../lib/provider.js";

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 200 * 1024 * 1024, files: 3, fields: 8 } });
export const inpaintRouter = Router();
inpaintRouter.post("/", upload.fields([{ name: "video", maxCount: 1 }, { name: "mask", maxCount: 1 }, { name: "frame", maxCount: 1 }]), async (req: Request, res, next) => {
  try {
    const files = (req.files ?? {}) as Record<string, Express.Multer.File[]>;
    const video = files.video?.[0], mask = files.mask?.[0], frame = files.frame?.[0];
    if (!video || !mask || !frame) { res.status(400).json({ error: "Magic Eraser requires video, mask, and extracted frame uploads." }); return; }
    if (!video.mimetype.startsWith("video/") || mask.mimetype !== "image/png" || frame.mimetype !== "image/png") { res.status(415).json({ error: "Expected a video, PNG mask, and PNG frame." }); return; }
    let timeline: Record<string, unknown>;
    try { timeline = JSON.parse(String(req.body.timeline ?? "{}")) as Record<string, unknown>; } catch { res.status(400).json({ error: "Invalid timeline JSON." }); return; }
    if (!Number.isFinite(Number(timeline.maskFrameSeconds)) || Number(timeline.maskFrameSeconds) < 0) { res.status(400).json({ error: "Mask frame time must be a non-negative number." }); return; }
    const task = await queueProviderTask({ kind: "inpaint", files: { video, mask, frame }, metadata: { ...timeline, requestedOperation: "temporal-object-removal" } });
    res.status(202).json({ task, eventsUrl: `/api/ai/tasks/${task.id}/events` });
  } catch (error) { next(error); }
});
