# P159 — Account, Device Activation & License Integration

- Added local companion-server license status, activation, and deactivation endpoints.
- A configured license key can have one active device at a time; the current device must deactivate before transfer.
- The database stores a SHA-256 fingerprint, not the raw license key.
- Added Settings UI for entering a key, checking status, activating, and deactivating.
- Configure valid keys on the server with `VIDGRAVITY_LICENSE_KEYS=key1,key2`; unconfigured keys are rejected.

## Validation boundaries
This is a local companion-server adapter, not a hosted account system. It does not implement payment verification, customer identity, remote account recovery, rate limiting, signed entitlements, or production-grade secret management. The UI status does not yet gate download actions.
