export type SceneSearchMode = "transcript" | "visual";
export interface TimestampedScene {
  timestampSeconds: number;
  label: string;
  evidence: string;
  mode: SceneSearchMode;
}
export interface SceneSearchProvider {
  search(query: string, mode: SceneSearchMode, signal?: AbortSignal): Promise<TimestampedScene[]>;
}
export async function findMediaScenes(query: string, mode: SceneSearchMode, provider: SceneSearchProvider, signal?: AbortSignal): Promise<TimestampedScene[]> {
  const normalized = query.trim();
  if (normalized.length < 3 || normalized.length > 240) throw new TypeError("Search must be between 3 and 240 characters");
  const results = await provider.search(normalized, mode, signal);
  return results.filter(item => Number.isFinite(item.timestampSeconds) && item.timestampSeconds >= 0 &&
    item.label.trim().length > 0 && item.evidence.trim().length > 0 && item.mode === mode)
    .map(item => ({ ...item, timestampSeconds: Math.round(item.timestampSeconds * 10) / 10 }))
    .sort((a, b) => a.timestampSeconds - b.timestampSeconds).slice(0, 30);
}
