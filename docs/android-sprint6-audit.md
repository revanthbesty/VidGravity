# Sprint 6 — integrated workspace audit

## Verified baseline
Live draft63:8ca0c7070030bd7ab3949c20d25f72ae70b1722f, draft65:
20deff238ce475c34e6186ab99dbead43257da9b, draft66:
41fe487363aed15fb499ebe7aa34866e46b9e38b. Stacked integration ends at
codex/android-sprint-5, not main. Run39137789387 passes build/unit/lint/regression
and23 instrumentation tests on each API29/35. APK12140174043 is unexpired with
actual checksum a6469959fc09f154bf861c9e6a65136faa7d74c2e78122657b7a4a3d9d400870.
Baseline rechecked through live GitHub metadata/jobs/logs before implementation.
Sprint6 is a separate branch; earlier branches remain unchanged.

## Inventory and verification matrix
| Feature | Entry/navigation | Actual implementation | Persistence/export | Automated coverage | Limits |
|---|---|---|---|---|---|
| Home | Home tab | Focused Create, Continue, camera/import, original template picks, recent native exports | Real project IDs/export records | IntegratedCreatorUiTest, HomeAccessibilityUiTest | Picks are curated, no popularity data |
| New video | Home Create / Studio New | Existing OpenDocument → native timeline | Private versioned project, originals retained | NativeStudioProjectUiTest + integration | Picker requires user media selection |
| Continue | Home → named project; Studio tab resumes active project | Loads exact project ID; route change awaits save callback | Repository stale-write protection; explicit save error keeps editor open | IntegratedCreatorUiTest, WorkspaceReliabilityTest | Project metadata references content URIs; revoked grants need relink |
| Native Studio | Studio → device editor | Existing trim/split/reorder/merge/speed/history, preview and export | Schemas1–4 remain supported; autosave | All Sprint1–5 tests retained | Undo history stays in memory while editor is open |
| Context tools | Editor → Color/Audio lanes/Captions/Encoding | Existing tool panels, now contextual; sound disclosure | Existing project settings/native rendering | Updated existing tests select panels; assertions retained | Caption motion is in Social Creator |
| Creative tools | Editor Create tools → Creative Studio | Existing smart cuts, framing, writing, captions and covers | Project settings/derived projects/PNG export | CreativeIntegrationTest/CreativeWorkspaceUiTest | Rules vs inference clearly labelled; no video-file STT/LLM |
| Social tools | Editor → Social Creator; Home template pick | Existing templates/branding/format variants/serial queue | Immutable WorkManager requests, MediaStore outputs | SocialCreatorIntegrationTest/SocialCreatorUiTest | Photo timelines, PiP, split-screen, freeze frames deferred |
| Camera | Camera tab / Quick Camera | Existing CameraX photo/video, flip/zoom/flash/timer, supported dual mode | Captures indexed; result opens video timeline or image workspace | Existing contracts + synthetic result-routing/save test | No actual camera capture or permission-denial UI exercised; hardware varies |
| Camera recovery | Camera entry | Explanation before permission, Capture disabled before init, retryable init/permission failure | Existing capture files preserved | Source contracts; no physical camera claims | Camera controls intentionally use dark overlay for readable preview |
| Library | Library tab | Single lazy container, list/adaptive grid, bounded previews, search | Existing index, rename/share/favorites/delete/bulk tools retained | Populated PNG grid/search/preview test | Missing index files removed; project relink remains in Studio |
| Downloads | Downloads tab | Link inspect/quality/batch/audio; queue/progress/history/retry/cancel | Existing WorkManager/local Library | Full download/share regression; root navigation test | Remote provider/network and pause/resume support vary |
| Inbox | Android text share / Downloads Inbox | Existing normalized saved links; inspect now routes to Downloads | Existing private inbox | Existing singleTop test; URL regression | Public link imports, not arbitrary external media intent support |
| Legacy source tools | Studio creator hub | Existing story/sticker/status/audio exports | DownloadApi service-backed render + Library | Existing Python export/share contracts | Requires configured reachable media API; separate from native offline timeline |
| Player / image preview | Library / recent export / legacy workspace | Existing playback + sampled image preview; image bitmap released on disposal | Existing resume/share behavior | Populated-library image path; existing tests | Broad codec/device support unverified |
| Swipe Feed / StudyTok | Library Swipe Feed / Downloads advanced | Existing collection playback and study tools | Existing behaviors unchanged | Existing regression | StudyTok optional/online; not local LLM |
| Settings/theme/help | Settings icon / theme toggle / Help | Shared navy/teal/violet and light themes, guide, skippable inline welcome | UI preferences local | Dark/light toggle, large-font onboarding | No exhaustive screen-reader/device accessibility certification |
| Licensing | Settings availability notice | Truthful unavailable notice replaces unusable key entry | No activation calls or paid dependencies | Manual source audit | Licensing integration remains unavailable |
| Startup recovery | MainActivity launcher | StartupHost milestones/retry preserved | Existing startup gate | Existing nonblack screenshot, navigation and recovery tests | API37/37.2 only if published SDK image exists |

