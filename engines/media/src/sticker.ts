import { validateStickerOptions, type StickerExportOptions } from "../../../packages/contracts/src/studio";
export interface StickerPlan { executable: "ffmpeg"; args: readonly string[]; outputPath: string; }
export function buildStickerPlan(inputPath: string, outputPath: string, o: StickerExportOptions): StickerPlan {
  validateStickerOptions(o);
  if (!inputPath || !outputPath || inputPath === outputPath) throw new Error("INVALID_OUTPUT_PATH");
  return { executable: "ffmpeg", outputPath, args: ["-nostdin","-hide_banner","-ss",(o.startMs/1000).toFixed(3),"-i",inputPath,"-t","3","-vf","fps=15,scale=512:512:force_original_aspect_ratio=decrease,format=rgba,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=#00000000","-vcodec","libwebp","-loop","0","-an","-vsync","0","-fs","500000","-y",outputPath] };
}
export async function assertStickerOutput(outputPath: string, inspect: (path: string)=>Promise<{width:number;height:number;bytes:number;codec:string}>): Promise<void> {
 const m=await inspect(outputPath); if(m.width!==512||m.height!==512||m.bytes>500_000||m.codec.toLowerCase()!=="webp") throw new Error("STICKER_OUTPUT_CONSTRAINT_FAILED");
}
