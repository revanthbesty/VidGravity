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
  self.assertIn('getBoolean("show_download_tools", false)',downloads)
  self.assertIn('putBoolean("show_download_tools", showDownloadTools)',downloads)
  self.assertIn('Text(if (showDownloadTools) "Hide download tools" else "More tools · reports & history")',downloads)
  self.assertIn('Text("Copy CSV report (',downloads)
  library_start=main.index("@Composable\nprivate fun LibraryContent(")
  library_end=main.index("@Composable\nprivate fun CreateStudioContent",library_start)
  library=main[library_start:library_end]
  self.assertIn('getBoolean("show_library_tools", false)',library)
  self.assertIn('putBoolean("show_library_tools", showLibraryTools)',library)
  self.assertIn('Text(if (showLibraryTools) "Hide library sharing tools" else "Library tools · share report")',library)
 def test_library_bulk_favorites_are_available_for_selected_media(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("@Composable\nprivate fun LibraryContent(")
  end=main.index("@Composable\nprivate fun CreateStudioContent",start)
  library=main[start:end]
  self.assertIn('getBoolean("show_bulk_actions", false)',library)
  self.assertIn('putBoolean("show_bulk_actions", showBulkActions)',library)
  self.assertIn("MediaLibraryIndexer.setFavorite(context, item.uri, !allChosenAreFavorites)",library)
  self.assertIn('Text(if (showBulkActions) "Hide selection tools" else "Selection tools")',library)
 def test_library_storage_insights_include_type_counts_and_largest_file(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index("@Composable\nprivate fun LibraryContent(")
  end=main.index("@Composable\nprivate fun CreateStudioContent",start)
  library=main[start:end]
  self.assertIn('getBoolean("show_storage_insights", false)',library)
  self.assertIn('putBoolean("show_storage_insights", showStorageInsights)',library)
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
  self.assertIn('LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) searchQuery = initialSearch }',library)
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
  self.assertIn('contentDescription = "Clear all pasted URLs"',main)
  self.assertIn('${url.length}/10000 characters · Up to 10 links per batch',main)

 def test_home_clipboard_paste_reports_empty_clipboard(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Clipboard does not contain text to paste.',main)
  self.assertIn('contentDescription = "Paste URLs from clipboard"',main)

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
  self.assertIn('"Queued $queuedRetries retry(s); skipped $skippedRetries duplicate or invalid item(s)."',main)
  self.assertIn("var skippedRetries = 0",main)

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
  self.assertIn('Text("${selectedLibraryItems.size} item(s) selected across your library"',main)

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

 def test_download_tools_expanded_state_persists(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getBoolean("show_download_tools", false)',main)
  self.assertIn('.putBoolean("show_download_tools", showDownloadTools)',main)

 def test_download_history_search_is_bounded(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('onValueChange = { downloadSearch = it.take(200) }',main)
  self.assertIn('${downloadSearch.length}/200 characters',main)
  self.assertIn('contentDescription = "Search download history"',main)

 def test_library_search_is_bounded_and_can_be_cleared(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('onValueChange = { searchQuery = it.take(200) }',main)
  self.assertIn('contentDescription = "Clear library search"',main)
  self.assertIn('${searchQuery.length}/200 characters',main)

 def test_library_bulk_favorites_include_selected_items_outside_current_filter(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index('if (showBulkActions) {',main.index('@Composable\nprivate fun LibraryContent('))
  end=main.index('OutlinedTextField(\n            value = searchQuery',start)
  bulk=main[start:end]
  self.assertIn('val chosenMedia = media.filter { it.uri.toString() in selectedUris }',bulk)

 def test_selected_media_share_confirms_prepared_item_count(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Prepared ${streams.size} item(s) to share.',main)

 def test_selected_bottom_navigation_tab_persists_between_launches(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('prefs.getString("last_tab", VgTab.Home.name)',main)
  self.assertIn('prefs.edit().putString("last_tab", selected.name).apply()',main)

 def test_diagnostics_report_includes_device_and_android_version(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('lines.add("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)',main)
  self.assertIn('lines.add("Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")")',main)

 def test_license_key_field_displays_input_limit(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('activationKey = it.take(160)',main)
  self.assertIn('${activationKey.length}/160 characters',main)

 def test_library_tool_panel_expansion_states_persist(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getBoolean("show_library_tools", false)',main)
  self.assertIn('getBoolean("show_bulk_actions", false)',main)
  self.assertIn('getBoolean("show_storage_insights", false)',main)
  self.assertIn('.putBoolean("show_library_tools", showLibraryTools)',main)
  self.assertIn('.putBoolean("show_bulk_actions", showBulkActions)',main)
  self.assertIn('.putBoolean("show_storage_insights", showStorageInsights)',main)

 def test_diagnostics_distinguishes_validated_internet_from_network_capability(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('NET_CAPABILITY_VALIDATED',main)
  self.assertIn('internet connection validated',main)
  self.assertIn('Android could not validate internet access',main)

 def test_bulk_retry_skips_duplicate_and_already_active_sources(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('val activeSources = activeWorks.mapNotNull',main)
  self.assertIn('!activeSources.add(source.lowercase())',main)
  self.assertIn('skipped $skippedRetries duplicate or invalid item(s).',main)

 def test_manual_library_refresh_confirms_item_count_after_success(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Library refreshed: ${media.size} item(s).',main)
  self.assertIn('if (mediaRefreshError == null)',main)

 def test_reset_library_filters_also_clears_selection(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Library filters and selection reset.',main)
  self.assertIn('selectedUris = emptySet()\n                android.widget.Toast.makeText(context, "Library filters and selection reset."',main)

 def test_diagnostics_report_has_generation_timestamp(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('lines.add("Generated: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"',main)

 def test_download_search_clear_action_has_specific_accessibility_label(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('contentDescription = "Clear download history search"',main)

 def test_bulk_favorite_and_delete_actions_use_all_selected_library_items(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  start=main.index('if (showBulkActions) {',main.index('@Composable\nprivate fun LibraryContent('))
  end=main.index('OutlinedTextField(\n            value = searchQuery',start)
  bulk=main[start:end]
  self.assertIn('val chosen = media.filter { it.uri.toString() in selectedUris }',bulk)
  self.assertIn('val chosenItems = media.filter { it.uri.toString() in selectedUris }',bulk)
  self.assertIn('Text("Delete selected (${media.count { it.uri.toString() in selectedUris }})")',bulk)
  delete_dialog=main[main.index('if (showBulkDeleteConfirm)',end):main.index('if (showBulkDeleteConfirm)',end)+250]
  self.assertIn('val chosen = media.filter { it.uri.toString() in selectedUris }',delete_dialog)

 def test_library_refresh_and_tool_panels_have_user_feedback_and_persistence(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('Library refreshed: ${media.size} item(s).',main)
  self.assertIn('.putBoolean("show_storage_insights", showStorageInsights)',main)

 def test_home_inspect_trims_and_validates_url_host(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn("val candidate = url.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()",main)
  self.assertIn('parsed != null && parsed.scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()',main)
  self.assertIn('Enter a complete URL with https:// and a valid host name.',main)

 def test_home_url_input_has_accessible_label_and_actions(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('label = { Text("Media URL or links") }',main)
  self.assertIn('contentDescription = "Clear all pasted URLs"',main)
  self.assertIn('contentDescription = "Paste URLs from clipboard"',main)

 def test_media_rename_is_bounded_and_prevents_path_input(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  indexer=(ANDROID/"app/src/main/java/com/vidgravity/app/MediaLibraryIndexer.kt").read_text()
  self.assertIn("renameText = it.take(180)",main)
  self.assertIn('replace("/", "")',main)
  self.assertIn('replace("\\\\", "")',main)
  self.assertIn('supportingText = { Text("${renameText.length}/180 characters · File paths are not allowed") }',main)
  self.assertIn("enabled = renameText.isNotBlank()",main)
  self.assertIn('replace(Regex(',indexer)
  self.assertIn('.take(140)',indexer)
  self.assertIn("context.contentResolver.notifyChange(media.uri, null)",indexer)

 def test_batch_queue_reports_partial_failures_and_saved_quality(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn("var failed = 0",main)
  self.assertIn(".onSuccess { queued++ }.onFailure { failed++ }",main)
  self.assertIn('notify("$queued of ${candidates.size} links queued using your saved quality preset; $failed failed.")',main)

 def test_creator_caption_studio_persists_platform_caption_and_hashtags(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('getString("captionPlatform", "Instagram")',main)
  self.assertIn('getString("captionText", "")',main)
  self.assertIn('getString("hashtagText", "#VidGravity #Creator")',main)
  self.assertIn('putString("captionPlatform", captionPlatform)',main)
  self.assertIn('putString("captionText", captionText.take(2200))',main)
  self.assertIn('putString("hashtagText", hashtagText.take(500))',main)

 def test_creator_caption_studio_has_platforms_templates_and_character_limits(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  for label in ("Instagram", "TikTok", "YouTube Shorts", "WhatsApp"):
   self.assertIn('"'+label+'"',main)
  for label in ("Behind the scenes", "Quick tutorial", "New creation", "Study notes"):
   self.assertIn('"'+label+'"',main)
  self.assertIn('captionText = it.take(2200)',main)
  self.assertIn('hashtagText = it.take(500)',main)
  self.assertIn('contentDescription = "Clear caption"',main)
  self.assertIn('contentDescription = "Clear hashtags"',main)

 def test_creator_caption_studio_has_emoji_hashtag_normalization_and_copy_actions(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  for emoji in ("✨", "🎬", "🔥", "📚", "💡", "❤️"):
   self.assertIn('"'+emoji+'"',main)
  self.assertIn('distinctBy { it.lowercase() }.joinToString(" ")',main)
  self.assertIn('Text("Normalize hashtags")',main)
  self.assertIn('Text("Copy caption")',main)
  self.assertIn('Text("Copy tags")',main)
  self.assertIn('Text("Copy caption + hashtags")',main)
  self.assertIn('Text("Share caption")',main)

 def test_sharing_original_media_includes_caption_without_modifying_original(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('val socialCaption = listOf(captionText.trim(), hashtagText.trim())',main)
  self.assertIn('if (socialCaption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, socialCaption)',main)
  self.assertIn('Text("The original stays safe. What do you think?")',main)

 def test_media_rename_reports_success_and_preserves_extension_case_insensitively(self):
  main=(ANDROID/"app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
  self.assertIn('endsWith(".$suffix", ignoreCase = true)',main)
  self.assertIn('if (ok) "Media renamed." else "Could not rename this media item."',main)

if __name__=="__main__":unittest.main(verbosity=2)
