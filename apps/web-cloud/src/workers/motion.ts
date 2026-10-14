import { Router, type Request } from "express";
import multer from "multer";
import { queueProviderTask } from "../lib/provider.js";

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 200 * 1024 * 1024, files: 2, fields: 8 } });
export const motionRouter = Router();
motionRouter.post("/", upload.fields([{ name: "character", maxCount: 1 }, { name: "choreography", maxCount: 1 }]), async (req: Request, res, next) => {
  try {
    const files = (req.files ?? {}) as Record<string, Express.Multer.File[]>;
    const character = files.character?.[0], choreography = files.choreography?.[0];
    if (!character || !choreography) { res.status(400).json({ error: "Motion Transfer requires a character image and choreography video." }); return; }
    if (!character.mimetype.startsWith("image/") || !choreography.mimetype.startsWith("video/")) { res.status(415).json({ error: "Expected a character image and choreography video." }); return; }
    let options: Record<string, unknown>;
    try { options = JSON.parse(String(req.body.options ?? "{}")) as Record<string, unknown>; } catch { res.status(400).json({ error: "Invalid motion options JSON." }); return; }
    const task = await queueProviderTask({ kind: "motion-transfer", files: { character, choreography }, metadata: { ...options, requestedOperation: "full-body-motion-transfer", outputFormat: "mp4" } });
    res.status(202).json({ task, eventsUrl: `/api/ai/tasks/${task.id}/events` });
  } catch (error) { next(error); }
});
