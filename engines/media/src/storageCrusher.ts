export interface RecompressCandidate {path:string;ageDays:number;codec:string;isOriginal:boolean;sizeBytes:number;}
export interface ArgvPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
export function selectRecompressCandidates(items:readonly RecompressCandidate[],freeSpaceRatio:number,weeklyIdle:boolean):RecompressCandidate[] {
 if(!Number.isFinite(freeSpaceRatio)||freeSpaceRatio<0||freeSpaceRatio>1) throw new Error("INVALID_DISK_RATIO");
 if(freeSpaceRatio>=0.1&&!weeklyIdle)return [];
 return items.filter(x=>!x.isOriginal&&x.ageDays>=14&&["h264","avc"].includes(x.codec.toLowerCase())&&x.sizeBytes>0);
}
export function buildStorageRecompressPlan(inputPath:string,derivativePath:string,codec:"av1"|"hevc"="av1"):ArgvPlan {
 if(!inputPath||!derivativePath||inputPath===derivativePath)throw new Error("ORIGINAL_MUST_REMAIN_IMMUTABLE");
 const codecArgs=codec==="av1"?["-c:v","libsvtav1","-crf","26","-preset","6"]:["-c:v","libx265","-crf","26","-preset","slow"];
 return {executable:"ffmpeg",outputPath:derivativePath,args:["-nostdin","-hide_banner","-i",inputPath,...codecArgs,"-c:a","copy","-map_metadata","0","-y",derivativePath]};
}
