package com.vidgravity.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class StudyCard(val question: String, val answer: String, val timestamp: String = "")
private data class StudyTokDraft(
    val transcript: String,
    val title: String,
    val cards: List<StudyCard>,
    val knownCards: Set<Int>,
    val practiceMode: Boolean,
    val practiceIndex: Int,
    val practiceQueue: List<Int>,
    val showAnswer: Boolean
)

private fun readStudyTokDraft(context: Context): StudyTokDraft {
    val prefs = context.getSharedPreferences("vidgravity_studytok_draft", Context.MODE_PRIVATE)
    val restoredCards = runCatching {
        val json = JSONArray(prefs.getString("cards", "[]") ?: "[]")
        (0 until json.length()).mapNotNull { index ->
            json.optJSONObject(index)?.let { item ->
                val question = item.optString("question")
                val answer = item.optString("answer")
                if (question.isNotBlank() && answer.isNotBlank())
                    StudyCard(question.take(500), answer.take(2000), item.optString("timestamp"))
                else null
            }
        }.take(20)
    }.getOrDefault(emptyList())
    val known = prefs.getStringSet("known_cards", emptySet()).orEmpty()
        .mapNotNull { it.toIntOrNull() }.filter { it in restoredCards.indices }.toSet()
    val queue = prefs.getString("practice_queue", "").orEmpty().split(",")
        .mapNotNull { it.toIntOrNull() }.filter { it in restoredCards.indices }
    return StudyTokDraft(
        transcript = prefs.getString("transcript", "").orEmpty().take(60000),
        title = prefs.getString("title", "My Study Pack").orEmpty().take(100).ifBlank { "My Study Pack" },
        cards = restoredCards,
        knownCards = known,
        practiceMode = prefs.getBoolean("practice_mode", false) && restoredCards.isNotEmpty(),
        practiceIndex = prefs.getInt("practice_index", 0).coerceAtLeast(0),
        practiceQueue = queue,
        showAnswer = prefs.getBoolean("show_answer", false)
    )
}

