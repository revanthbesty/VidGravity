/**
 * Soundtrack identification boundary.
 * A deployment must inject a licensed fingerprint provider; this module never sends audio
 * to an undocumented service or fabricates a match when no provider is configured.
 */
export interface TrackMatch {
  title: string;
  artist: string;
  spotifyTrackId?: string;
  appleMusicUrl?: string;
  confidence: number;
}

export interface FingerprintProvider {
  /** Accepts a fingerprint generated from a 4-second audio sample, not raw media bytes. */
  identify(fingerprint: string, signal?: AbortSignal): Promise<TrackMatch | null>;
}

export interface SoundtrackResult extends TrackMatch {
  spotifyUri?: string;
  spotifyWebUrl?: string;
}

/** A fingerprint extractor should return a provider-specific, non-empty fingerprint for a 4s sample. */
export async function identifySoundtrack(
  fingerprint: string | null | undefined,
  provider: FingerprintProvider | null,
  signal?: AbortSignal,
): Promise<SoundtrackResult | null> {
  if (!fingerprint?.trim() || !provider) return null;
  const match = await provider.identify(fingerprint.trim(), signal);
  if (!match || !match.title.trim() || !match.artist.trim() ||
      !Number.isFinite(match.confidence) || match.confidence < 0.72) return null;
  const result: SoundtrackResult = {
    ...match,
    title: match.title.trim(),
    artist: match.artist.trim(),
    confidence: Math.min(1, Math.max(0, match.confidence)),
  };
  if (match.spotifyTrackId && /^[A-Za-z0-9]{22}$/.test(match.spotifyTrackId)) {
    result.spotifyUri = `spotify:track:${match.spotifyTrackId}`;
    result.spotifyWebUrl = `https://open.spotify.com/track/${match.spotifyTrackId}`;
  }
  if (match.appleMusicUrl && /^https:\/\/music\.apple\.com\//i.test(match.appleMusicUrl)) {
    result.appleMusicUrl = match.appleMusicUrl;
  } else {
    delete result.appleMusicUrl;
  }
  return result;
}
