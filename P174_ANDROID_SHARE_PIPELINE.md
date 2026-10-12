# P174 Android Share-to-Download
Build: `gradle -p apps/android assembleDebug`
Configure a public HTTPS companion using `-PVG_API_BASE_URL=https://your-public-api.example`.
Shared text/plain URLs route through encrypted Android Keystore preferences to automatic WorkManager download or a bottom-sheet format picker calling `/api/formats`. WorkManager polls existing companion jobs, publishes foreground notifications, supports pause/resume/cancel, checks a trusted SHA-256 response header before publishing to MediaStore, and indexes metadata in SQLite. Stable User-Agent and bounded backoff are used instead of rotating identities to evade throttling. Physical-device acceptance remains required.
