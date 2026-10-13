import {
  consumeGhostPayload,
  encryptGhostPayload,
} from "../src/ghostEncryption.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("ghost payload encrypts and consumes a single-use AES-GCM key", async () => {
  const original = new TextEncoder().encode("local view-once payload");
  const session = await encryptGhostPayload(original);
  assert(session.key.length === 32, "AES-256 key should contain 32 bytes");
  assert(session.envelope.iv.length === 12, "GCM IV should be 96 bits");
  assert(session.envelope.ciphertext.length > original.length, "ciphertext should include an authentication tag");
  assert(session.envelope.ciphertext[0] !== original[0], "payload should not be stored as plaintext");
  const key = session.key;
  const decrypted = await consumeGhostPayload(session.envelope, key);
  assert(new TextDecoder().decode(decrypted) === "local view-once payload", "decryption should restore payload");
  assert(key.every((value) => value === 0), "key bytes should be cleared after consumption");
});

Deno.test("ghost payload rejects tampering and clears the one-use key", async () => {
  const session = await encryptGhostPayload(new TextEncoder().encode("sensitive"));
  session.envelope.ciphertext[0] ^= 1;
  let rejected = false;
  try {
    await consumeGhostPayload(session.envelope, session.key);
  } catch {
    rejected = true;
  }
  assert(rejected, "modified ciphertext must fail authenticated decryption");
  assert(session.key.every((value) => value === 0), "key bytes should be cleared even when decryption fails");
});

Deno.test("ghost payload rejects empty input", async () => {
  let rejected = false;
  try {
    await encryptGhostPayload(new Uint8Array());
  } catch (error) {
    rejected = error instanceof Error && error.message === "GHOST_EMPTY_PAYLOAD";
  }
  assert(rejected, "empty payload should be rejected");
});
