import { useCallback, useEffect, useRef, useState } from "react";
import { fetchTaskResult, offerShare, saveResultToOPFS, submitAITask, type AITaskSnapshot } from "../lib/aiTasks";

export interface AITaskView extends AITaskSnapshot { fileName: string; result?: Blob; savedLocally?: boolean; shareState?: string; }
export interface AITaskQueue {
  tasks: AITaskView[];
  start: (path: string, payload: FormData, fileName: string) => Promise<void>;
  share: (id: string) => Promise<void>;
  dismiss: (id: string) => void;
}
export function useAITaskQueue(): AITaskQueue {
  const [tasks, setTasks] = useState<AITaskView[]>([]);
  const events = useRef(new Map<string, EventSource>());
  const update = useCallback((id: string, patch: Partial<AITaskView>) => setTasks(current => current.map(t => t.id === id ? { ...t, ...patch } : t)), []);
  const start = useCallback(async (path: string, payload: FormData, fileName: string) => {
    const handle = await submitAITask(path, payload);
    const initial: AITaskView = { ...handle.task, fileName };
    setTasks(current => [initial, ...current]);
    events.current.set(initial.id, handle.events);
    handle.events.addEventListener("snapshot", event => {
      try { update(initial.id, JSON.parse((event as MessageEvent<string>).data) as Partial<AITaskView>); } catch { /* Ignore malformed telemetry. */ }
    });
    handle.events.addEventListener("progress", event => {
      try { update(initial.id, JSON.parse((event as MessageEvent<string>).data) as Partial<AITaskView>); } catch { /* Ignore malformed telemetry. */ }
    });
    handle.events.addEventListener("completed", async event => {
      handle.events.close(); events.current.delete(initial.id);
      try {
        const data = JSON.parse((event as MessageEvent<string>).data) as Partial<AITaskView>;
        update(initial.id, { ...data, status: "completed", progress: 100, message: data.message ?? "Your video is ready." });
        const blob = await fetchTaskResult(initial.id);
        let savedLocally = false;
        try { savedLocally = Boolean(await saveResultToOPFS(blob, fileName)); } catch { savedLocally = false; }
        update(initial.id, { result: blob, savedLocally, message: savedLocally ? "Ready · saved to this device" : "Ready · use Share or Download to save a copy" });
      } catch (error) { update(initial.id, { status: "failed", error: error instanceof Error ? error.message : "Could not retrieve the generated result." }); }
    });
    handle.events.addEventListener("failed", event => {
      handle.events.close(); events.current.delete(initial.id);
      try { const data = JSON.parse((event as MessageEvent<string>).data) as Partial<AITaskView>; update(initial.id, { ...data, status: "failed", error: data.error ?? "The provider could not complete this task." }); }
      catch { update(initial.id, { status: "failed", error: "The provider could not complete this task." }); }
    });
    handle.events.onerror = () => update(initial.id, { message: "Reconnecting to task updates…" });
  }, [update]);
  const share = useCallback(async (id: string) => {
    const task = tasks.find(item => item.id === id);
    if (!task?.result) return;
    try {
      const result = await offerShare(task.result, task.fileName);
      update(id, { shareState: result === "shared" ? "Shared successfully." : result === "downloaded" ? "Download started." : "Share was cancelled or is unavailable." });
    } catch (error) { update(id, { shareState: error instanceof Error ? error.message : "Could not share this file." }); }
  }, [tasks, update]);
  const dismiss = useCallback((id: string) => {
    events.current.get(id)?.close(); events.current.delete(id);
    setTasks(current => current.filter(task => task.id !== id));
  }, []);
  useEffect(() => () => { for (const source of events.current.values()) source.close(); events.current.clear(); }, []);
  return { tasks, start, share, dismiss };
}
