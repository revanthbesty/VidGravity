import {discordVideoBitrateKbps,type DiscordExportOptions} from "../../../packages/contracts/src/studio";
export interface ArgvPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
export interface DiscordTwoPassPlan {pass1:ArgvPlan;pass2:ArgvPlan;targetVideoBitrateKbps:number;maxBytes:number;}
function validatePaths(inputPath:string,outputPath:string,passlogPath:string):void {
 if(!inputPath||!outputPath||inputPath===outputPath||!passlogPath||passlogPath===inputPath||passlogPath===outputPath)throw new Error("INVALID_DISCORD_PATH");
}
/** Produces separate, shell-free FFmpeg argv plans. Pass 1 writes analysis only; pass 2 writes the derivative. */
export function buildDiscordTwoPassPlans(inputPath:string,outputPath:string,o:DiscordExportOptions,passlogPath:string):DiscordTwoPassPlan {
 validatePaths(inputPath,outputPath,passlogPath);
 const bitrate=discordVideoBitrateKbps(o);
 const videoCodec=o.videoCodec==="av1"?["-c:v","libsvtav1","-preset","6"]:["-c:v","libx264","-preset","medium"];
 const common=["-nostdin","-hide_banner","-y","-i",inputPath,...videoCodec,"-b:v",`${bitrate}k`,"-passlogfile",passlogPath];
 const pass1:ArgvPlan={executable:"ffmpeg",outputPath:"NUL",args:[...common,"-pass","1","-an","-f","null","NUL"]};
 const pass2:ArgvPlan={executable:"ffmpeg",outputPath,args:[...common,"-pass","2","-c:a","aac","-b:a",`${o.audioBitrateKbps}k`,"-movflags","+faststart",outputPath]};
 return {pass1,pass2,targetVideoBitrateKbps:bitrate,maxBytes:o.maxBytes};
}
/** Compatibility wrapper: returns only the final pass; new callers should run both plans in order. */
export function buildDiscordCompressionPlan(inputPath:string,outputPath:string,o:DiscordExportOptions,passlogPath:string):ArgvPlan {
 return buildDiscordTwoPassPlans(inputPath,outputPath,o,passlogPath).pass2;
}
/** Output-size verification is mandatory; bitrate estimates cannot guarantee a hard byte cap. */
export function assertDiscordSize(bytes:number,maxBytes=8_000_000):void {if(!Number.isSafeInteger(bytes)||bytes<1||bytes>maxBytes)throw new Error("DISCORD_OUTPUT_EXCEEDS_8MB");}
