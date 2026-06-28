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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
        setContent { RealoTheme { App(this) } }
    }

    fun openFileChooser(cb: ValueCallback<Array<Uri>>, intent: Intent) {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = cb
        try { fileChooser.launch(intent) } catch (e: Exception) { filePathCallback = null }
    }
}

private enum class Tab { GUARD, TOOLS }

@Composable
private fun App(activity: MainActivity) {
    var tab by remember { mutableStateOf(Tab.GUARD) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == Tab.GUARD, onClick = { tab = Tab.GUARD },
                    icon = { Icon(Icons.Filled.Shield, null) }, label = { Text("Guard") })
                NavigationBarItem(
                    selected = tab == Tab.TOOLS, onClick = { tab = Tab.TOOLS },
                    icon = { Icon(Icons.Filled.Build, null) }, label = { Text("Tools") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.GUARD -> GuardScreen()
                Tab.TOOLS -> ToolsScreen(activity)
            }
        }
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

@Composable
private fun GuardScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    var watched by remember { mutableStateOf(prefs.watched) }
    var alerts by remember { mutableStateOf(prefs.alerts()) }
    var backend by remember { mutableStateOf(prefs.backend) }
    var trusted by remember { mutableStateOf(prefs.trusted) }

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("REALO Guard", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground)
        Text("autopilot scam guard  •  v" + BuildConfig.VERSION_NAME, color = Color(0xFF8B91B5), fontSize = 13.sp)
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

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                if (granted) {
                    Text("🛡️  Protection is ON", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    Spacer(Modifier.height(6.dp))
                    Text("REALO is watching your chosen apps in the background. You don't need to do anything.",
                        color = Color(0xFF8B91B5), fontSize = 14.sp)
                } else {
                    Text("⚠️  Protection is OFF", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB020))
                    Spacer(Modifier.height(6.dp))
                    Text("One-time setup: allow REALO to read notifications so it can auto-check messages for scams. Nothing is stored or shared.",
                        color = Color(0xFF8B91B5), fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()) { Text("Turn on protection") }
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
        if (alerts.isEmpty()) Text("No scams caught yet — that's good news.", color = Color(0xFF8B91B5), fontSize = 14.sp)
        else alerts.forEach { AlertRow(it) }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Trusted senders (optional — rarely needed)")
        OutlinedTextField(value = trusted, onValueChange = { trusted = it },
            label = { Text("Names to never flag (comma-separated)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { prefs.trusted = trusted; trusted = prefs.trusted }, modifier = Modifier.fillMaxWidth()) {
            Text("Save trusted senders")
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Engine (backend)")
        OutlinedTextField(value = backend, onValueChange = { backend = it },
            label = { Text("REALO engine URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { prefs.backend = backend; backend = prefs.backend }, modifier = Modifier.fillMaxWidth()) {
            Text("Save engine URL")
        }

        Spacer(Modifier.height(28.dp))
        Text("REALO • global AI anti-scam • on-device consent • nothing stored",
            color = Color(0xFF8B91B5), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ToolsScreen(activity: MainActivity) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
        WebView(it).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
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
            loadUrl(prefs.backend)
        }
    })
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
