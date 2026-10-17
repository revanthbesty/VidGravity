import React from "react";
import { useAITaskQueue } from "../../hooks/useAITaskQueue";
import { AITaskOverlay } from "./AITaskOverlay";
import { MagicEraser } from "./MagicEraser";
import { FaceSwap } from "./FaceSwap";
import { MotionTransfer } from "./MotionTransfer";
import "./ai-studio.css";

/** Mount this route inside the existing PWA shell; it does not replace the Library or navigation. */
export function AIStudio() {
  const queue=useAITaskQueue();
  return <main className="vg-ai-studio-shell"><header><p className="vg-ai-field-note">VIDGRAVITY · GENERATIVE STUDIO</p><h1>AI Video Studio</h1><p>Erase objects, swap faces, and transfer choreography. Tasks run in the background so your Library stays usable.</p></header>
    <div className="vg-ai-studio-grid"><MagicEraser onTaskStarted={queue.start}/><FaceSwap onTaskStarted={queue.start}/><MotionTransfer onTaskStarted={queue.start}/></div>
    <AITaskOverlay tasks={queue.tasks} onShare={id=>void queue.share(id)} onDismiss={queue.dismiss}/>
  </main>;
}
