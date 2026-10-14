package ir.naderinia.nsa.data

data class ChecklistItem(val text: String, val done: Boolean)

fun parseChecklist(raw: String?): List<ChecklistItem> =
    raw?.lines()
        ?.filter { it.length > 2 && it[1] == '|' }
        ?.map { ChecklistItem(text = it.substring(2), done = it[0] == '1') }
        ?: emptyList()

fun serializeChecklist(items: List<ChecklistItem>): String? =
    items
        .filter { it.text.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n") { "${if (it.done) 1 else 0}|${it.text.replace("\n", " ").trim()}" }
