/**
 * Client-side, single-session AES-GCM envelope for local view-once media.
 *
 * The caller must keep the returned key only in memory and must never persist it
 * beside the ciphertext. This module does not implement server-side sharing or
 * claim protection against external cameras, rooted devices, or OS compromise.
 */
export interface GhostEnvelope {
  version: 1;
  algorithm: "AES-GCM";
  iv: Uint8Array;
  ciphertext: Uint8Array;
}

export interface GhostSession {
  envelope: GhostEnvelope;
  /** Raw key material. Keep in memory only; call destroyGhostKey when finished. */
  key: Uint8Array;
}

export async function encryptGhostPayload(
  plaintext: Uint8Array,
): Promise<GhostSession> {
  if (plaintext.byteLength === 0) throw new Error("GHOST_EMPTY_PAYLOAD");
  const keyBytes = crypto.getRandomValues(new Uint8Array(32));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  try {
    const key = await crypto.subtle.importKey(
      "raw",
      keyBytes,
      { name: "AES-GCM" },
      false,
      ["encrypt"],
    );
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv, tagLength: 128 },
      key,
      plaintext,
    );
    return {
      envelope: { version: 1, algorithm: "AES-GCM", iv, ciphertext: new Uint8Array(ciphertext) },
      key: keyBytes,
    };
  } catch (error) {
    destroyGhostKey(keyBytes);
    throw error;
  }
}

/** Decrypts once and destroys the caller-owned raw key in a finally block. */
export async function consumeGhostPayload(
  envelope: GhostEnvelope,
  keyBytes: Uint8Array,
): Promise<Uint8Array> {
  if (envelope.version !== 1 || envelope.algorithm !== "AES-GCM") {
    destroyGhostKey(keyBytes);
    throw new Error("GHOST_UNSUPPORTED_ENVELOPE");
  }
  try {
    const key = await crypto.subtle.importKey(
      "raw",
      keyBytes,
      { name: "AES-GCM" },
      false,
      ["decrypt"],
    );
    const plaintext = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: envelope.iv, tagLength: 128 },
      key,
      envelope.ciphertext,
    );
    return new Uint8Array(plaintext);
  } finally {
    destroyGhostKey(keyBytes);
  }
}

/** Best-effort zeroing; JavaScript engines may retain internal copies. */
export function destroyGhostKey(keyBytes: Uint8Array): void {
  keyBytes.fill(0);
}
