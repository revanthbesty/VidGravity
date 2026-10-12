import { useState } from "react";
import { ShareModal } from "./ShareModal";
import type { ShareMedia } from "../hooks/useShare";
export function MediaShareAction({media,label="Share"}:{media:ShareMedia;label?:string}) {
 const [open,setOpen]=useState(false);
 return <><button type="button" onClick={()=>setOpen(true)}>{label}</button>{open&&<ShareModal media={media} onClose={()=>setOpen(false)} />}</>;
}
