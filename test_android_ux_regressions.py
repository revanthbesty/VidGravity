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