## Findings repaired
- Home had multiple repeated creation/download/camera entries and a coming-soon
  Pro card. The new Home has one primary Create action; link work moved to Downloads.
- Library nested a vertical lazy list inside vertical scrolling. Populated media
  now uses one lazy container; there is no unbounded nested vertical measure.
- Library action rows were too wide for small screens. Secondary actions use a
  labelled menu, and grid falls back to one column at large font scales.
- Leaving Studio could dispose a pending400ms autosave and forget which project
  was open. Root navigation/camera/inbox now await an editor save callback before
  leaving; active ID is hoisted/saveable and reopens on Studio return. Repository
  writes are serialized across instances and reject older snapshots.
- Advanced audio/caption/color/encoding controls competed in one long panel.
  Contextual sections retain the same implementation. No features/tests removed.
- Shared-link Inspect previously routed to Home; it now routes to its Downloads
  field. Camera results use one validated local-content result adapter.
- Unavailable licensing no longer invites entering/copying an unconnected key.

## Design, performance and accessibility
VgTheme, VgSection, VgDisclosure, MediaThumbnail and LibraryMediaCard are reusable.
Five primary tabs are Home/Studio/Camera/Library/Downloads; Settings is top-level
icon only. Camera launches the existing dedicated Activity; it does not create a
second editor. Help and optional onboarding share the same essential workflows.
Dark/light controls use Material semantic colors, shapes and typography. Controls
retain semantic roles/labels and48dp targets where modified. Font wrapping and
adaptive columns replace fixed grids; native camera controls remain dark for
contrast over live imagery. System animation-duration scale is not overridden;
no perpetual decorative motion was added.

Shared thumbnail cache is bounded to8MiB; decode concurrency is2 and image/video
previews are bounded to320px. Cache eviction does not recycle a bitmap still used
by a visible composable. Full image previews retain the existing2048px bound and
now release their owned bitmap. Library filtering/sorting is remembered by inputs.
Home lists and media rows use lazy containers with stable keys. Disk/media work
added by this sprint runs on IO; root startup still does not await preferences.

WorkspaceReliabilityTest measures two requests for one2048×1024 image: exactly one
bounded decode, same cached bitmap, dimensions/bytes and elapsed time logged with
VGPerf. This demonstrates avoided duplicate decoding, NOT a startup/frame-rate,
battery or whole-app speed claim. Existing VGStartup logs still report actual
first-frame milestones. No before/after device profiling, thermal/battery benchmark
or camera throughput measurement was performed. Full MediaStore inventory scans
and private JSON project scans remain; an indexed metadata database is future work.

## Scope/limitations and Sprint7
All prior native editing, smart/social, downloads, legacy export and share paths
remain. New test cases target navigation-save/reopen, populated Library, themes,
large font/onboarding, bounded cache, stale saves and synthetic camera results.
Tests do not simulate physical camera/microphone or prove every API/device/codec.
Permission messaging and camera visual changes are source-reviewed, not hardware
validated. Downloads' advanced reporting/filter controls remain available rather
than being deleted. Incoming text shares retain existing handling; general ACTION_VIEW
media deep-link support is not added. Onboarding skip persists on this device.
Navigation save failure deliberately blocks leaving; source media remains unchanged.

Sprint5 limitations remain: queue dependency failures need retries; exactly-once
MediaStore publication across process death is not guaranteed; brand asset cleanup,
photo montage, PiP/split-screen, continuous ramps, word highlighting and local
video STT/LLM are incomplete. Full tablet/landscape and screen-reader audits remain.
No production infrastructure, merge, deployment, purchase or physical testing.
Sprint7 priorities: process-death publication recovery, project/asset portability,
CameraX device matrix and accessibility audits, paged metadata indexing, and measured
frame-time/startup benchmarks. Run API37 when a published image becomes available.
