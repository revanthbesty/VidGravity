# VidGravity — Social Media Everyday-Life Feature Roadmap

**Status:** Pipeline / planned only — not implemented or verified by this document.  
**Date:** 2026-10-15  
**Delivery:** Android APK first; ₹0 additional-cost target; P0 reliability before enhancements; small tested batches; preserve existing SRS and platform scope. Keep PWA unchanged unless separately approved.

## Product goal

Serve two audiences through one local-first media utility:
- **Simple Mode:** save supported content for later, organize files, view, and share.
- **Creator Mode:** edit original or otherwise authorized media and prepare social-ready exports.

Target compatibility includes YouTube, Snapchat, Instagram, Facebook, WhatsApp and other social platforms, but support is capability-specific and must be tested. VidGravity is a companion utility, not a replacement for platform feeds, chat systems, comments, accounts, or official playback.

## Pipeline entries

All items below are **planned/backlog**. Reconcile IDs with the SRS and existing feature ledger before implementation; reuse existing modules rather than duplicating them.

| ID | Feature | Priority after P0 | Scope and acceptance evidence |
|---|---|---|---|
| VG-SM-001 | Universal Share Inbox | P1 after P0 | Receive supported text, URLs, images, video, audio and multiple selected files via Android Sharesheet/intents. Preview and confirm before processing. Test ACTION_SEND/ACTION_SEND_MULTIPLE, MIME types, malformed and oversized input. |
| VG-SM-002 | Save for Later / Link Shelf | P1 after P0 | Save URL, title when available, user note, tags, date and Watch Later state locally. Test restart persistence, invalid links, search/filter and backup/export. |
| VG-SM-003 | Smart Media Vault | P1 after P0 | Unified local library with thumbnails, duration, size, format, resolution, tags, search, albums, duplicate hints and safe temporary-file cleanup. Test indexing, restart, permissions, false-positive duplicates and deletion confirmation. |
| VG-SM-004 | Create Once, Share Anywhere | P2 | Hand off user-owned/authorized exports via Android's native Sharesheet; do not claim a post succeeded just because the chooser opened. Test common MIME types and target-app behavior on-device. |
| VG-SM-005 | Social Export Presets | P2 | Configurable presets for YouTube Shorts, Instagram Reels/Stories, Snapchat, Facebook Reels, WhatsApp Status and generic vertical/square/landscape sharing. Validate dimensions, codec, duration, size and real target-app playback/upload before claiming compatibility. |
| VG-SM-006 | Quick Photo Studio | P2 | Local crop, rotate, resize, simple filters, collage, text/stickers, thumbnails and optional watermark. Test quality, metadata policy, large-image memory, cancel/undo and output integrity. |
| VG-CAM-001 | Live Camera Filter Library | P2 — high user value after P0 | Minimum 20 working filters; target 25 named launch filters. Offline-first live photo/video filter categories: natural, portrait, warm/cool, cinematic, vintage/film, monochrome, food, travel, night, vivid and playful. Add intensity control, Original/before-after, favorites and recent filters. Selected effects must be applied to saved output when requested, not preview-only. Test live preview, photo/video output, intensity/reset, camera flip, orientation, latency, heat and low-end fallback. |
| VG-CAM-002 | Camera Templates & Shot Guides | P2 — high user value after P0 | Minimum 100 usable templates with real previews and output; many reusable photo/video templates for selfies/portraits, couples/groups, travel, food, pets, products, cinematic clips, profile photos, festivals, collages, quote/story/status cards and vertical social framing. Guide framing/aspect ratio/overlays/text/shot order/timing; allow preview and edits. Test valid outputs, safe text areas, aspect ratios, cancel/undo and draft recovery. |
| VG-CAM-003 | Filter & Template Packs | P2 — high user value after P0 | Include an offline baseline of at least 20 working filters and 100 usable templates. Categorized packs such as Portrait, Cinematic, Vintage, Nature/Travel, Food, Night, Monochrome, Fun, Festivals and Creator/Story. Ship a useful free offline starter pack; support browse/search/preview/favorites/reset and a metadata-driven way to add more licensed/free packs. Test offline first-run, manifest, persistence, asset-size budget, missing-pack behavior, accessibility and asset licensing/provenance. |
| VG-SM-007 | Quick Video Studio | P2 | Local trim/split/merge where supported, speed, mute/audio selection, simple text/title cards, preview and draft recovery. Preserve originals. Test duration, codec, audio sync, cancellation and Library output. |
| VG-SM-008 | Captions & Post Pack | P2 | Editable caption/title/hook/description/hashtag suggestions and one-tap copy. AI optional. Test Unicode/languages and copy/paste; no automatic posting. |
| VG-SM-009 | Chat & Comment Draft Helper | P2 | User manually shares/pastes selected text to rewrite, shorten, translate or change tone, then copies the draft back. No auto-send, background DM reading, contact scraping or account login. |
| VG-SM-010 | Privacy Shield | P2 | Optional location-metadata removal, crop, manual blur/redaction and pre-share review. Test output metadata, original preservation, undo and redaction workflow. Detection models require a verified local model or explicit online consent. |
| VG-SM-011 | Audio & Voice Note Tools | P2 | Trim/convert supported audio, normalize where practical and optional user-initiated transcription with a verified local/free engine. Test codecs, playback, transcript quality before claiming language support, and no silent upload. |
| VG-SM-012 | Watch Later & Universal Player | P2 | Local playlists, resume, speed, subtitles when available, favorites, open/share and missing-file recovery. Open external social links in official apps/sites rather than bypassing platform playback. |
| VG-SM-013 | Story/Status Planner | P2 | Create and reorder clips/cards, preview, save drafts and export. Preserve existing 30/60/90/120-second split options. User posts manually. Test durations, order, audio sync, cancellation and output files. |
| VG-SM-014 | Multilingual & Accessibility Studio | P2/P3 | English and selected Indian languages, caption editing, screen-reader labels, contrast and one-handed controls. Add languages only after font/rendering and quality tests. |
| VG-SM-015 | Smart Batch Actions | P3 | Batch rename/tag/convert/export, queue presets, storage estimates and recoverable cleanup. Test partial failures, cancellation and non-destructive behavior. |
| VG-SM-016 | Optional AI Creator Copilot | P3 | Local/rule-based assistance first; optional online AI for caption/storyboard ideas, translation and edit suggestions. Confirm genuinely free availability, quotas, data disclosure, opt-in consent and offline/manual fallback. |
| VG-SM-017 | Creator Checklist & Reminders | P3 | Local checklist for caption, cover, aspect ratio, audio and privacy; optional local reminder. No unsupported auto-posting or scheduler claims. |
| VG-SM-018 | Platform Capability Registry | P1/P2 foundation | Tested matrix by platform and input type: shared URL/file, deep link, import/export support, restrictions and last-tested app/version. Unsupported actions must be disabled or labelled clearly. |

