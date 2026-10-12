import { useCallback, useState } from "react";
import type { DownloadProgress, DownloadRequest, DownloadEngine, TaskId } from "../../../packages/contracts/src/downloader";
import type { SectionDownloadRequest } from "../../../packages/contracts/src/download-enhancements";
import { validateTimeRange } from "../../../packages/contracts/src/download-enhancements";
export interface DownloadService extends DownloadEngine { enqueueSection(request: SectionDownloadRequest): Promise<TaskId>; }
export function useDownloader(service: DownloadService | null) {
  const [activeTask,setActiveTask] = useState<TaskId|null>(null);
  const [progress,setProgress] = useState<DownloadProgress|null>(null);
  const [error,setError] = useState<string|null>(null);
  const enqueue = useCallback(async (request: DownloadRequest) => {
    if (!service) throw new Error("Download service is not connected.");
    setError(null);
    try { const id=await service.enqueue(request); setActiveTask(id); setProgress(await service.getProgress(id)); return id; }
    catch(e) { const message=e instanceof Error?e.message:"Could not queue download."; setError(message); throw e; }
  },[service]);
  const enqueueSection = useCallback(async (request: SectionDownloadRequest) => {
    if (!service) throw new Error("Download service is not connected.");
    validateTimeRange(request.range);
    setError(null);
    try { const id=await service.enqueueSection(request); setActiveTask(id); setProgress(await service.getProgress(id)); return id; }
    catch(e) { const message=e instanceof Error?e.message:"Could not queue section download."; setError(message); throw e; }
  },[service]);
  const refresh = useCallback(async () => {
    if (!service || !activeTask) return null;
    try { const value=await service.getProgress(activeTask); setProgress(value); return value; }
    catch(e) { setError(e instanceof Error?e.message:"Could not refresh download status."); return null; }
  },[service,activeTask]);
  const pause = useCallback(()=>{ if(!service||!activeTask) throw new Error("No active download."); return service.pause(activeTask); },[service,activeTask]);
  const resume = useCallback(()=>{ if(!service||!activeTask) throw new Error("No active download."); return service.resume(activeTask); },[service,activeTask]);
  const cancel = useCallback(()=>{ if(!service||!activeTask) throw new Error("No active download."); return service.cancel(activeTask); },[service,activeTask]);
  return { activeTask,progress,error,enqueue,enqueueSection,refresh,pause,resume,cancel };
}
