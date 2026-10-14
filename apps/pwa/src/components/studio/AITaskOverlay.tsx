import React from "react";
import type { AITaskView } from "../../hooks/useAITaskQueue";
import "./ai-studio.css";

export function AITaskOverlay({ tasks, onShare, onDismiss }: { tasks: AITaskView[]; onShare: (id: string) => void; onDismiss: (id: string) => void }) {
  if (!tasks.length) return null;
  return <aside className="vg-ai-task-overlay" aria-label="AI task progress" aria-live="polite">
    <header className="vg-ai-task-heading"><div><strong>AI Studio tasks</strong><small>Keep browsing while renders run</small></div><span>{tasks.filter(t => t.status === "running" || t.status === "queued").length} active</span></header>
    <div className="vg-ai-task-list">{tasks.slice(0, 5).map(task => <article className="vg-ai-task" key={task.id}>
      <div className="vg-ai-task-line"><strong>{task.kind === "inpaint" ? "Magic Eraser" : task.kind === "face-swap" ? "Face-Off" : "Motion Transfer"}</strong><span>{task.progress}%</span></div>
      <div className="vg-ai-progress" role="progressbar" aria-label={task.message} aria-valuemin={0} aria-valuemax={100} aria-valuenow={task.progress}><span style={{ width: `${Math.max(0, Math.min(100, task.progress))}%` }} /></div>
      <p>{task.error ?? task.message}</p>
      {task.result && <div className="vg-ai-task-actions"><button type="button" onClick={() => onShare(task.id)}>Share / Download</button>{task.savedLocally && <small>Saved in this browser</small>}</div>}
      {task.shareState && <small>{task.shareState}</small>}
      {(task.status === "failed" || task.status === "completed") && <button type="button" className="vg-ai-dismiss" onClick={() => onDismiss(task.id)}>Dismiss</button>}
    </article>)}</div>
  </aside>;
}
