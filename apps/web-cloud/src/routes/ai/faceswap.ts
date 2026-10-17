import { Router, type Request } from "express";
import multer from "multer";
import { queueProviderTask } from "../../lib/provider.js";

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 200 * 1024 * 1024, files: 2, fields: 8 } });
export const faceSwapRouter = Router();
faceSwapRouter.post("/", upload.fields([{ name: "video", maxCount: 1 }, { name: "reference", maxCount: 1 }]), async (req: Request, res, next) => {
  try {
    const files = (req.files ?? {}) as Record<string, Express.Multer.File[]>;
    const video = files.video?.[0], reference = files.reference?.[0];
    if (!video || !reference) { res.status(400).json({ error: "Face-Off requires target video and reference image." }); return; }
    if (req.body.consentConfirmed !== "true") { res.status(400).json({ error: "Explicit permission confirmation is required before face-swap processing." }); return; }
    if (!video.mimetype.startsWith("video/") || !reference.mimetype.startsWith("image/")) { res.status(415).json({ error: "Expected a video and a reference image." }); return; }
    let timeline: unknown;
    try { timeline = JSON.parse(String(req.body.timeline ?? "{}")); } catch { res.status(400).json({ error: "Invalid face timeline JSON." }); return; }
    if (!timeline || typeof timeline !== "object" || !Array.isArray((timeline as { boxes?: unknown }).boxes) || (timeline as { boxes: unknown[] }).boxes.length > 20_000) { res.status(400).json({ error: "Face timeline is missing or too large." }); return; }
    const task = await queueProviderTask({ kind: "face-swap", files: { video, reference }, metadata: { timeline, requestedOperation: "identity-swap-with-temporal-blending" } });
    res.status(202).json({ task, eventsUrl: `/api/ai/tasks/${task.id}/events` });
  } catch (error) { next(error); }
});
