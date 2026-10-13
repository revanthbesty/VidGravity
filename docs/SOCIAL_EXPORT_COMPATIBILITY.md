# VidGravity Social Export Compatibility
Reviewed: 2026-10-13
This is a capability-aware baseline, not a guarantee that a platform will accept every file. Limits can vary by account, app version, region, native upload versus API, and rollout cohort. Revalidate official docs before each release and keep constraints data-driven rather than hard-coded in UI.

## Current source-backed baselines
| Target | Baseline to validate | Notes |
|---|---|---|
| YouTube Shorts | Square/vertical video up to 3 minutes | Official help says uploads on/after 2024-10-15 with square or vertical aspect ratio up to 3 minutes are Shorts for standard channels. A Short over 1 minute with an active third-party Content ID claim can be blocked globally. Source: https://support.google.com/youtube/answer/15424877?hl=en-IN |
| WhatsApp Status | MP4/MPEG-4 or 3GP; current help article says videos up to 90 seconds | The product should not force every status into 30-second segments. Offer user-selectable 15/30/60/90-second segments and validate the latest native-app limits. Source: https://faq.whatsapp.com/643144237275579/?cms_platform=iphone&locale=en_US |
| TikTok Content Posting API | MP4/WebM/MOV; H.264 recommended, H.265/VP8/VP9 listed; 23–60 FPS; each dimension 360–4096 px; developer upload endpoint max 10 minutes and 4 GB in current guide | Account-level native upload duration can differ; current guide says all creators can post 3-minute videos, some have 5/10-minute access. API upload can require user to finish editing/publishing in TikTok. Sources: https://developers.tiktok.com/docs/en/content-posting-api-media-transfer-guide and https://developers.tiktok.com/docs/en/content-posting-api-reference-upload-video |
| Instagram Reels publishing API | Official Meta API reference surfaced through Postman: MP4/MOV, H.264/HEVC, AAC 48 kHz, 23–60 FPS, recommended 9:16, API-specific duration and size constraints | Treat these as API constraints, not universal in-app rules. Re-check Meta's current official API docs before enabling direct publishing. Source: https://www.postman.com/meta/instagram/folder/y6xustx/reels-publishing |

## Export behavior
- Default vertical preset: 1080x1920 (9:16), H.264 video + AAC audio in MP4 for broad compatibility, unless user selects another supported preset.
- Use 30 FPS by default when source is suitable; preserve source frame rate where appropriate and within target limits.
- Provide platform presets plus custom export. Validate dimensions, aspect ratio, frame rate, codec, duration and file size after render using actual output metadata.
- Show precise warnings: “May exceed current API limit”, “Account/app limits may differ”, or “Copyright claim may block this upload”; do not promise guaranteed acceptance.
- Preserve the original. Export to a new file. Show measured output size; compression savings depend on source and settings.
- Never add copyrighted trending music automatically. Let the user choose media they own or are licensed to use.
- Use Android Sharesheet/deep links unless official API access and user authorization support a more direct workflow. Never scrape private feeds or bypass platform access controls.
- Keep platform constraints in a versioned registry with checked date, official URL, native-vs-API scope, and tests. Recheck before release and after platform announcements.

## Acceptance tests
- Given a 16:9 source, vertical export produces 9:16 without stretching; blurred duplicate background is optional.
- Given a clip longer than the target duration, UI offers trim/split/re-encode options and reports exact planned output durations.
- Given unknown platform/account constraints, app warns instead of claiming pass.
- After encoding, ffprobe/Media3 metadata confirms actual dimensions, duration, codec, frame rate and file size.
- Export cancellation removes temporary derivatives and never alters the source.
