from pathlib import Path

ROOT = Path(__file__).parent
MAIN = ROOT / "apps/android/app/src/main/java/com/vidgravity/app/MainActivity.kt"
CAMERA = ROOT / "apps/android/app/src/main/java/com/vidgravity/camera/DualCameraCaptureActivity.kt"


def test_screen_host_does_not_intercept_screen_scroll():
    source = MAIN.read_text(encoding="utf-8")
    assert "Box(Modifier.fillMaxSize().padding(padding)) {" in source


def test_studytok_and_status_quick_actions_do_not_require_a_url():
    source = MAIN.read_text(encoding="utf-8")
    assert source.index('if (action.contains("StudyTok"))') < source.index('else if (url.isBlank())')
    assert 'else if (action.contains("Status Clip"))' in source


def test_sound_only_falls_back_when_extractor_omits_audio_only_formats():
    source = MAIN.read_text(encoding="utf-8")
    assert 'val selector = audio?.selector ?: "bestaudio"' in source
    assert 'No separate audio stream was listed; queued best-audio extraction.' in source


def test_creator_placeholders_open_a_real_media_picker():
    source = MAIN.read_text(encoding="utf-8")
    assert 'Text("Choose video for animated sticker")' in source
    assert 'Text("Choose video for WhatsApp Status")' in source
    assert source.count('onClick = { picker.launch(arrayOf("video/*")) }') >= 2


def test_camera_exposes_quality_and_flash_controls():
    source = CAMERA.read_text(encoding="utf-8")
    assert 'qualityButton = controlButton("Quality: 1080p") { chooseVideoQuality() }' in source
    assert "enableTorch(requestedState)" in source
    assert "Flash ON · tap off" in source
    assert "qualityButton.isEnabled = false" in source
    assert 'Video quality set to ' in source


def test_camera_captures_are_published_to_shared_gallery():
    source = CAMERA.read_text(encoding="utf-8")
    assert "publishCapture(target, \"image/jpeg\")" in source
    assert "publishCapture(target, \"video/mp4\")" in source
    assert 'put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/VidGravity" else "Pictures/VidGravity")' in source


def test_story_editor_hides_advanced_controls_until_requested_and_detects_audio_extensions():
    story = (ROOT / "apps/android/app/src/main/java/com/vidgravity/app/StoryCardScreen.kt").read_text(encoding="utf-8")
    assert 'Fine-tune clip, caption & layout' in story
    assert 'if (showAdvanced)' in story
    assert 'isAudioStorySource(media.mimeType, media.displayName)' in story
    assert '"mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "wma", "weba"' in story


def test_story_export_retries_interrupted_connection_and_infers_audio_mime():
    api = (ROOT / "apps/android/app/src/main/java/com/vidgravity/app/DownloadApi.kt").read_text(encoding="utf-8")
    assert 'for (attempt in 0..1)' in api
    assert 'The export connection was interrupted twice.' in api
    assert 'setRequestProperty("Connection", "close")' in api
    assert 'private fun resolveExportMime' in api
    assert 'VidGravity could not identify this as an audio file.' in api
