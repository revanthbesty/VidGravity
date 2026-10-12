import {discordVideoBitrateKbps,type DiscordExportOptions} from "../../../packages/contracts/src/studio";
export interface ArgvPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
export function buildDiscordCompressionPlan(inputPath:string,outputPath:string,o:DiscordExportOptions,passlogPath:string):ArgvPlan {
 if(!inputPath||!outputPath||inputPath===outputPath||!passlogPath||passlogPath===inputPath)throw new Error("INVALID_DISCORD_PATH");
 const bitrate=discordVideoBitrateKbps(o);
 const codec=o.videoCodec==="av1"?["-c:v","libsvtav1","-b:v",`${bitrate}k`,"-preset","6"]:["-c:v","libx264","-b:v",`${bitrate}k`,"-pass","1","-passlogfile",passlogPath];
 const args=["-nostdin","-hide_banner","-i",inputPath,...codec,"-c:a","aac","-b:a",`${o.audioBitrateKbps}k`,"-movflags","+faststart","-y",outputPath];
 return {executable:"ffmpeg",args,outputPath};
}
/** Output-size verification is mandatory; bitrate estimates cannot guarantee a hard byte cap. */
export function assertDiscordSize(bytes:number,maxBytes=8_000_000):void {if(!Number.isSafeInteger(bytes)||bytes<1||bytes>maxBytes)throw new Error("DISCORD_OUTPUT_EXCEEDS_8MB");}
