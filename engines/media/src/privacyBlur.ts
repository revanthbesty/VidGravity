export interface Box {x:number;y:number;width:number;height:number;kind:"face"|"license-plate";}
export interface BlurPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
/** Detection is injected from an approved detector; this function only builds a validated derivative render. */
export function buildPrivacyBlurPlan(inputPath:string,outputPath:string,boxes:readonly Box[],frameWidth:number,frameHeight:number):BlurPlan {
 if(!inputPath||!outputPath||inputPath===outputPath||!Number.isSafeInteger(frameWidth)||!Number.isSafeInteger(frameHeight)||frameWidth<1||frameHeight<1)throw new Error("INVALID_PRIVACY_BLUR");
 const safe=boxes.map(b=>({ ...b, x:Math.round(b.x), y:Math.round(b.y), width:Math.round(b.width), height:Math.round(b.height) })).filter(b=>[b.x,b.y,b.width,b.height].every(Number.isFinite)&&b.width>=2&&b.height>=2&&b.x>=0&&b.y>=0&&b.x+b.width<=frameWidth&&b.y+b.height<=frameHeight);
 if(!safe.length)throw new Error("NO_VALID_PRIVACY_REGIONS");
 const filters:string[]=[];
 let previous="[0:v]";
 safe.forEach((b,i)=>{
  const merged=`merged${i}`,blurIn=`blurIn${i}`,blur=`blur${i}`,splitBase=`splitBase${i}`;
  filters.push(`${previous}split=2[${splitBase}][${blurIn}]`);
  const blurRadius=Math.max(1,Math.min(10,Math.floor((Math.min(b.width,b.height)-1)/2)));
  filters.push(`[${blurIn}]crop=${Math.round(b.width)}:${Math.round(b.height)}:${Math.round(b.x)}:${Math.round(b.y)},boxblur=${blurRadius}:${Math.max(1,Math.floor(blurRadius/2))}[${blur}]`);
  filters.push(`[${splitBase}][${blur}]overlay=${Math.round(b.x)}:${Math.round(b.y)}[${merged}]`);
  previous=`[${merged}]`;
 });
 return {executable:"ffmpeg",outputPath,args:["-nostdin","-hide_banner","-i",inputPath,"-filter_complex",filters.join(";"),"-map",previous,"-map","0:a?","-c:a","copy","-y",outputPath]};
}
