import { useCallback, useState } from "react";
import type { ChapterMarker, MediaInsightResult, MediaProHooks, TranscriptSegment } from "../../../packages/contracts/src/player";

type AsyncState = "idle" | "loading" | "success" | "error";
export function useMediaProHooks(service: MediaProHooks | null, mediaId: string) {
  const [askState, setAskState] = useState<AsyncState>("idle");
  const [transcriptState, setTranscriptState] = useState<AsyncState>("idle");
  const [chapterState, setChapterState] = useState<AsyncState>("idle");
  const [answer, setAnswer] = useState<MediaInsightResult | null>(null);
  const [transcript, setTranscript] = useState<TranscriptSegment[]>([]);
  const [chapters, setChapters] = useState<ChapterMarker[]>([]);
  const [error, setError] = useState<string | null>(null);
  const ask = useCallback(async (query: string, currentTimeSeconds: number) => {
    if (!service) { setError("Ask My Media is not configured for this account."); return null; }
    const controller = new AbortController(); setAskState("loading"); setError(null);
    try { const result = await service.askMyMedia({ mediaId, currentTimeSeconds, query }, controller.signal); setAnswer(result); setAskState("success"); return result; }
    catch (e) { setAskState("error"); setError(e instanceof Error ? e.message : "Ask My Media request failed."); return null; }
  }, [service, mediaId]);
  const lookupTranscript = useCallback(async () => {
    if (!service) { setError("Transcript lookup is not configured for this account."); return []; }
    setTranscriptState("loading"); setError(null);
    try { const result = await service.lookupTranscript(mediaId); setTranscript(result); setTranscriptState("success"); return result; }
    catch (e) { setTranscriptState("error"); setError(e instanceof Error ? e.message : "Transcript lookup failed."); return []; }
  }, [service, mediaId]);
  const generateChapters = useCallback(async () => {
    if (!service) { setError("Chapter generation is not configured for this account."); return []; }
    setChapterState("loading"); setError(null);
    try { const result = await service.generateChapters(mediaId); setChapters(result); setChapterState("success"); return result; }
    catch (e) { setChapterState("error"); setError(e instanceof Error ? e.message : "Chapter generation failed."); return []; }
  }, [service, mediaId]);
  return { ask, lookupTranscript, generateChapters, answer, transcript, chapters, askState, transcriptState, chapterState, error, clearError: () => setError(null) };
}
