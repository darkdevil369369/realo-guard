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
        val advice: String, val reasons: List<String>
    )

    /** Returns null on any failure (never crashes the listener). */
    fun scan(backend: String, device: String, text: String): Result? {
        return try {
            val body = JSONObject().put("text", text).put("device", device).toString()
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
                    reasons
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