@Composable
fun StudyTokScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    val context = LocalContext.current
    val draft = remember(context) { readStudyTokDraft(context) }
    var transcript by remember { mutableStateOf(draft.transcript) }
    var cards by remember { mutableStateOf(draft.cards) }
    var title by remember { mutableStateOf(draft.title) }
    var practiceMode by remember { mutableStateOf(draft.practiceMode) }
    var practiceIndex by remember { mutableIntStateOf(draft.practiceIndex) }
    var practiceQueue by remember { mutableStateOf(draft.practiceQueue.ifEmpty { draft.cards.indices.toList() }) }
    var showAnswer by remember { mutableStateOf(draft.showAnswer) }
    var knownCards by remember { mutableStateOf(draft.knownCards) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transcript, title, cards, knownCards, practiceMode, practiceIndex, practiceQueue, showAnswer) {
        val prefs = context.getSharedPreferences("vidgravity_studytok_draft", Context.MODE_PRIVATE)
        val serializedCards = JSONArray().apply {
            cards.take(20).forEach { card ->
                put(JSONObject().put("question", card.question.take(500))
                    .put("answer", card.answer.take(2000)).put("timestamp", card.timestamp))
            }
        }
        prefs.edit()
            .putString("transcript", transcript.take(60000))
            .putString("title", title.take(100))
            .putString("cards", serializedCards.toString())
            .putStringSet("known_cards", knownCards.filter { it in cards.indices }.map { it.toString() }.toSet())
            .putBoolean("practice_mode", practiceMode)
            .putInt("practice_index", practiceIndex.coerceIn(0, cards.size))
            .putString("practice_queue", practiceQueue.filter { it in cards.indices }.joinToString(","))
            .putBoolean("show_answer", showAnswer)
            .apply()
    }
    val saveCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri == null || csv == null) {
            notify("CSV export cancelled.")
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
                    ?: throw IllegalStateException("Could not open the selected destination.")
            }.onSuccess { notify("StudyTok CSV saved successfully.") }
                .onFailure { notify("Could not save CSV: ${it.message ?: "try another destination"}") }
        }
    }

    fun buildCards() {
        val sentences = transcript
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().trim('-', '•', ' ') }
            .filter { it.length in 25..1200 }
        val cue = Regex("(?i)\\b(is defined as|means|refers to|is called|is known as|because|therefore|consists of|is the process of|is a method of)\\b")
        val ranked = sentences.mapIndexed { index, sentence ->
            val score = (if (cue.containsMatchIn(sentence)) 5 else 0) +
                (if (sentence.contains(Regex("(?i)\\b(definition|important|principle|formula|key|example|process|result|therefore)\\b"))) 3 else 0) +
                minOf(sentence.length / 120, 3)
            Triple(index, sentence, score)
        }.sortedByDescending { it.third }.take(20).sortedBy { it.first }
        cards = ranked.map { (_, sentence, _) ->
            val clean = sentence.trim().trimEnd('.', '!', '?')
            val match = Regex("(?i)^(.*?)(\\s+(?:is defined as|means|refers to|is called|is known as|is the process of|is a method of)\\s+)(.+)$").matchEntire(clean)
            val question = if (match != null) "What is ${match.groupValues[1].trim()}?" else {
                val prompt = clean.split(Regex("\\s+")).take(7).joinToString(" ")
                "Explain: ${prompt}${if (clean.split(Regex("\\s+")).size > 7) "…" else ""}"
            }
            val answer = if (match != null) match.groupValues[3].trim() else clean
            StudyCard(question.take(500), answer.take(2000))
        }.distinctBy { it.question.lowercase() + "|" + it.answer.lowercase() }
        if (cards.isEmpty()) notify("Add a transcript or notes with a few complete sentences first.")
        else notify("Created ${cards.size} revision cards from your text. Review them before studying.")
    }

    fun shareCards() {
        if (cards.isEmpty()) { notify("Create a study pack first."); return }
        val csv = buildString {
            append("front,back,timestamp\r\n")
            cards.forEach { card ->
                val fields = listOf(card.question, card.answer, card.timestamp).map { "\"" + it.replace("\"", "\"\"") + "\"" }
                append(fields.joinToString(",")).append("\r\n")
            }
        }
        val safeName = title.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(70).trim().ifBlank { "VidGravity StudyTok" }
        pendingCsv = csv
        saveCsvLauncher.launch("${safeName}.csv")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("StudyTok", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Turn notes into a revision pack", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Draft and practice progress autosave on this device", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("Transcript-first study workflow", fontWeight = FontWeight.SemiBold)
                Text(
                    "Paste captions, a transcript, or your own notes. VidGravity extracts likely key points locally; it does not claim to transcribe audio or call an AI service in this screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(100) },
            label = { Text("Study pack name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = transcript, onValueChange = { transcript = it.take(60000) },
            label = { Text("Paste transcript or notes") },
            placeholder = { Text("Paste captions or class notes here…") },
            minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { buildCards() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Create revision cards")
        }
        if (cards.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${cards.size} flashcards", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { shareCards() }) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Save CSV")
                }
            }
            if (!practiceMode) {
                Button(onClick = {
                    practiceMode = true
                    practiceIndex = 0
                    practiceQueue = cards.indices.toList()
                    showAnswer = false
                    knownCards = emptySet()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Text("Start flashcard practice")
                }
                cards.forEachIndexed { index, card ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CARD ${index + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text(card.question, fontWeight = FontWeight.SemiBold)
                            HorizontalDivider()
                            Text(card.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val finished = practiceIndex >= practiceQueue.size
                if (finished) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Practice complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("You marked ${knownCards.size} of ${cards.size} original cards as known.")
                            Button(onClick = {
                                practiceIndex = 0
                                practiceQueue = cards.indices.toList()
                                showAnswer = false
                                knownCards = emptySet()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Practice again") }
                            OutlinedButton(onClick = { practiceMode = false }, modifier = Modifier.fillMaxWidth()) { Text("Back to all cards") }
                        }
                    }
                } else {
                    val currentCardIndex = practiceQueue[practiceIndex]
                    val card = cards[currentCardIndex]
                    Text("CARD ${practiceIndex + 1} OF ${practiceQueue.size} · ${knownCards.size} KNOWN",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { (practiceIndex.toFloat() / practiceQueue.size.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Card(Modifier.fillMaxWidth().heightIn(min = 220.dp), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("QUESTION", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text(card.question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (showAnswer) {
                                HorizontalDivider()
                                Text("ANSWER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                Text(card.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                OutlinedButton(onClick = { showAnswer = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal answer") }
                            }
                        }
                    }
                    if (showAnswer) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                practiceQueue = practiceQueue + currentCardIndex
                                practiceIndex++
                                showAnswer = false
                            }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Review again") }
                            Button(onClick = {
                                knownCards = knownCards + currentCardIndex
                                practiceIndex++
                                showAnswer = false
                            }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("I knew it") }
                        }
                    }
                    TextButton(onClick = { practiceMode = false; showAnswer = false }, modifier = Modifier.fillMaxWidth()) { Text("Exit practice") }
                }
            }
            OutlinedButton(onClick = {
                transcript = ""
                title = "My Study Pack"
                cards = emptyList()
                practiceMode = false
                practiceIndex = 0
                practiceQueue = emptyList()
                showAnswer = false
                knownCards = emptySet()
                context.getSharedPreferences("vidgravity_studytok_draft", Context.MODE_PRIVATE).edit().clear().apply()
                notify("Study pack and saved draft cleared from this device.")
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear study pack and draft")
            }
        }
    }
}
