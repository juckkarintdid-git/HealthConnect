package app.synchealth.hc.priority

import app.synchealth.hc.model.Source

/**
 * Decides what to do when a new record overlaps records this app already wrote from other
 * sources. [priority] is ordered best-first; a source missing from it ranks last.
 */
object PriorityResolver {

    data class Existing(val clientRecordId: String, val source: Source)

    data class Decision(val write: Boolean, val deleteClientRecordIds: List<String>)

    fun resolve(newSource: Source, overlapping: List<Existing>, priority: List<Source>): Decision {
        fun rank(s: Source) = priority.indexOf(s).let { if (it < 0) Int.MAX_VALUE else it }
        val others = overlapping.filter { it.source != newSource }
        if (others.any { rank(it.source) < rank(newSource) }) {
            return Decision(write = false, deleteClientRecordIds = emptyList())
        }
        val lower = others.filter { rank(it.source) > rank(newSource) }.map { it.clientRecordId }
        return Decision(write = true, deleteClientRecordIds = lower)
    }
}

/**
 * A measurement's records share a base id "<sourceId>:<epochSecond>"; each Health Connect record
 * uses "<base>:<kind>" as its clientRecordId so re-imports upsert instead of duplicating.
 */
object RecordIds {
    fun base(source: Source, epochSecond: Long) = "${source.id}:$epochSecond"

    fun record(baseId: String, kind: String) = "$baseId:$kind"

    fun sourceOf(id: String?): Source? = id?.substringBefore(':')?.let(Source::fromId)
}
