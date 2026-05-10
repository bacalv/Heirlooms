package digital.heirlooms.ui.main

import androidx.compose.runtime.mutableStateListOf

data class DiagEvent(
    val id: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val detail: String,
)

object DiagnosticsStore {
    val events = mutableStateListOf<DiagEvent>()

    fun log(tag: String, message: String, detail: String = "") {
        events.add(0, DiagEvent(tag = tag, message = message, detail = detail))
        if (events.size > 300) events.removeLast()
    }

    fun clear() = events.clear()
}
