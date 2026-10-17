import test from "node:test";
import assert from "node:assert/strict";
import { createTask, getTask, updateTask } from "./taskStore.js";
import { queueProviderTask } from "./provider.js";

test("AI task lifecycle emits stable queued, running, and completed state", () => {
  const task = createTask("motion-transfer");
  assert.equal(task.status, "queued");
  assert.equal(task.progress, 0);
  updateTask(task.id, { status: "running", progress: 42, message: "Generating" });
  assert.equal(getTask(task.id)?.progress, 42);
  updateTask(task.id, { status: "completed", progress: 100, message: "Ready", resultPath: "/tmp/example.mp4" }, "completed");
  assert.equal(getTask(task.id)?.status, "completed");
  assert.equal(getTask(task.id)?.progress, 100);
});

test("AI tasks fail closed when provider credentials are absent", async () => {
  const previousUrl = process.env.VIDGRAVITY_AI_PROVIDER_URL;
  const previousToken = process.env.VIDGRAVITY_AI_PROVIDER_TOKEN;
  delete process.env.VIDGRAVITY_AI_PROVIDER_URL;
  delete process.env.VIDGRAVITY_AI_PROVIDER_TOKEN;
  try {
    await assert.rejects(() => queueProviderTask({ kind: "inpaint", files: {}, metadata: {} }), /AI generation is not configured/);
  } finally {
    if (previousUrl === undefined) delete process.env.VIDGRAVITY_AI_PROVIDER_URL; else process.env.VIDGRAVITY_AI_PROVIDER_URL = previousUrl;
    if (previousToken === undefined) delete process.env.VIDGRAVITY_AI_PROVIDER_TOKEN; else process.env.VIDGRAVITY_AI_PROVIDER_TOKEN = previousToken;
  }
});
