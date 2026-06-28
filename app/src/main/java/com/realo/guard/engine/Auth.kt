package com.realo.guard.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Email + OTP + password auth against the REALO engine. */
object Auth {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Result(val ok: Boolean, val token: String = "", val email: String = "", val error: String = "")

    private fun post(url: String, body: JSONObject): Result {
        return try {
            val req = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                val o = JSONObject(resp.body?.string() ?: "{}")
                if (o.optBoolean("ok"))
                    Result(true, o.optString("token"), o.optString("email"))
                else Result(false, error = o.optString("error", "failed"))
            }
        } catch (e: Exception) { Result(false, error = "network") }
    }

    /** Sends a 6-digit OTP to [email]. */
    fun requestOtp(backend: String, email: String): Result =
        post("$backend/api/auth/request-otp", JSONObject().put("email", email))

    /** Verifies OTP, creates the account, optionally subscribes to the newsletter. */
    fun signup(backend: String, email: String, otp: String, password: String, newsletter: Boolean): Result =
        post("$backend/api/auth/signup", JSONObject()
            .put("email", email).put("otp", otp).put("password", password).put("newsletter", newsletter))

    fun login(backend: String, email: String, password: String): Result =
        post("$backend/api/auth/login", JSONObject().put("email", email).put("password", password))
}

/** Friendly message for an error code. */
fun authError(code: String): String = when (code) {
    "bad_email" -> "That email doesn't look right."
    "send_failed" -> "Couldn't send the code — try again."
    "bad_otp" -> "Wrong or expired code."
    "weak_password" -> "Password must be at least 6 characters."
    "bad_credentials" -> "Wrong email or password."
    "network" -> "No connection — check internet."
    else -> "Something went wrong — try again."
}
