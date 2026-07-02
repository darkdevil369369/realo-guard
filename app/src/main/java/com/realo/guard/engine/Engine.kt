package com.realo.guard.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Calls the existing REALO web engine (/api/scan) — the same brain the website uses. */
object Engine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Result(
        val verdict: String, val confidence: Int,
        val advice: String, val reasons: List<String>,
        val paymentRequest: Boolean = false
    )

    /** Returns null on any failure (never crashes the listener). */
    fun scan(backend: String, device: String, text: String,
             sourceApp: String = "", sender: String = ""): Result? {
        return try {
            val body = JSONObject().put("text", text).put("device", device)
                .apply {
                    if (sourceApp.isNotBlank()) {
                        put("source", "notification"); put("app", sourceApp); put("sender", sender)
                    }
                }.toString()
                .toRequestBody(JSON)
            val req = Request.Builder().url("$backend/api/scan").post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body?.string() ?: return null)
                if (o.has("error")) return null
                val reasons = o.optJSONArray("reasons")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList()
                Result(
                    o.optString("verdict", "UNCERTAIN").uppercase(),
                    o.optInt("confidence", 0),
                    o.optString("advice", ""),
                    reasons,
                    o.optBoolean("payment_request", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Fire-and-forget user feedback ("not a scam") — makes the engine self-healing. */
    fun feedback(backend: String, device: String, snippet: String, verdict: String, conf: Int, app: String) {
        try {
            val body = JSONObject().put("kind", "false_alarm").put("snippet", snippet)
                .put("verdict", verdict).put("confidence", conf).put("app", app)
                .put("device", device).toString().toRequestBody(JSON)
            client.newCall(Request.Builder().url("$backend/api/feedback").post(body).build())
                .execute().close()
        } catch (_: Exception) {}
    }
}