## Recommended batch order after P0

1. **Batch A — Share Inbox foundation:** VG-SM-001 + minimal VG-SM-018 registry.
2. **Batch B — Save and find:** VG-SM-002 + basic VG-SM-003 local search/indexing.
3. **Batch C — Share out:** VG-SM-004 + VG-SM-005; start with generic exports and validate WhatsApp Status / Instagram-style vertical outputs on-device.
4. **Batch D — Local creation:** VG-SM-006 + VG-SM-007 + VG-SM-013; reuse Create Studio and Status Splitter.
5. **Batch D2 — Photo/video enthusiast camera:** VG-CAM-001 live filters, then VG-CAM-002 templates and VG-CAM-003 packs; prioritize a free offline starter set, and validate real saved outputs/performance on a physical device.
6. **Batch E — Text and privacy:** VG-SM-008 + VG-SM-009 + VG-SM-010, useful without online AI.
7. **Batch F — Audio, viewing and localization:** VG-SM-011 + VG-SM-012 + VG-SM-014.
8. **Batch G — Automation and optional AI:** VG-SM-015 + VG-SM-016 + VG-SM-017 only after free-service and privacy feasibility is demonstrated.

## Photo/video enthusiast experience

The camera should feel like a compact creative studio, not just a capture button: keep shutter/record and camera-switch controls in the one-hand reach zone; show a horizontally browsable filter strip with readable names/thumbnails, intensity slider, favorites/recent, an obvious Original option and before/after comparison. Group templates by activity and output shape. Keep processing local/offline where practical, preserve originals by default, and never claim a filter is baked into the file unless the exported result proves it. Audit any GPU filter dependency for license, maintained build, device compatibility, APK size and performance before adopting it. Filter/template packs are planned, not implemented.



