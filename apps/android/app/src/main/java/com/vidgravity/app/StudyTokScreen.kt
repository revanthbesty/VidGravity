package com.vidgravity.app

import android.content.Intent
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

@Composable
fun StudyTokScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var cards by remember { mutableStateOf(emptyList<StudyCard>()) }
    var title by remember { mutableStateOf("My Study Pack") }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
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
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Export CSV")
                }
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
            OutlinedButton(onClick = { cards = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear study pack")
            }
        }
    }
}
