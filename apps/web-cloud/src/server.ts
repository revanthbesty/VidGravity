import express from "express";
import cors from "cors";
import { inpaintRouter } from "./routes/ai/inpaint.js";
import { faceSwapRouter } from "./routes/ai/faceswap.js";
import { taskRouter } from "./routes/ai/tasks.js";
import { motionRouter } from "./workers/motion.js";

const app = express();
const port = Number(process.env.PORT ?? 8787);
const allowedOrigins = (process.env.VIDGRAVITY_PWA_ORIGINS ?? "http://localhost:5173,http://localhost:3000").split(",").map(value => value.trim()).filter(Boolean);
app.disable("x-powered-by");
app.use(cors({ origin(origin, callback) { if (!origin || allowedOrigins.includes(origin)) callback(null, true); else callback(new Error("Origin is not allowed by AI API CORS policy.")); }, credentials: true }));
app.use(express.json({ limit: "1mb" }));
app.get("/health", (_req, res) => res.json({ status: "ok", service: "vidgravity-ai", providerConfigured: Boolean(process.env.VIDGRAVITY_AI_PROVIDER_URL && process.env.VIDGRAVITY_AI_PROVIDER_TOKEN), gatewayProtected: Boolean(process.env.VIDGRAVITY_AI_GATEWAY_SECRET) }));
// In production, fail closed unless a trusted edge/API gateway injects this secret.
// Never expose this value to the browser bundle.
app.use("/api/ai", (req, res, next) => {
  const secret = process.env.VIDGRAVITY_AI_GATEWAY_SECRET;
  if (process.env.NODE_ENV === "production" && !secret) { res.status(503).json({ error: "AI API gateway authentication is not configured." }); return; }
  if (secret && req.get("x-vidgravity-ai-gateway") !== secret) { res.status(401).json({ error: "A trusted AI gateway is required." }); return; }
  next();
});
app.post("/api/ai/inpaint", inpaintRouter);
app.post("/api/ai/faceswap", faceSwapRouter);
app.post("/api/ai/motion", motionRouter);
app.use("/api/ai/tasks", taskRouter);
app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  const message = error instanceof Error ? error.message : "Unexpected AI service error.";
  const status = message.includes("File too large") ? 413 : message.includes("not configured") || message.includes("provider URL") ? 503 : 500;
  res.status(status).json({ error: message.slice(0, 300) });
});
app.listen(port, () => console.info(`VidGravity AI API listening on :${port}`));
