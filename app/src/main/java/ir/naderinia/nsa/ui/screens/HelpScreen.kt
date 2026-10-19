package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private sealed class HelpRow {
    data class Header(val section: HelpSection) : HelpRow()
    data class Topic(val key: String, val section: HelpSection, val topic: HelpTopic) : HelpRow()
}

/**
 * «راهنمای کامل»: sections of question-style topics. Tap a topic to open it; type in the box to
 * search every question and answer. [initialSectionId] jumps straight to a section (used by the
 * «؟» links on other screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    initialSectionId: String?,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    val rows: List<HelpRow> = remember(query) {
        val q = query.trim()
        buildList {
            for (section in HelpContent.sections) {
                val topics = section.topics.mapIndexedNotNull { index, topic ->
                    if (q.isEmpty() || topic.question.contains(q) || topic.answer.contains(q) || section.title.contains(q))
                        HelpRow.Topic("${section.id}#$index", section, topic)
                    else null
                }
                if (topics.isNotEmpty()) {
                    add(HelpRow.Header(section))
                    addAll(topics)
                }
            }
        }
    }

    // Jump to the requested section once, and open its first topic.
    LaunchedEffect(initialSectionId) {
        val target = HelpContent.byId(initialSectionId) ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is HelpRow.Header && it.section.id == target.id }
        if (index >= 0) {
            opened = opened + "${target.id}#0"
            listState.scrollToItem(index + 1) // +1: the search box is the first item
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("راهنمای کامل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجو توی راهنما (مثلاً چک، قسط، اعلان)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "چیزی پیدا نشد. یه کلمه‌ی ساده‌تر امتحان کن، مثلاً «قبض» یا «پیام».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(rows, key = {
                when (it) {
                    is HelpRow.Header -> "h_${it.section.id}"
                    is HelpRow.Topic -> "t_${it.key}"
                }
            }) { row ->
                when (row) {
                    is HelpRow.Header -> SectionHeaderRow(row.section)
                    is HelpRow.Topic -> TopicCard(
                        topic = row.topic,
                        isOpen = row.key in opened || query.isNotBlank(),
                        onToggle = { opened = if (row.key in opened) opened - row.key else opened + row.key }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(section: HelpSection) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            section.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopicCard(topic: HelpTopic, isOpen: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    topic.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(if (isOpen) "−" else "+", style = MaterialTheme.typography.titleMedium)
            }
            if (isOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(topic.answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
