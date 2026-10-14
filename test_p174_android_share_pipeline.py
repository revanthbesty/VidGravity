from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent
ANDROID=ROOT/"apps"/"android"
class P174AndroidSharePipelineTests(unittest.TestCase):
 def test_manifest_share_filter(self):
  root=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot();ns="{http://schemas.android.com/apk/res/android}";app=root.find("application")
  a=next(x for x in app.findall("activity") if x.attrib.get(ns+"name")==".ShareReceiverActivity")
  self.assertTrue(any(any(q.attrib.get(ns+"name")=="android.intent.action.SEND" for q in f.findall("action")) and any(d.attrib.get(ns+"mimeType")=="text/plain" for d in f.findall("data")) for f in a.findall("intent-filter")))
 def test_checksum_before_media_store(self):
  p=ANDROID/"app/src/main/java/com/vidgravity/app";self.assertIn("X-Content-SHA256",(p/"DownloadApi.kt").read_text());self.assertIn("MediaLibraryIndexer.publish",(p/"DownloadWorker.kt").read_text())
 def test_contract_is_exported(self):
  self.assertIn("AndroidShareIntakeRequest",(ROOT/"packages/contracts/src/android-share.ts").read_text());self.assertIn('export * from "./android-share";',(ROOT/"packages/contracts/src/index.ts").read_text())
 def test_all_screen_quality_regressions(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  main=(app/"MainActivity.kt").read_text()
  player=(app/"PlayerScreen.kt").read_text()
  swipe=(app/"SwipeFeedScreen.kt").read_text()
  study=(app/"StudyTokScreen.kt").read_text()
  picker=(app/"FormatPickerBottomSheet.kt").read_text()
  manifest=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot()
  ns="{http://schemas.android.com/apk/res/android}"
  activity=next(x for x in manifest.find("application").findall("activity") if x.attrib.get(ns+"name")==".MainActivity")
  self.assertIn("fun DownloadsContent",main);self.assertIn("fun LibraryContent",main)
  self.assertGreaterEqual(main.count("verticalScroll(rememberScrollState())"),4)
  self.assertIn("onCreateWithMedia",main)
  self.assertNotIn("Sticker renderer integration is not available yet",main)
  self.assertNotIn("30-second status splitting is not available yet",main)
  self.assertIn("vg_player_progress",player)
  self.assertIn("STATE_ENDED",player)
  self.assertIn("clips.filter { it.isFavorite }",swipe)
  self.assertIn("CreateDocument(\"text/csv\")",study)
  self.assertIn("verticalScroll(rememberScrollState())",study)
  self.assertIn("return ScrollView(context)",picker)
  self.assertIn("orientation|screenSize|keyboardHidden",activity.attrib.get(ns+"configChanges",""))
 def test_creator_stickers_are_reconciled_and_published_atomically(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  indexer=(app/"MediaLibraryIndexer.kt").read_text()
  api=(app/"DownloadApi.kt").read_text()
  self.assertIn("MediaStore.Images.Media.EXTERNAL_CONTENT_URI to \"Pictures/VidGravity/Stickers/\"",indexer)
  self.assertIn('MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "image/*"',indexer)
  self.assertIn("MediaStore.MediaColumns.IS_PENDING, 1",api)
  self.assertIn("MediaStore.MediaColumns.IS_PENDING, 0",api)
  self.assertIn("context.contentResolver.notifyChange(publishedUri, null)",api)
 def test_pause_resume_reaches_the_actual_byte_transfer(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  api=(app/"DownloadApi.kt").read_text()
  worker=(app/"DownloadWorker.kt").read_text()
  self.assertIn("shouldPause: () -> Boolean",api)
  self.assertIn("while (shouldPause())",api)
  self.assertIn("currentCoroutineContext().ensureActive()",api)
  self.assertIn("shouldPause = { DownloadActionReceiver.isPaused(applicationContext, workId) }",worker)
  self.assertIn("onPauseState = { paused ->",worker)
  self.assertIn('"progress" to lastTransferPercent',worker)
 def test_downloads_scroll_and_clear_history_are_real(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("private fun DownloadsContent(")
  end=main.index("private fun InfoCard",start)
  downloads=main[start:end]
  self.assertIn("Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())",downloads)
  self.assertIn("filteredWorks.forEach { work ->",downloads)
  self.assertNotIn("LazyColumn(",downloads)
  self.assertIn('getStringSet("hidden_finished_ids"',downloads)
  self.assertIn('putStringSet("hidden_finished_ids"',downloads)
  self.assertIn("This hides completed, failed, and cancelled items",downloads)
  self.assertNotIn("WorkManager.getInstance(context).pruneWork()",downloads)
 def test_creator_custom_status_clip_targets_remain_available(self):
  story=(ANDROID/"app/src/main/java/com/vidgravity/app/StoryCardScreen.kt").read_text()
  for seconds in [30,60,90,120]:
   self.assertIn(str(seconds),story)
  self.assertIn("length = it.coerceIn(1f, maxLength)",story)
 def test_studytok_draft_and_practice_progress_are_persisted(self):
  study=(ANDROID/"app/src/main/java/com/vidgravity/app/StudyTokScreen.kt").read_text()
  self.assertIn('getSharedPreferences("vidgravity_studytok_draft"',study)
  self.assertIn("readStudyTokDraft(context)",study)
  self.assertIn('.putString("transcript", transcript.take(60000))',study)
  self.assertIn('.putString("cards", serializedCards.toString())',study)
  self.assertIn('.putStringSet("known_cards"',study)
  self.assertIn('.putInt("practice_index"',study)
  self.assertIn("Clear study pack and draft",study)
 def test_share_inbox_and_library_lists_have_bounded_scroll_viewports(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  inbox_start=main.index("private fun ShareInboxContent(")
  library_start=main.index("@Composable\nprivate fun LibraryContent(",inbox_start)
  inbox=main[inbox_start:library_start]
  self.assertIn("Modifier.weight(1f).fillMaxWidth()",inbox)
  library_end=main.index("\n@Composable",library_start+20)
  library=main[library_start:library_end]
  self.assertIn("visibleMedia.forEach { item ->",library)
  self.assertIn("Column(\n                modifier = Modifier.fillMaxWidth(),",library)
  self.assertNotIn("LazyColumn(",library)
 def test_downloads_and_library_secondary_tools_are_collapsible(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("private fun DownloadsContent(")
  end=main.index("private fun InfoCard",start)
  downloads=main[start:end]
  self.assertIn("var showDownloadTools by remember { mutableStateOf(false) }",downloads)
  self.assertIn('Text(if (showDownloadTools) "Hide download tools" else "More tools · reports & history")',downloads)
  self.assertIn('Text("Copy CSV report (',downloads)
  library_start=main.index("@Composable\nprivate fun LibraryContent(")
  library_end=main.index("@Composable\nprivate fun CreateStudioContent",library_start)
  library=main[library_start:library_end]
  self.assertIn("var showLibraryTools by remember { mutableStateOf(false) }",library)
  self.assertIn('Text(if (showLibraryTools) "Hide library sharing tools" else "Library tools · share report")',library)
 def test_library_bulk_favorites_are_available_for_selected_media(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("@Composable\nprivate fun LibraryContent(")
  end=main.index("@Composable\nprivate fun CreateStudioContent",start)
  library=main[start:end]
  self.assertIn("var showBulkActions by remember { mutableStateOf(false) }",library)
  self.assertIn("MediaLibraryIndexer.setFavorite(context, item.uri, !allChosenAreFavorites)",library)
  self.assertIn('Text(if (showBulkActions) "Hide selection tools" else "Selection tools")',library)
 def test_library_storage_insights_include_type_counts_and_largest_file(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("@Composable\nprivate fun LibraryContent(")
  end=main.index("@Composable\nprivate fun CreateStudioContent",start)
  library=main[start:end]
  self.assertIn("var showStorageInsights by remember { mutableStateOf(false) }",library)
  self.assertIn('Text(if (showStorageInsights) "Hide storage insights" else "Storage insights")',library)
  self.assertIn("val largest = media.maxByOrNull { it.sizeBytes }",library)
  self.assertIn('Text("Videos  $videoCount  ·  Audio  $audioCount"',library)
 def test_library_quick_filters_include_recent_and_large_files(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("@Composable\nprivate fun LibraryContent(")
  end=main.index("@Composable\nprivate fun CreateStudioContent",start)
  library=main[start:end]
  self.assertIn('"Recent 7 days" -> item.createdAtMs >= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000',library)
  self.assertIn('"Large files" -> item.sizeBytes > 100_000_000L',library)
  self.assertIn('"Favorites", "Recent 7 days", "Large files"',library)
 def test_settings_diagnostics_probe_network_backend_storage_and_library(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  main=(app/"MainActivity.kt").read_text()
  api=(app/"DownloadApi.kt").read_text()
  manifest=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot()
  ns="{http://schemas.android.com/apk/res/android}"
  permissions={node.attrib.get(ns+"name") for node in manifest.findall("uses-permission")}
  self.assertIn("Diagnostics & self-test",main)
  self.assertIn("DownloadApi.healthCheck()",main)
  self.assertIn("MediaLibraryIndexer.list(context)",main)
  self.assertIn("StatFs(context.filesDir.absolutePath)",main)
  self.assertIn("fun healthCheck(): String",api)
  self.assertIn('open("/api/health", "GET")',api)
  self.assertIn("android.permission.ACCESS_NETWORK_STATE",permissions)
  self.assertIn("Copy diagnostics report",main)
 def test_library_search_prefill_is_scoped_to_library_not_share_inbox(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  inbox=main[main.index("private fun ShareInboxContent("):main.index("@Composable",main.index("private fun ShareInboxContent(")+1)]
  library=main[main.index("private fun LibraryContent("):main.index("@Composable",main.index("private fun LibraryContent(")+1)]
  self.assertNotIn("initialSearch",inbox)
  self.assertIn("mutableStateOf(initialSearch)",library)
  self.assertIn("LaunchedEffect(initialSearch)",library)
 def test_completed_download_opens_library_filtered_to_its_title(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('onOpenLibrary = { title -> librarySearchTarget = title; selected = VgTab.Library }',main)
  self.assertIn('onClick = { onOpenLibrary(title) }',main)
  self.assertIn('initialSearch = librarySearchTarget',main)
  self.assertIn('LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) searchQuery = initialSearch }',main)
  self.assertIn('Text("Find this download in Library")',main)
 def test_story_card_restores_per_media_editor_draft(self):
  story=(ANDROID/"app/src/main/java/com/vidgravity/app/StoryCardScreen.kt").read_text()
  self.assertIn('getSharedPreferences("vg-story-card-drafts"',story)
  self.assertIn('putFloat("${draftKey}_start", start)',story)
  self.assertIn('putFloat("${draftKey}_length", length)',story)
  self.assertIn('putString("${draftKey}_title", title)',story)
  self.assertIn('putString("${draftKey}_artist", artist)',story)
  self.assertIn('putString("${draftKey}_layout", layout)',story)
  self.assertIn('remember(media.uri)',story)
 def test_creator_status_clip_length_persists_without_clearing_draft_preference(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('draftPrefs.getInt("statusClipSeconds", 90)',main)
  self.assertIn('draftPrefs.edit().putInt("statusClipSeconds", statusClipSeconds).apply()',main)
  self.assertIn('remove("uri").remove("name").remove("size").remove("mime").remove("savedAt")',main)
  self.assertIn('listOf(30, 60, 90, 120)',main)
 def test_creator_studio_persists_and_restores_local_draft(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getSharedPreferences("vg-creator-draft", 0)',main)
  self.assertIn('draftPrefs.getString("uri", null)',main)
  self.assertIn('.putString("uri", media.uri.toString())',main)
  self.assertIn('Creator draft saved on this device',main)
  self.assertIn('Creator draft cleared from this device.',main)
  self.assertIn('takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)',main)
 def test_simple_collection_screen_supports_vertical_scrolling(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("fun SimpleCollectionScreen(")
  end=main.index("\n@Composable",start)
  screen=main[start:end]
  self.assertIn("verticalScroll(rememberScrollState())",screen)
  self.assertIn("rows.forEach { row ->",screen)
 def test_remembered_quality_guard_matches_best_available_label(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('choice.first != "Best available"',main)
  self.assertNotIn('choice.first != "Highest available"',main)
 def test_library_search_has_clear_action(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear search") }',main)
 def test_library_filters_can_be_reset_together(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('searchQuery = ""\n                filter = "All Media"\n                sizeFilter = "Any size"\n                sortBy = "Newest"',main)
 def test_download_history_search_can_be_cleared(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('IconButton(onClick = { downloadSearch = "" })',main)
 def test_download_history_filters_have_reset_action(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Clear filters")',main)
 def test_device_id_has_copy_action(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('TextButton(onClick = {',main)
  self.assertIn('androidx.compose.ui.text.AnnotatedString(deviceId)',main)
  self.assertIn('Device ID copied.',main)
 def test_diagnostics_report_has_copy_action(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Copy diagnostics report")',main)
 def test_wifi_only_setting_persists_preference(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('copy(unmeteredOnly = it)',main)
 def test_default_quality_presets_include_common_choices(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  for quality in ['"Best"', '"2160p"', '"1080p"', '"720p"', '"Audio"']:
   self.assertIn(quality,main)
 def test_creator_status_clip_lengths_are_validated(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('it in listOf(30, 60, 90, 120)',main)
 def test_creator_draft_preserves_persistable_uri_permission(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)',main)
 def test_settings_can_refresh_library_storage_usage(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Refresh library size")',main)
  self.assertIn("refreshStorageUsage()",main)
 def test_library_has_manual_refresh_with_loading_state(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Refresh library")',main)
  self.assertIn('Text("Refreshing library…")',main)
  self.assertIn("enabled = !refreshingMedia",main)
 def test_diagnostics_report_can_be_shared(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Share diagnostics report")',main)
  self.assertIn('putExtra(Intent.EXTRA_SUBJECT, "VidGravity diagnostics report")',main)
 def test_library_refresh_reindexes_off_main_thread(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn("media = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context) }",main)

 def test_empty_download_search_has_one_tap_recovery(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("private fun DownloadsContent(")
  end=main.index("private fun InfoCard",start)
  downloads=main[start:end]
  self.assertIn('if (searchTerm.isNotBlank()) "No matching downloads"',downloads)
  self.assertIn('Text("Reset download search and filters")',downloads)
  self.assertIn('downloadSearch = ""\n                            downloadFilter = "All"\n                            downloadSort = "Newest"',downloads)

 def test_storage_refresh_reports_permission_or_indexing_failure(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("private fun SettingsContent(")
  end=main.index("private fun ",start+30)
  settings=main[start:end]
  self.assertIn("var storageRefreshError by remember { mutableStateOf<String?>(null) }",settings)
  self.assertIn('storageRefreshError = "Could not refresh library size. Check storage access and try again."',settings)
  self.assertIn("storageRefreshError?.let { message ->",settings)

 def test_quality_preset_selection_confirms_saved_choice(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Toast.makeText(context, "Default quality set to ${preset.first}.", android.widget.Toast.LENGTH_SHORT).show()',main)
 def test_home_url_input_has_clear_action_and_character_limit(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('onValueChange = { onUrl(it.take(10000)) }',main)
  self.assertIn('contentDescription = "Clear URL input"',main)
  self.assertIn('${url.length}/10000 characters · Up to 10 links per batch',main)

 def test_home_clipboard_paste_reports_empty_clipboard(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Clipboard does not contain text to paste.',main)
  self.assertIn('contentDescription = "Paste URL"',main)

 def test_clipboard_suggestion_can_be_dismissed(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('clipboardSuggestion = null })',main)
  self.assertIn('contentDescription = "Dismiss clipboard suggestion"',main)

 def test_device_id_copy_confirms_success(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Device ID copied.',main)

 def test_license_key_copy_prevents_copying_blank_value(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Enter a license key first.',main)
  self.assertIn('License key copied.',main)

 def test_library_refresh_surfaces_indexing_errors(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('var mediaRefreshError by remember { mutableStateOf<String?>(null) }',main)
  self.assertIn('Could not refresh the library. Check media access and try again.',main)
  self.assertIn('mediaRefreshError?.let { message ->',main)

 def test_download_filter_search_and_sort_persist_between_visits(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getSharedPreferences("vg_downloads_ui"',main)
  self.assertIn('.putString("download_filter", downloadFilter)',main)
  self.assertIn('.putString("download_search", downloadSearch)',main)
  self.assertIn('.putString("download_sort", downloadSort)',main)

 def test_downloads_can_retry_all_retryable_filtered_failures(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Retry all available failures (${retryableFilteredWorks.size})")',main)
  self.assertIn('retryableFilteredWorks.forEach { work ->',main)
  self.assertIn('"Queued $queuedRetries of ${retryableFilteredWorks.size} retryable download(s)."',main)

 def test_library_filter_search_and_sort_persist_between_visits(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getSharedPreferences("vg_library_ui"',main)
  self.assertIn('.putString("filter", filter)',main)
  self.assertIn('.putString("size_filter", sizeFilter)',main)
  self.assertIn('.putString("sort_by", sortBy)',main)
  self.assertIn('.putString("search_query", searchQuery)',main)

 def test_library_visible_selection_preserves_other_filtered_selections(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('selectedUris = if (allVisibleSelected) selectedUris - visibleUris else selectedUris + visibleUris',main)
  self.assertIn('Text(if (visibleMedia.isNotEmpty() && visibleMedia.all { it.uri.toString() in selectedUris }) "Deselect visible" else "Select visible")',main)
  self.assertIn('Text("${selectedUris.size} item(s) selected across your library"',main)

 def test_library_empty_results_can_reset_search_and_filters(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Reset library search and filters")',main)
  self.assertIn('searchQuery = ""\n                        filter = "All Media"\n                        sizeFilter = "Any size"\n                        sortBy = "Newest"',main)

 def test_diagnostics_always_clears_running_state_after_failure(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Diagnostics could not finish:',main)
  self.assertIn('finally {\n                                diagnosticsRunning = false\n                            }',main)

 def test_library_has_explicit_clear_all_selection_action(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Clear all selected items")',main)
  self.assertIn('onClick = { selectedUris = emptySet() }',main)

 def test_download_tools_can_copy_only_failed_source_links(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Text("Copy failed source links")',main)
  self.assertIn('Copied ${links.size} failed source link(s).',main)
  self.assertIn('.filter { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }',main)

 def test_home_explains_supported_media_sources_and_access_limits(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Common sources: YouTube, Instagram, TikTok, Facebook, X, Vimeo and SoundCloud.',main)
  self.assertIn('Availability depends on the source and its access rules.',main)

 def test_wifi_only_toggle_confirms_saved_state(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Wi-Fi-only downloads enabled.',main)
  self.assertIn('Wi-Fi-only downloads disabled.',main)

 def test_auto_share_download_toggle_confirms_saved_state(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Automatic share downloads enabled.',main)
  self.assertIn('Automatic share downloads disabled.',main)

 def test_manual_storage_refresh_confirms_success(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Library size refreshed.',main)
  self.assertIn('if (storageRefreshError == null)',main)

if __name__=="__main__":unittest.main(verbosity=2)
