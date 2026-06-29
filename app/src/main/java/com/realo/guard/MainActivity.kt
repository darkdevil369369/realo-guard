package com.realo.guard

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realo.guard.data.Alert
import com.realo.guard.data.Prefs
import com.realo.guard.engine.Updater
import com.realo.guard.service.ScanListenerService
import com.realo.guard.ui.RealoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    // WebView file-upload plumbing (for the Deepfake image picker in Tools)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooser: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            val uris = if (res.resultCode == RESULT_OK && data?.data != null) arrayOf(data.data!!) else null
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
        setContent {
            RealoTheme {
                val prefs = remember { Prefs(this) }
                var authed by remember { mutableStateOf(prefs.loggedIn) }
                if (authed) App(this) { authed = false } else AuthScreen(prefs) { authed = true }
            }
        }
    }

    fun openFileChooser(cb: ValueCallback<Array<Uri>>, intent: Intent) {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = cb
        try { fileChooser.launch(intent) } catch (e: Exception) { filePathCallback = null }
    }
}

private enum class Tab { GUARD, TOOLS }

@Composable
private fun App(activity: MainActivity, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tab by remember { mutableStateOf(Tab.GUARD) }
    var showAdv by remember { mutableStateOf(false) }
    var toolsHash by remember { mutableStateOf("") }   // "#deepfake" when opened from the card
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomNav(tab, onSelect = { toolsHash = ""; tab = it }, onAdvanced = { showAdv = true }, onLogout = onLogout)
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.GUARD -> GuardScreen(onOpenDeepfake = { toolsHash = "#deepfake"; tab = Tab.TOOLS })
                Tab.TOOLS -> ToolsScreen(activity, toolsHash)
            }
        }
    }
    if (showAdv) AdvancedDialog(prefs) { showAdv = false }
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

@Composable
private fun GuardScreen(onOpenDeepfake: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    var watched by remember { mutableStateOf(prefs.watched) }
    var alerts by remember { mutableStateOf(prefs.alerts()) }

    var update by remember { mutableStateOf<Updater.Info?>(null) }
    var updateMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { granted = hasAccess(ctx); alerts = prefs.alerts() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) { Updater.check(BuildConfig.VERSION_NAME) }
    }

    val brandGrad = Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(brandGrad),
                contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "REALO Guard", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                    style = LocalTextStyle.current.copy(brush = brandGrad)
                )
                Text("autopilot scam guard  •  v" + BuildConfig.VERSION_NAME,
                    color = Color(0xFF8B91B5), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))

        // ---- FLAGSHIP: Is this photo real or AI? (deepfake) ----
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF4DA0), Color(0xFF7C5CFF))))
                .clickable { onOpenDeepfake() }.padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center) { Text("✨", fontSize = 28.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Is this photo real or AI?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(3.dp))
                    Text("Catch deepfakes in 1 tap — free", color = Color(0xF5F5F0FF), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("✨ Check a photo  →", color = Color(0xFF7C5CFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        update?.let { up ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF13243A)),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("⬆️  Update available: ${up.tag}", color = Color(0xFF22D3EE),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (updateMsg.isNotBlank()) {
                        Spacer(Modifier.height(4.dp)); Text(updateMsg, color = Color(0xFF8B91B5), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        scope.launch {
                            updateMsg = "Downloading…"
                            val f = withContext(Dispatchers.IO) { Updater.download(ctx, up.apkUrl) }
                            if (f != null) { updateMsg = "Tap Install to finish."; Updater.install(ctx, f) }
                            else updateMsg = "Download failed — check connection."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Update now") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!granted) {
            // ---- FIRST-RUN: clean welcome + single CTA (no settings dump) ----
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("🛡️", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Stop scams before they reach you", fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(10.dp))
                    Bullet("Watches WhatsApp, Telegram & more — automatically")
                    Bullet("Warns you the instant a scam message arrives")
                    Bullet("Nothing is stored or shared — you stay in control")
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Enable protection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("On the next screen, turn on REALO Guard. That's it.",
                        color = Color(0xFF8B91B5), fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("REALO reads your notifications to spot scams. Keep notifications ON for protected apps. It's an aid, not a guarantee — always use your own judgment.",
                        color = Color(0xFF6B7194), fontSize = 10.sp, textAlign = TextAlign.Center,
                        lineHeight = 13.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            // ---- PROTECTED: premium dashboard ----
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE))))
                    .padding(24.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(66.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 34.sp) }
                    Spacer(Modifier.height(12.dp))
                    Text("You're Protected", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text("REALO is guarding you 24/7 — automatically.",
                        color = Color(0xCCFFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Modifier.weight(1f), "${watched.size}", "Apps guarded")
                StatTile(Modifier.weight(1f), "${prefs.blocked}", "Scams blocked")
                StatTile(Modifier.weight(1f), "24/7", "Always on")
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent scam alerts")
                Spacer(Modifier.weight(1f))
                if (alerts.isNotEmpty()) TextButton(onClick = { prefs.clearAlerts(); alerts = emptyList() }) { Text("Clear") }
            }
            if (alerts.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✅", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("No scams caught yet — that's good news.", color = Color(0xFF8B91B5), fontSize = 14.sp)
                    }
                }
            } else alerts.forEach { AlertRow(it) }
        }

        Spacer(Modifier.height(24.dp))
        if (prefs.loggedIn) {
            Text("Logged in as ${prefs.authEmail}", color = Color(0xFF8B91B5), fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        // Persistent disclaimer (legal): how it works + not a guarantee
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF12101A)),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "ⓘ REALO scans messages shown in your notifications — keep notifications ON for the apps you protect. " +
                "If an app's notifications are off or hidden, REALO can't scan it. REALO is an assistive aid, not a guarantee: " +
                "it may miss some scams or flag genuine messages. Always use your own judgment — never share OTP/passwords or send money based only on (or despite) REALO.",
                color = Color(0xFF8B91B5), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("REALO • global AI anti-scam • nothing stored",
            color = Color(0xFF8B91B5), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ToolsScreen(activity: MainActivity, hash: String = "") {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
        WebView(it).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            // identify ourselves so the web toolkit hides "Get the app" / signup-wall inside the app
            settings.userAgentString = (settings.userAgentString ?: "") + " RealoApp"
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?, cb: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    if (cb == null) return false
                    val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    activity.openFileChooser(cb, intent)
                    return true
                }
            }
            loadUrl(prefs.backend + hash)
        }
    })
}

