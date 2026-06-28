package com.realo.guard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Simple on-device settings + alert history. No cloud account. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("realo", Context.MODE_PRIVATE)

    var backend: String
        get() = sp.getString("backend", com.realo.guard.BuildConfig.DEFAULT_BACKEND)!!
        set(v) = sp.edit().putString("backend", v.trim().trimEnd('/')).apply()

    val deviceId: String
        get() {
            var d = sp.getString("device", null)
            if (d == null) { d = "and_" + UUID.randomUUID().toString().take(12); sp.edit().putString("device", d).apply() }
            return d
        }

    /** Packages the user opted in to watch. */
    var watched: Set<String>
        get() = sp.getStringSet("watched", DEFAULT_WATCHED)!!
        set(v) = sp.edit().putStringSet("watched", v).apply()

    fun toggleWatched(pkg: String, on: Boolean) {
        val s = watched.toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        watched = s
    }

    /** Recent alerts (most recent first), capped. */
    fun addAlert(app: String, verdict: String, confidence: Int, snippet: String, advice: String) {
        val arr = JSONArray(sp.getString("alerts", "[]"))
        val o = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("app", app).put("verdict", verdict).put("confidence", confidence)
            .put("snippet", snippet).put("advice", advice)
        val out = JSONArray().put(o)
        for (i in 0 until minOf(arr.length(), 49)) out.put(arr.get(i))
        sp.edit().putString("alerts", out.toString()).apply()
    }

    fun alerts(): List<Alert> {
        val arr = JSONArray(sp.getString("alerts", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Alert(o.getLong("ts"), o.optString("app"), o.optString("verdict"),
                o.optInt("confidence"), o.optString("snippet"), o.optString("advice"))
        }
    }

    fun clearAlerts() = sp.edit().remove("alerts").apply()

    companion object {
        // Sensible global default set (user can change). Package names.
        val DEFAULT_WATCHED = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca",         // Messenger
            "com.instagram.android",
            "org.thoughtcrime.securesms" // Signal
        )
    }
}

data class Alert(
    val ts: Long, val app: String, val verdict: String,
    val confidence: Int, val snippet: String, val advice: String
)
