import type { StudyFlashcard } from "../../../packages/contracts/src/studio";
export interface TranscriptSegment {startSeconds:number;endSeconds:number;text:string;}
export function validateFlashcards(value:unknown):StudyFlashcard[] {
 if(!Array.isArray(value))throw new Error("FLASHCARDS_MUST_BE_ARRAY");
 return value.map((x):StudyFlashcard=>{
  if(!x||typeof x.front!=="string"||typeof x.back!=="string"||typeof x.timestamp!=="string"||!x.front.trim()||!x.back.trim()||x.front.length>500||x.back.length>2000||x.timestamp.length>32)throw new Error("INVALID_FLASHCARD");
  return {front:x.front.trim(),back:x.back.trim(),timestamp:x.timestamp.trim()};
 });
}
export function rankConceptSegments(segments:readonly TranscriptSegment[],limit=3):TranscriptSegment[] {
 if(!Number.isInteger(limit)||limit<1||limit>10)throw new Error("INVALID_SEGMENT_LIMIT");
 return [...segments].filter(s=>s.text.trim()&&Number.isFinite(s.startSeconds)&&s.endSeconds>s.startSeconds).sort((a,b)=>score(b.text)-score(a.text)).slice(0,limit).sort((a,b)=>a.startSeconds-b.startSeconds);
}
function score(text:string):number {const words=text.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);const cues=/definition|therefore|in summary|key concept|important|because|means|is called|principle|formula|exam|remember/g;return new Set(words).size+((text.match(cues)||[]).length*5)+Math.min(text.length/100,4);}
export function flashcardsCsv(cards:readonly StudyFlashcard[]):string {const q=(s:string)=>'"'+s.replace(/"/g,'""')+'"';return ["front,back,timestamp",...cards.map(c=>[c.front,c.back,c.timestamp].map(q).join(","))].join("\r\n");}
export function studyNotesMarkdown(cards:readonly StudyFlashcard[],mediaUrl:string):string {if(!/^https?:\/\//.test(mediaUrl))throw new Error("INVALID_MEDIA_URL");return "# StudyTok Notes\n\n"+cards.map(c=>`## ${c.front}\n\n${c.back}\n\n[Play at ${c.timestamp}](${mediaUrl})\n`).join("\n");}
