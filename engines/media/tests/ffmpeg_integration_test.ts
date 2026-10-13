import { buildPhotoDumpPlan } from "../src/photoDump.ts";
import { buildPrivacyBlurPlan } from "../src/privacyBlur.ts";
import { buildWallpaperPlan } from "../src/wallpaperExporter.ts";
import { buildStatusSplitPlan } from "../src/statusSplitter.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
async function run(executable: string, args: string[]): Promise<string> {
  const result = await new Deno.Command(executable, { args, stdout: "piped", stderr: "piped" }).output();
  if (!result.success) {
    throw new Error(`${executable} exited ${result.code}: ${new TextDecoder().decode(result.stderr)}`);
  }
  return new TextDecoder().decode(result.stdout);
}
async function metadata(path: string): Promise<{streams:{codec_type:string;width?:number;height?:number}[];format:{duration:string;size:string}}> {
  return JSON.parse(await run("ffprobe", ["-v","error","-show_entries","stream=codec_type,width,height","-show_entries","format=duration,size","-of","json",path]));
}
async function existsNonempty(path: string): Promise<boolean> {
  try { return (await Deno.stat(path)).isFile && (await Deno.stat(path)).size > 0; } catch { return false; }
}

Deno.test("real FFmpeg splits long status media at the requested cap and preserves audio", async () => {
  const dir = await Deno.makeTempDir({ prefix: "vidgravity-status-split-" });
  try {
    const source = `${dir}/source.mp4`;
    const pattern = `${dir}/status-%03d.mp4`;
    await run("ffmpeg", ["-nostdin","-hide_banner","-loglevel","error","-f","lavfi","-i","testsrc2=size=160x120:rate=12","-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","4.2","-c:v","libx264","-pix_fmt","yuv420p","-c:a","aac","-y",source]);
    const plan = buildStatusSplitPlan(source, pattern, 4.2, 2);
    await run(plan.executable, plan.args as string[]);
    const names: string[] = [];
    for await (const entry of Deno.readDir(dir)) if (/^status-\d{3}\.mp4$/.test(entry.name)) names.push(entry.name);
    names.sort();
    assert(names.length === 3, `expected three status segments at a two-second cap, got ${names.length}`);
    for (const name of names) {
      const info = await metadata(`${dir}/${name}`);
      const duration = Number(info.format.duration);
      assert(duration > 0 && duration <= 2.3, `segment duration ${duration} must stay close to the requested two-second cap`);
      assert(info.streams.some(stream => stream.codec_type === "video"), "each segment must contain video");
      assert(info.streams.some(stream => stream.codec_type === "audio"), "each segment must preserve audio");
    }
  } finally {
    await Deno.remove(dir, { recursive: true });
  }
});

Deno.test("real FFmpeg renders privacy blur and wallpaper derivatives without modifying source", async () => {
  const dir = await Deno.makeTempDir({ prefix: "vidgravity-media-" });
  try {
    const source = `${dir}/source.mp4`;
    const blurred = `${dir}/redacted.mp4`;
    const wallpaper = `${dir}/wallpaper.mp4`;
    await run("ffmpeg", ["-nostdin","-hide_banner","-loglevel","error","-f","lavfi","-i","testsrc2=size=160x120:rate=12","-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","2","-c:v","libx264","-pix_fmt","yuv420p","-c:a","aac","-y",source]);
    const originalBytes = (await Deno.readFile(source)).length;
    const blurPlan = buildPrivacyBlurPlan(source, blurred, [
      { x: 20, y: 20, width: 40, height: 30, kind: "face" },
      { x: 90, y: 40, width: 30, height: 25, kind: "license-plate" },
    ], 160, 120);
    await run(blurPlan.executable, blurPlan.args as string[]);
    assert(await existsNonempty(blurred), "privacy blur should produce a non-empty file");
    const blurMeta = await metadata(blurred);
    assert(blurMeta.streams.some(s => s.codec_type === "video"), "blur output must contain video");
    assert(blurMeta.streams.some(s => s.codec_type === "audio"), "blur output must preserve audio");
    const wallpaperPlan = buildWallpaperPlan(source, wallpaper, 1.5);
    await run(wallpaperPlan.executable, wallpaperPlan.args as string[]);
    assert(await existsNonempty(wallpaper), "wallpaper should produce a non-empty file");
    const wallMeta = await metadata(wallpaper);
    const video = wallMeta.streams.find(s => s.codec_type === "video");
    assert(video?.width === 1080 && video.height === 1920, "wallpaper output must be 1080x1920");
    assert(Number(wallMeta.format.duration) >= 2.8 && Number(wallMeta.format.duration) <= 3.2, "wallpaper output duration should be about 3 seconds");
    assert((await Deno.readFile(source)).length === originalBytes, "source media must remain unchanged");
  } finally {
    await Deno.remove(dir, { recursive: true });
  }
});

Deno.test("real FFmpeg photo dump renders five photos with supplied audio", async () => {
  const dir = await Deno.makeTempDir({ prefix: "vidgravity-photodump-" });
  try {
    const photos: string[] = [];
    for (let i = 0; i < 5; i++) {
      const photo = `${dir}/photo-${i}.png`;
      await run("ffmpeg", ["-nostdin","-hide_banner","-loglevel","error","-f","lavfi","-i",`color=c=${["red","green","blue","yellow","purple"][i]}:s=160x120`,"-frames:v","1","-y",photo]);
      photos.push(photo);
    }
    const audio = `${dir}/audio.wav`;
    const output = `${dir}/photo-dump.mp4`;
    await run("ffmpeg", ["-nostdin","-hide_banner","-loglevel","error","-f","lavfi","-i","sine=frequency=660:sample_rate=44100","-t","2","-y",audio]);
    const plan = buildPhotoDumpPlan({ photos, audioPath: audio, outputPath: output, beatTimesSeconds: [0.4,0.8,1.2,1.6], durationSeconds: 2 });
    await run(plan.executable, plan.args as string[]);
    assert(await existsNonempty(output), "photo dump should produce a non-empty MP4");
    const result = await metadata(output);
    const video = result.streams.find(s => s.codec_type === "video");
    assert(video?.width === 1080 && video.height === 1920, "photo dump must be 1080x1920");
    assert(result.streams.some(s => s.codec_type === "audio"), "photo dump must contain audio");
    assert(Number(result.format.duration) >= 1.8 && Number(result.format.duration) <= 2.2, "photo dump duration should be about 2 seconds");
  } finally {
    await Deno.remove(dir, { recursive: true });
  }
});