### Minimum launch inventory — explicit acceptance requirement

**Minimum: 25 named filters and 100 named templates.** These are the planned launch inventory, not yet implemented assets. Filter names describe looks to build using locally available image/video processing; no face-beauty, blur, LUT, or other effect is claimed until it exists and is tested. Template names are reusable layout/shot-guide concepts; each template must have an actual preview/layout definition and usable output, not just a label.

#### Filter catalogue (25)

1. Natural Glow
2. Soft Portrait
3. Warm Skin Tone
4. Cool Skin Tone
5. Golden Hour
6. Sunset Pop
7. Cinematic Teal & Orange
8. Moody Cinema
9. Vintage Film
10. Retro Fade
11. Analog Grain
12. Black & White
13. High-Contrast Mono
14. Soft Matte
15. Vivid Color
16. Pastel Dream
17. Nature Green
18. Travel Bright
19. Food Boost
20. Night Clarity
21. Cool Night
22. Dreamy Bloom
23. Sepia Classic
24. Comic Pop
25. Neon Night

#### Template catalogue (100 across 10 categories)

**Selfies & Portraits (10 templates)**

1. Clean Profile Portrait
2. Soft Portrait Frame
3. Golden-Hour Selfie
4. Mirror Selfie Layout
5. Close-Up Beauty Frame
6. Black-and-White Portrait
7. Cinematic Portrait
8. Studio-Style Portrait
9. Casual Outdoor Portrait
10. Portrait Contact Sheet

**Friends, Couples & Family (10 templates)**

1. Best-Friends Collage
2. Couple Memory Card
3. Date-Night Story
4. Family Moments Grid
5. Group Photo Frame
6. Birthday Friends Card
7. Travel Buddies Layout
8. Before-and-After Duo
9. Four-Moment Friendship Story
10. Family Celebration Cover

**Travel & Outdoors (10 templates)**

1. Travel Diary Cover
2. Weekend Getaway Story
3. Mountain Adventure Card
4. Beach-Day Story
5. City Explorer Reel
6. Road-Trip Timeline
7. Sunrise/Sunset Frame
8. Nature Walk Montage
9. Passport-Style Travel Grid
10. Destination Postcard

**Food & Cafes (10 templates)**

1. Cafe Review Card
2. Coffee Flat-Lay Frame
3. Restaurant Highlight
4. Homemade Meal Story
5. Dessert Showcase
6. Recipe Step Card
7. Food Review Cover
8. Menu Feature Layout
9. Street-Food Diary
10. Top-Three Dishes Collage

**Pets & Nature (10 templates)**

1. Pet Portrait Card
2. Pet Birthday Story
3. Pet Adventure Reel
4. Garden Moments Frame
5. Flower Close-Up Layout
6. Wildlife Observation Card
7. Rainy-Day Nature Story
8. Seasonal Nature Grid
9. Pet-and-Owner Portrait
10. Nature Color Diary

**Fashion & Lifestyle (10 templates)**

