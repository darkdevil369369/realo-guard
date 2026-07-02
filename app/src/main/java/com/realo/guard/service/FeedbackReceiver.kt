package com.realo.guard.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.realo.guard.data.Prefs
import com.realo.guard.engine.Engine
import kotlin.concurrent.thread

/** Handles the "Not a scam? Report" action on alert notifications.
 *  Sends feedback to the engine (self-healing Crowd Shield) and dismisses the alert. */
class FeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx.applicationContext)
        val snippet = intent.getStringExtra("snippet") ?: ""
        val verdict = intent.getStringExtra("verdict") ?: ""
        val conf = intent.getIntExtra("conf", 0)
        val app = intent.getStringExtra("app") ?: ""
        val nid = intent.getIntExtra("nid", -1)
        val pending = goAsync()
        thread {
            Engine.feedback(prefs.backend, prefs.deviceId, snippet, verdict, conf, app)
            pending.finish()
        }
        if (nid >= 0) ctx.getSystemService(NotificationManager::class.java)?.cancel(nid)
        Toast.makeText(ctx, "Thanks — REALO just got sharper.", Toast.LENGTH_SHORT).show()
    }
}
