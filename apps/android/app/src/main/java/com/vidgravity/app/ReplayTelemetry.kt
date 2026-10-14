package com.vidgravity.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Minimal local event journal for VidGravity-owned ephemeral shares.
 *
 * This is local telemetry only: it does not notify a sender or prove screenshots occurred.
 * FLAG_SECURE can deter ordinary screenshots on supported Android windows, but cannot
 * detect external cameras and is not a guarantee against every capture path.
 */
object ReplayTelemetry {
    enum class EventType { OPENED, REPLAY_BLOCKED, EXPIRED, CAPTURE_PROTECTION_ENABLED }

    data class Event(
        val eventId: String,
        val shareId: String,
        val type: EventType,
        val attempt: Int,
        val timestampMs: Long
    )

    private const val PREFS = "vg_replay_telemetry"
    private const val EVENTS_KEY = "events"
    private const val MAX_EVENTS = 200

    /** Records opening once, or logs a blocked replay when a share was already consumed. */
    @Synchronized
    fun openOrRecordReplayBlocked(context: Context, shareId: String, maxOpens: Int = 1): Boolean {
        if (!validShareId(shareId) || maxOpens !in 1..10) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "opens_" + shareId
        val opens = prefs.getInt(key, 0)
        if (opens >= maxOpens) {
            append(context, shareId, EventType.REPLAY_BLOCKED, opens + 1)
            return false
        }
        val next = opens + 1
        if (!prefs.edit().putInt(key, next).commit()) return false
        append(context, shareId, EventType.OPENED, next)
        return true
    }

    @Synchronized
    fun record(context: Context, shareId: String, type: EventType, attempt: Int = 1): Boolean {
        if (!validShareId(shareId) || attempt < 1) return false
        append(context, shareId, type, attempt)
        return true
    }

    fun events(context: Context, shareId: String? = null): List<Event> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(EVENTS_KEY, "[]") ?: "[]"
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val event = runCatching {
                    Event(
                        eventId = item.getString("eventId"),
                        shareId = item.getString("shareId"),
                        type = EventType.valueOf(item.getString("type")),
                        attempt = item.getInt("attempt"),
                        timestampMs = item.getLong("timestampMs")
                    )
                }.getOrNull() ?: continue
                if (shareId == null || event.shareId == shareId) add(event)
            }
        }
    }

    @Synchronized
    fun clear(context: Context, shareId: String): Boolean {
        if (!validShareId(shareId)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val removed = prefs.edit().remove("opens_" + shareId).commit()
        val retained = events(context).filterNot { it.shareId == shareId }
        val array = JSONArray()
        retained.forEach { array.put(it.toJson()) }
        return prefs.edit().putString(EVENTS_KEY, array.toString()).commit() && removed
    }

    private fun append(context: Context, shareId: String, type: EventType, attempt: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = events(context).toMutableList()
        list.add(Event(UUID.randomUUID().toString(), shareId, type, attempt, System.currentTimeMillis()))
        val array = JSONArray()
        list.takeLast(MAX_EVENTS).forEach { array.put(it.toJson()) }
        prefs.edit().putString(EVENTS_KEY, array.toString()).apply()
    }

    private fun Event.toJson() = JSONObject()
        .put("eventId", eventId)
        .put("shareId", shareId)
        .put("type", type.name)
        .put("attempt", attempt)
        .put("timestampMs", timestampMs)

    private fun validShareId(value: String): Boolean =
        value.length in 1..128 && value.none { it.isISOControl() }
}
