export interface Box {x:number;y:number;width:number;height:number;kind:"face"|"license-plate";}
export interface BlurPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
/** Detection is intentionally injected: use an approved on-device/server detector, never guess boxes. */
export function buildPrivacyBlurPlan(inputPath:string,outputPath:string,boxes:readonly Box[],frameWidth:number,frameHeight:number):BlurPlan {
 if(!inputPath||!outputPath||inputPath===outputPath||frameWidth<1||frameHeight<1)throw new Error("INVALID_PRIVACY_BLUR");
 const safe=boxes.filter(b=>[b.x,b.y,b.width,b.height].every(Number.isFinite)&&b.width>0&&b.height>0&&b.x>=0&&b.y>=0&&b.x+b.width<=frameWidth&&b.y+b.height<=frameHeight);
 if(!safe.length)throw new Error("NO_VALID_PRIVACY_REGIONS");
 const filters=safe.map((b,i)=>`[0:v]split[base${i}][blurIn${i}];[blurIn${i}]crop=${Math.round(b.width)}:${Math.round(b.height)}:${Math.round(b.x)}:${Math.round(b.y)},boxblur=20:10[blur${i}];[base${i}][blur${i}]overlay=${Math.round(b.x)}:${Math.round(b.y)}[v${i}]`).join(";");
 return {executable:"ffmpeg",outputPath,args:["-nostdin","-hide_banner","-i",inputPath,"-filter_complex",filters,"-map",`[v${safe.length-1}]`,"-map","0:a?","-c:a","copy","-y",outputPath]};
}
