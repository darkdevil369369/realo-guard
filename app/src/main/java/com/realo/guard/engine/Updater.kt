package com.realo.guard.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** In-app updater: checks GitHub Releases for a newer APK and installs it (one tap). */
object Updater {
    private const val API = "https://api.github.com/repos/darkdevil369369/realo-guard/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    data class Info(val tag: String, val apkUrl: String)

    /** Returns update info if GitHub's latest release is newer than [currentVersion], else null. */
    fun check(currentVersion: String): Info? {
        return try {
            val req = Request.Builder().url(API)
                .header("Accept", "application/vnd.github+json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val o = JSONObject(resp.body?.string() ?: return null)
                val tag = o.optString("tag_name").ifBlank { return null }   // e.g. "v0.6"
                val assets = o.optJSONArray("assets") ?: return null
                var apk = ""
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) {
                        apk = a.optString("browser_download_url"); break
                    }
                }
                if (apk.isBlank()) return null
                if (isNewer(tag.removePrefix("v"), currentVersion)) Info(tag, apk) else null
            }
        } catch (e: Exception) { null }
    }

    /** dotted-version compare: returns true if [latest] > [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK to cache; returns the file or null. */
    fun download(context: Context, apkUrl: String): File? {
        return try {
            val req = Request.Builder().url(apkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val f = File(context.cacheDir, "realo-update.apk")
                resp.body?.byteStream()?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                } ?: return null
                f
            }
        } catch (e: Exception) { null }
    }

    /** Launches the system installer for the downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
