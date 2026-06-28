package com.realo.guard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.realo.guard.data.Alert
import com.realo.guard.data.Prefs
import com.realo.guard.service.ScanListenerService
import com.realo.guard.ui.RealoTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RealoTheme { Home() } }
    }
}

// Apps REALO can watch (global scam vectors). label -> package
private val WATCHABLE = listOf(
    "WhatsApp" to "com.whatsapp",
    "Telegram" to "org.telegram.messenger",
    "Messenger" to "com.facebook.orca",
    "Instagram" to "com.instagram.android",
    "Signal" to "org.thoughtcrime.securesms",
    "Snapchat" to "com.snapchat.android",
    "Discord" to "com.discord",
    "Gmail" to "com.google.android.gm",
    "Outlook" to "com.microsoft.office.outlook",
    "LinkedIn" to "com.linkedin.android",
    "X (Twitter)" to "com.twitter.android",
    "Tinder" to "com.tinder"
)

private fun hasAccess(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    val cn = ComponentName(ctx, ScanListenerService::class.java).flattenToString()
    return flat.split(":").any { it.equals(cn, true) || it.contains(ctx.packageName) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    var watched by remember { mutableStateOf(prefs.watched) }
    var alerts by remember { mutableStateOf(prefs.alerts()) }
    var backend by remember { mutableStateOf(prefs.backend) }
    var trusted by remember { mutableStateOf(prefs.trusted) }

    // refresh state when returning from settings
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { granted = hasAccess(ctx); alerts = prefs.alerts() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(buildString { append("REAL") }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("autopilot scam guard", color = Color(0xFF8B91B5), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            // STATUS card
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    if (granted) {
                        Text("🛡️  Protection is ON", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E))
                        Spacer(Modifier.height(6.dp))
                        Text("REALO is watching your chosen apps in the background. You don't need to do anything.",
                            color = Color(0xFF8B91B5), fontSize = 14.sp)
                    } else {
                        Text("⚠️  Protection is OFF", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB020))
                        Spacer(Modifier.height(6.dp))
                        Text("One-time setup: allow REALO to read notifications so it can auto-check messages for scams. Nothing is stored or shared.",
                            color = Color(0xFF8B91B5), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Turn on protection") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Apps to protect (you choose)")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    WATCHABLE.forEach { (label, pkg) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Switch(checked = pkg in watched, onCheckedChange = {
                                prefs.toggleWatched(pkg, it); watched = prefs.watched
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent scam alerts")
                Spacer(Modifier.weight(1f))
                if (alerts.isNotEmpty()) TextButton(onClick = { prefs.clearAlerts(); alerts = emptyList() }) { Text("Clear") }
            }
            if (alerts.isEmpty()) {
                Text("No scams caught yet — that's good news.", color = Color(0xFF8B91B5), fontSize = 14.sp)
            } else {
                alerts.forEach { AlertRow(it) }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Trusted senders (never flagged)")
            Text("Comma-separated names REALO should skip — e.g. your own bots, banks you trust. Example: BTCPulse, Mom",
                color = Color(0xFF8B91B5), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = trusted, onValueChange = { trusted = it },
                label = { Text("Trusted names") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { prefs.trusted = trusted; trusted = prefs.trusted },
                modifier = Modifier.fillMaxWidth()) { Text("Save trusted senders") }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Engine (backend)")
            OutlinedTextField(value = backend, onValueChange = { backend = it },
                label = { Text("REALO engine URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { prefs.backend = backend; backend = prefs.backend },
                modifier = Modifier.fillMaxWidth()) { Text("Save engine URL") }

            Spacer(Modifier.height(28.dp))
            Text("REALO • global AI anti-scam • on-device consent • nothing stored",
                color = Color(0xFF8B91B5), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun SectionTitle(t: String) {
    Text(t, fontSize = 12.sp, color = Color(0xFF8B91B5), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable private fun AlertRow(a: Alert) {
    val color = if (a.verdict == "SCAM") Color(0xFFFF4D6D) else Color(0xFFFFB020)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1020)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text("${a.verdict} · ${a.confidence}%", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(a.app + "  " + SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(a.ts)),
                    color = Color(0xFF8B91B5), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("“${a.snippet}”", color = Color(0xFFFFD0D8), fontSize = 14.sp)
            if (a.advice.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(a.advice, color = Color(0xFF8B91B5), fontSize = 13.sp)
            }
        }
    }
}
