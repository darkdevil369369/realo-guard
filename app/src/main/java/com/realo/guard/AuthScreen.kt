package com.realo.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realo.guard.data.Prefs
import com.realo.guard.engine.Auth
import com.realo.guard.engine.authError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(prefs: Prefs, onAuthed: () -> Unit) {
    var mode by remember { mutableStateOf("signup") }   // "signup" | "login"
    var step by remember { mutableStateOf(1) }          // signup: 1=email, 2=otp+password
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newsletter by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val backend = prefs.backend

    fun done(r: Auth.Result) {
        if (r.ok) { prefs.authToken = r.token; prefs.authEmail = r.email; onAuthed() }
        else error = authError(r.error)
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0D17)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text("REALO", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C5CFF))
            Text("AI scam guard", color = Color(0xFF8B91B5), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))

            // mode toggle
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141728)).padding(4.dp)
            ) {
                AuthTab(Modifier.weight(1f), "Sign up", mode == "signup") { mode = "signup"; step = 1; error = "" }
                AuthTab(Modifier.weight(1f), "Log in", mode == "login") { mode = "login"; error = "" }
            }
            Spacer(Modifier.height(22.dp))

            if (mode == "login") {
                Field(email, "Email") { email = it; error = "" }
                Spacer(Modifier.height(12.dp))
                Field(password, "Password", password = true) { password = it; error = "" }
                Spacer(Modifier.height(18.dp))
                BigButton("Log in", busy) {
                    busy = true; error = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Auth.login(backend, email.trim(), password) }
                        busy = false; done(r)
                    }
                }
            } else {
                if (step == 1) {
                    Text("Create your free account. We'll email you a code to verify.",
                        color = Color(0xFF8B91B5), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Field(email, "Email") { email = it; error = "" }
                    Spacer(Modifier.height(18.dp))
                    BigButton("Send code", busy) {
                        busy = true; error = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Auth.requestOtp(backend, email.trim()) }
                            busy = false
                            if (r.ok) { step = 2; info = "Code sent to ${email.trim()}" } else error = authError(r.error)
                        }
                    }
                } else {
                    Text(info, color = Color(0xFF22D3EE), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Field(otp, "6-digit code") { otp = it; error = "" }
                    Spacer(Modifier.height(12.dp))
                    Field(password, "Set a password (6+ chars)", password = true) { password = it; error = "" }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newsletter, onCheckedChange = { newsletter = it })
                        Text("Also get Decoded's free scam-alert newsletter (unsubscribe anytime)",
                            color = Color(0xFF8B91B5), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    BigButton("Create account", busy) {
                        busy = true; error = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                Auth.signup(backend, email.trim(), otp.trim(), password, newsletter)
                            }
                            busy = false; done(r)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Change email", color = Color(0xFF8B91B5), fontSize = 13.sp,
                        modifier = Modifier.clickable { step = 1; otp = ""; error = "" })
                }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(error, color = Color(0xFFFF4D6D), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            Text("Your email is used to secure your account and send scam alerts. Nothing is sold.",
                color = Color(0xFF8B91B5), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AuthTab(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    val grad = Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Color(0xFF22D3EE)))
    Box(
        modifier.clip(RoundedCornerShape(11.dp))
            .then(if (selected) Modifier.background(grad) else Modifier)
            .clickable { onClick() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF08101F) else Color(0xFF8B91B5),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(value: String, label: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF7C5CFF), unfocusedBorderColor = Color(0xFF252A40),
            focusedTextColor = Color(0xFFEEF1FF), unfocusedTextColor = Color(0xFFEEF1FF),
            focusedLabelColor = Color(0xFF8B91B5), unfocusedLabelColor = Color(0xFF8B91B5)
        )
    )
}

@Composable
private fun BigButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(if (busy) "Please wait…" else label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
