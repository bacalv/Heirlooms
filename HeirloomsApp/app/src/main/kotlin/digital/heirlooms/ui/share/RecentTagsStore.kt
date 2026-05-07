package digital.heirlooms.ui.share

import android.content.Context

private const val PREFS_NAME = "heirlooms_recent_tags"
private const val KEY = "tags"
private const val MAX_SIZE = 12

class RecentTagsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, "")
            ?.split('\n')
            ?.filter { it.isNotEmpty() && isValidTag(it) }
            ?: emptyList()

    fun record(usedTags: List<String>) {
        if (usedTags.isEmpty()) return
        val existing = load()
        val updated = (usedTags.reversed() + existing)
            .distinct()
            .take(MAX_SIZE)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
    }
}
