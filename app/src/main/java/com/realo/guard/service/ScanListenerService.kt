package com.realo.guard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.realo.guard.MainActivity
import com.realo.guard.R
import com.realo.guard.data.Prefs
import com.realo.guard.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * THE AUTOPILOT. Reads notifications from user-selected apps (WhatsApp, Telegram,
 * Instagram, Messenger, Signal, email...), sends the text to the REALO engine,
 * and warns the user if it's a scam — all with zero user action.
 */
class ScanListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val recent = object : LinkedHashMap<Int, Long>(64, .75f, true) {
        override fun removeEldestEntry(e: Map.Entry<Int, Long>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(applicationContext)
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return                 // ignore our own
        if (pkg !in prefs.watched) return              // only opted-in apps

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = (if (big.isNotBlank()) big else text).trim()
        if (body.length < 12) return                   // skip "2 new messages" etc.

        // skip trusted senders (your own bots, known contacts) — no false alarms
        if (prefs.isTrusted(title)) return

        val message = (if (title.isNotBlank()) "$title: " else "") + body

        // de-dupe identical notifications within 60s
        val key = (pkg + "|" + body).hashCode()
        val now = System.currentTimeMillis()
        val last = recent[key]
        if (last != null && now - last < 60_000) return
        recent[key] = now

        val appName = appLabel(pkg)
        scope.launch {
            val r = Engine.scan(prefs.backend, prefs.deviceId, message) ?: return@launch
            // CREDIBILITY FIRST: only alarm on CLEAR, high-confidence scams.
            // Borderline "SUSPICIOUS" never fires an alarm — a false alarm costs more
            // than a missed borderline case. (Engine precision + trusted-senders also help.)
            if (r.verdict == "SCAM" && r.confidence >= 80) {
                prefs.addAlert(appName, r.verdict, r.confidence, body.take(140), r.advice)
                warn(appName, r.verdict, r.confidence, r.advice, body.take(120))
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun warn(app: String, verdict: String, conf: Int, advice: String, snippet: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL, "Scam warnings", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "REALO autopilot scam alerts"; enableVibration(true) }
        nm.createNotificationChannel(ch)

        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val emoji = if (verdict == "SCAM") "🚨" else "⚠️"
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("$emoji $verdict in $app ($conf%)")
            .setContentText(advice.ifBlank { "This message looks like a scam." })
            .setStyle(NotificationCompat.BigTextStyle().bigText("“$snippet”\n\n$advice"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        nm.notify(snippet.hashCode(), n)
    }

    companion object { private const val CHANNEL = "realo_alerts" }
}
