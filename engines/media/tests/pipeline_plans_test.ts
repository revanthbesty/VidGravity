import { buildPhotoDumpPlan } from "../src/photoDump.ts";
import { buildPrivacyBlurPlan } from "../src/privacyBlur.ts";
import { buildDiscordTwoPassPlans, assertDiscordSize } from "../src/discordCompressor.ts";
import { buildWallpaperPlan } from "../src/wallpaperExporter.ts";
import { buildStatusSplitPlan } from "../src/statusSplitter.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
function assertThrows(run: () => unknown, expected: string): void {
  try { run(); } catch (error) {
    assert(error instanceof Error && error.message === expected, `Expected ${expected}; got ${String(error)}`);
    return;
  }
  throw new Error(`Expected ${expected} to be thrown`);
}

Deno.test("WhatsApp Status splitting defaults to 90 seconds and supports platform overrides", () => {
  const whatsapp = buildStatusSplitPlan("source.mp4", "status-%03d.mp4", 150);
  assert(whatsapp.args.includes("90"), "WhatsApp Status segments should use the current 90-second cap");
  assert(whatsapp.args[whatsapp.args.indexOf("-segment_time") + 1] === "90", "default segment duration must be 90 seconds");
  const legacyPlatform = buildStatusSplitPlan("source.mp4", "clip-%03d.mp4", 75, 30);
  assert(legacyPlatform.args[legacyPlatform.args.indexOf("-segment_time") + 1] === "30", "platform-specific duration overrides must be honored");
  assertThrows(() => buildStatusSplitPlan("source.mp4", "status-%03d.mp4", 80), "INVALID_STATUS_SPLIT");
});

Deno.test("photo dump builds a vertical derivative with one segment per photo", () => {
  const plan = buildPhotoDumpPlan({
    photos: ["1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg"],
    audioPath: "track.mp3",
    outputPath: "render.mp4",
    beatTimesSeconds: [2, 4, 6, 8],
    durationSeconds: 10,
  });
  assert(plan.executable === "ffmpeg", "must use FFmpeg");
  assert(plan.args.filter((arg) => arg === "-loop").length === 5, "each photo must be looped");
  assert(plan.args.join(" ").includes("scale=1080:1920:force_original_aspect_ratio=increase"), "must render vertical video");
  assert(plan.args.includes("5:a:0"), "must map audio after five image inputs");
  assert(plan.outputPath === "render.mp4", "must preserve requested derivative path");
});
Deno.test("photo dump rejects overlapping beats and source/output collisions", () => {
  assertThrows(() => buildPhotoDumpPlan({
    photos: ["1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg"],
    audioPath: "track.mp3", outputPath: "render.mp4",
    beatTimesSeconds: [2, 2, 6, 8], durationSeconds: 10,
  }), "INVALID_PHOTO_DUMP");
  assertThrows(() => buildPhotoDumpPlan({
    photos: ["1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg"],
    audioPath: "track.mp3", outputPath: "1.jpg",
    beatTimesSeconds: [2, 4, 6, 8], durationSeconds: 10,
  }), "INVALID_PHOTO_DUMP");
});
Deno.test("privacy blur accepts only in-frame regions and emits audio-preserving filter plan", () => {
  const plan = buildPrivacyBlurPlan("source.mp4", "redacted.mp4", [
    { x: 20, y: 30, width: 100, height: 80, kind: "face" },
    { x: -20, y: 0, width: 20, height: 20, kind: "license-plate" },
  ], 640, 480);
  assert(plan.args.includes("-map") && plan.args.includes("0:a?"), "audio should be preserved when present");
  assert(plan.args.join(" ").includes("boxblur=10:2:5:2"), "region should be blurred");
  assertThrows(() => buildPrivacyBlurPlan("source.mp4", "out.mp4", [
    { x: -10, y: 0, width: 30, height: 30, kind: "face" },
  ], 640, 480), "NO_VALID_PRIVACY_REGIONS");
  assertThrows(() => buildPrivacyBlurPlan("same.mp4", "same.mp4", [
    { x: 0, y: 0, width: 30, height: 30, kind: "face" },
  ], 640, 480), "INVALID_PRIVACY_BLUR");
});
Deno.test("Discord compression exposes ordered two-pass plans and enforces actual output size", () => {
  const plan = buildDiscordTwoPassPlans("source.mp4", "discord.mp4", {
    durationSeconds: 20, audioBitrateKbps: 96, maxBytes: 8_000_000, videoCodec: "h264",
  }, "passlog");
  assert(plan.pass1.args.includes("1") && plan.pass2.args.includes("2"), "must expose both passes");
  assert(plan.pass1.args.includes("-an"), "analysis pass must not encode audio");
  assert(plan.pass2.outputPath === "discord.mp4", "second pass writes derivative output");
  assert(plan.maxBytes === 8_000_000, "size cap must be surfaced for post-render validation");
  assertThrows(() => assertDiscordSize(8_000_001), "DISCORD_OUTPUT_EXCEEDS_8MB");
  assertThrows(() => assertDiscordSize(0), "DISCORD_OUTPUT_EXCEEDS_8MB");
  assertDiscordSize(8_000_000);
});
Deno.test("wallpaper export rejects invalid duration and creates a silent looping derivative", () => {
  const plan = buildWallpaperPlan("source.mp4", "wallpaper.mp4", 15);
  assert(plan.args.includes("-an"), "wallpaper derivative should be silent");
  assert(plan.args.includes("30.000"), "output duration should be twice the input duration");
  assert(plan.args.join(" ").includes("reverse"), "loop should reverse the clip");
  assertThrows(() => buildWallpaperPlan("source.mp4", "wallpaper.mp4", 61), "INVALID_WALLPAPER_REQUEST");
  assertThrows(() => buildWallpaperPlan("source.mp4", "source.mp4", 10), "INVALID_WALLPAPER_REQUEST");
});