@Composable
private fun BottomNav(tab: Tab, onSelect: (Tab) -> Unit, onAdvanced: () -> Unit, onLogout: () -> Unit) {
    Surface(color = Color(0xFF0F1220)) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // TOP row: Guard | Tools
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavPill(Modifier.weight(1f), "🛡️", "Guard", tab == Tab.GUARD) { onSelect(Tab.GUARD) }
                NavPill(Modifier.weight(1f), "🧰", "Tools", tab == Tab.TOOLS) { onSelect(Tab.TOOLS) }
            }
            // BOTTOM row: Advanced | Log out (same size)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionPill(Modifier.weight(1f), "⚙️", "Advanced", Color(0xFF1A1E30), Color(0xFFCBD0EA)) { onAdvanced() }
                ActionPill(Modifier.weight(1f), "", "Log out", Color(0xFF2A1622), Color(0xFFFF4D6D)) { onLogout() }
            }
        }
    }
}

@Composable
private fun ActionPill(modifier: Modifier, emoji: String, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji.isNotEmpty()) { Text(emoji, fontSize = 16.sp); Spacer(Modifier.width(8.dp)) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedDialog(prefs: Prefs, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var watched by remember { mutableStateOf(prefs.watched) }
    var trusted by remember { mutableStateOf(prefs.trusted) }
    var upStatus by remember { mutableStateOf("") }
    var upInfo by remember { mutableStateOf<Updater.Info?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF141728)) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("Advanced", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEEF1FF))
                Spacer(Modifier.height(14.dp))

                // --- Updates ---
                SectionTitle("App version  •  v${BuildConfig.VERSION_NAME}")
                if (upInfo == null) {
                    Button(onClick = {
                        upStatus = "Checking…"
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Updater.checkDetailed(BuildConfig.VERSION_NAME) }
                            when (r.status) {
                                "update" -> { upInfo = r.info; upStatus = "Update ${r.info?.tag} available!" }
                                "latest" -> upStatus = "✅ You're on the latest version."
                                else -> upStatus = "Couldn't check: ${r.message}"
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
                } else {
                    Button(onClick = {
                        upStatus = "Downloading…"
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Updater.download(ctx, upInfo!!.apkUrl) }
                            if (f != null) { upStatus = "Tap Install to finish."; Updater.install(ctx, f) }
                            else upStatus = "Download failed — check connection."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("⬆️ Update to ${upInfo!!.tag}") }
                }
                if (upStatus.isNotBlank()) {
                    Spacer(Modifier.height(6.dp)); Text(upStatus, color = Color(0xFF8B91B5), fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))
                SectionTitle("Apps to protect")
                WATCHABLE.forEach { (label, pkg) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = Color(0xFFEEF1FF))
                        Switch(checked = pkg in watched, onCheckedChange = { prefs.toggleWatched(pkg, it); watched = prefs.watched })
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle("Trusted senders (rarely needed)")
                OutlinedTextField(value = trusted, onValueChange = { trusted = it },
                    label = { Text("Names to never flag (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { prefs.trusted = trusted; trusted = prefs.trusted }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
private fun NavPill(modifier: Modifier, emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val grad = Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE)))
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(grad) else Modifier.background(Color(0xFF1A1E30)))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color(0xFF08101F) else Color(0xFF8B91B5))
    }
}

@Composable private fun StatTile(modifier: Modifier, value: String, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF141728)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color(0xFF22D3EE), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF8B91B5), fontSize = 11.sp)
    }
}

@Composable private fun Bullet(t: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("✓ ", color = Color(0xFF22C55E), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(t, color = Color(0xFFCBD0EA), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable private fun SectionTitle(t: String) {
    Text(t, fontSize = 12.sp, color = Color(0xFF8B91B5), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable private fun AlertRow(a: Alert) {
    val color = if (a.verdict == "SCAM") Color(0xFFFF4D6D) else Color(0xFFFFB020)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1020)),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
