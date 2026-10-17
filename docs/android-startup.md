# Android startup investigation

Base: PR #57, integration/android-release-candidate-1 at 80a47f4.

The reported Pixel 10 Pro / Android 17 API 37.2 run builds, installs and creates
an Activity surface without a captured AndroidRuntime fatal exception. That does
not distinguish a main-thread stall, delayed composition, or graphics/presentation
failure. No Android 17-specific root cause has been verified, and this change has
not been emulator/device tested.

Source review found SharedPreferences loading and clipboard binder access on the
initial composition path. Theme preference access now runs on Dispatchers.IO;
clipboard reads wait for frame callbacks, including reads triggered by resume.
MediaStore queries and thumbnails already use Dispatchers.IO. Network inspection,
WorkManager download queries, camera and player initialization are not required
by the initial Home route. No SDK downgrade, blanket software rendering override,
or unrelated dependency upgrade is applied without evidence.

The window has an explicit navy background. A native startup/recovery view remains
above Compose until a composition commits, the root has non-zero layout, and its
draw callback runs. A ten-second main-thread watchdog hides stalled Compose and
offers Retry startup and App settings. Synchronous content installation failures
also enter recovery. This does not catch asynchronous composition exceptions or
recover a blocked main thread; system crash/ANR diagnostics remain necessary.
A draw callback is not proof that pixels were presented by the GPU.

On the affected emulator, collect a fresh launch log:

```sh
adb shell am force-stop com.vidgravity.app
adb logcat -c
adb shell am start -W -n com.vidgravity.app/.MainActivity
adb logcat -v threadtime -s VGStartup AndroidRuntime ActivityTaskManager OpenGLRenderer
```

`VGStartup` logs onCreate begin/end, SDK and target SDK, resume/focus, native host
installation, content installation, preference completion, composition commit,
root layout and draw dispatch with elapsed milliseconds. It does not log URLs,
clipboard contents, media paths or credentials. If onCreate stops progressing,
collect an ANR/thread trace. If commit/layout occur without draw, inspect frame
scheduling. If draw dispatch occurs while both native and Compose UI stay black,
collect screenshots and SurfaceFlinger/graphics diagnostics and compare emulator
graphics backends before changing app rendering policy.

Automated validation: `gradle -p apps/android assembleDebug testDebugUnitTest` and
`python -m unittest discover -s tests -p test_android_startup.py -v`. JVM tests
verify the recovery gate's commit/layout/draw and timeout transitions. Android CI
builds the APK and runs those tests; it does not run an emulator.
