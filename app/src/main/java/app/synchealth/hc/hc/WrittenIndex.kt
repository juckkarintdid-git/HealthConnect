package app.synchealth.hc.hc

import android.content.Context
import app.synchealth.hc.model.DataCategory
import java.time.Duration
import java.time.Instant

/**
 * Local list of the record groups this app wrote, as "<sourceId>:<epochSecond>" per category.
 * Lets priority checks run without Health Connect read permissions.
 */
class WrittenIndex(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("written_index", Context.MODE_PRIVATE)

    fun inWindow(category: DataCategory, center: Instant, window: Duration): List<String> =
        entries(category).filter { id ->
            val t = id.substringAfterLast(':').toLongOrNull() ?: return@filter false
            kotlin.math.abs(t - center.epochSecond) <= window.seconds
        }

    fun add(category: DataCategory, baseId: String) = update(category) { it + baseId }

    fun remove(category: DataCategory, baseIds: Collection<String>) = update(category) { it - baseIds.toSet() }

    private fun entries(category: DataCategory): Set<String> =
        prefs.getStringSet(category.id, emptySet()).orEmpty()

    private fun update(category: DataCategory, change: (Set<String>) -> Set<String>) {
        val cutoff = Instant.now().minus(Duration.ofDays(400)).epochSecond
        val next = change(entries(category)).filter {
            (it.substringAfterLast(':').toLongOrNull() ?: 0) >= cutoff
        }.toSet()
        prefs.edit().putStringSet(category.id, next).apply()
    }
}