1. Outfit of the Day
2. Wardrobe Lookbook
3. Style Moodboard
4. Shopping Haul Cover
5. Minimal Lifestyle Story
6. Daily Routine Timeline
7. Fitness Progress Card
8. Wellness Journal Frame
9. Accessories Showcase
10. Personal Brand Portrait

**Creator & Video Shots (10 templates)**

1. Vertical Hook Intro
2. Three-Shot Storyboard
3. Five-Shot Mini Vlog
4. Product Reveal Sequence
5. Talking-Head Title Frame
6. Tutorial Step Sequence
7. Unboxing Storyboard
8. Cinematic B-Roll Plan
9. Transition Practice Guide
10. Outro and Follow Prompt

**Stories, Status & Quotes (10 templates)**

1. Minimal Status Card
2. Bold Quote Story
3. Daily Thought Card
4. Question Sticker Layout
5. Poll Prompt Card
6. Announcement Story
7. Countdown Story
8. Thank-You Status
9. Good-News Celebration
10. Simple Text-and-Photo Story

**Festivals & Celebrations (10 templates)**

1. New Year Greeting
2. Pongal Celebration Card
3. Diwali Lights Story
4. Holi Color Story
5. Eid Greeting Frame
6. Christmas Memory Card
7. Birthday Greeting
8. Anniversary Memory
9. Graduation Celebration
10. Achievement Announcement

**Collage, Product & Utility (10 templates)**

1. Two-Photo Comparison
2. Four-Photo Grid
3. Nine-Photo Mosaic
4. Photo Strip Collage
5. Product Hero Shot
6. Product Detail Grid
7. Marketplace Listing Cover
8. Event Poster Layout
9. Thumbnail with Safe Text Area
10. Landscape Memory Album

#### Inventory acceptance gates

- Ship at least **20 working filters** in the first usable camera release; target the full 25-filter catalogue at launch if performance permits. The filter picker must show real previews, intensity where meaningful, Original/reset, and favourite/recent states.
- Ship at least **100 usable templates** in the launch catalogue, with real previews, category browsing/search, editable text/layout where applicable, and working export/capture flows. Do not count empty placeholders as templates.
- Starter filters and templates must work offline and be included without paid packs/accounts. Additional packs can be added later, but the baseline counts must not depend on downloading paid assets.
- Test both photo and video where a filter claims to support both; mark unsupported modes clearly. Verify that saved output matches the selected filter/template, and that camera flip, orientation, cancel/undo, memory use, frame rate, heat and low-end-device fallback are acceptable.
- Use original or appropriately licensed assets; record source/license for each external asset. Keep originals unchanged by default.

## Platform, privacy and budget guardrails

- Prefer official Android sharing intents and the native Sharesheet; let users review incoming content before action.
- Platform names are compatibility targets, not permission to bypass access controls. Process/download only content the user owns, is authorized to use, or the platform explicitly permits.
- Do not use YouTube integration/API paths to enable unauthorized offline downloads, audio separation, ad blocking or background playback. If a download is not authorized, offer a bookmark or open the official app/site.
- Do not scrape private chats/comments/contacts, capture credentials/cookies, automate likes/views/comments, or auto-send/post without an officially supported, explicitly authorized integration.
- Local-first by default. Online AI or transcription must be opt-in, disclose data leaving the device, and preserve a non-AI/manual path. No paid APIs, mandatory accounts or paid infrastructure under the ₹0 target.
- Originals remain unchanged by default. Deletion/overwrite requires explicit confirmation; never upload private media silently.

## Definition of done per batch

- Requirement ID, scope, platform, dependencies and tests recorded before implementation.
- Isolated branch and small change set; do not add these enhancements to the P0 bug-fix PR.
- Targeted tests and exact-head CI results attached.
- For APK changes: version/versionCode, exact commit, artifact URL, size and SHA-256 recorded; install and test on a physical Android device.
- Platform compatibility claims backed by platform/app-version-specific device smoke tests.
- Master tracker and feature ledger updated; Planned, Coded, CI passed, APK artifact verified, Device verified and Released remain distinct states.
