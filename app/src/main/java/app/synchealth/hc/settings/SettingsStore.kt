package app.synchealth.hc.settings

import android.content.Context
import app.synchealth.hc.model.DataCategory
import app.synchealth.hc.model.Source
import app.synchealth.hc.scale.LastReading
import app.synchealth.hc.scale.Sex
import app.synchealth.hc.scale.UserProfile
import java.time.Instant
import java.time.Year

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val profile: UserProfile?
        get() {
            val height = prefs.getFloat("height_cm", 0f)
            val birthYear = prefs.getInt("birth_year", 0)
            val sex = prefs.getString("sex", null)?.let { runCatching { Sex.valueOf(it) }.getOrNull() }
            if (height <= 0f || birthYear <= 0 || sex == null) return null
            return UserProfile(height.toDouble(), Year.now().value - birthYear, sex)
        }

    val birthYear: Int get() = prefs.getInt("birth_year", 0)
    val heightCm: Float get() = prefs.getFloat("height_cm", 0f)
    val sex: Sex? get() = prefs.getString("sex", null)?.let { runCatching { Sex.valueOf(it) }.getOrNull() }

    fun saveProfile(heightCm: Float, birthYear: Int, sex: Sex) {
        prefs.edit().putFloat("height_cm", heightCm).putInt("birth_year", birthYear).putString("sex", sex.name).apply()
    }

    fun priority(category: DataCategory): List<Source> {
        val saved = prefs.getString("priority_${category.id}", null)
            ?.split(',')?.mapNotNull(Source::fromId).orEmpty()
        // Keep user order, then append sources added in newer app versions.
        return (saved + category.defaultSources).distinct()
    }

    fun setPriority(category: DataCategory, order: List<Source>) {
        prefs.edit().putString("priority_${category.id}", order.joinToString(",") { it.id }).apply()
    }

    var scaleScanEnabled: Boolean
        get() = prefs.getBoolean("scale_scan", false)
        set(v) = prefs.edit().putBoolean("scale_scan", v).apply()

    /** MAC of the scale this app trusts; bound to the first scale seen (trust on first use). */
    var boundScaleAddress: String?
        get() = prefs.getString("bound_scale", null)
        set(v) = prefs.edit().putString("bound_scale", v).apply()

    var lastReading: LastReading?
        get() {
            val raw = prefs.getString("last_reading", null) ?: return null
            val p = raw.split('|')
            if (p.size != 4) return null
            return LastReading(
                Instant.ofEpochSecond(p[0].toLong()),
                Instant.ofEpochSecond(p[1].toLong()),
                p[2].toDouble(),
                p[3].toIntOrNull(),
            )
        }
        set(v) {
            val raw = v?.let { "${it.time.epochSecond}|${it.receivedAt.epochSecond}|${it.weightKg}|${it.impedanceOhm ?: ""}" }
            prefs.edit().putString("last_reading", raw).apply()
        }

    var lastStatus: String
        get() = prefs.getString("last_status", "") ?: ""
        set(v) = prefs.edit().putString("last_status", v).apply()
}
