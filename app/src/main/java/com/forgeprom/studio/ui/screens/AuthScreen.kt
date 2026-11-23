package com.forgeprom.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PasswordVisualTransformation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.core.security.LocalAuthStore
import com.forgeprom.studio.ui.navigation.AppNavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthRoot(container: AppContainer) {
    var authenticated by remember { mutableStateOf(false) }
    var accountExists by remember { mutableStateOf<Boolean?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        accountExists = null
        loadError = null
        val result = withContext(Dispatchers.IO) { runCatching { container.auth.hasAccount() } }
        result.fold(
            onSuccess = { accountExists = it },
            onFailure = { loadError = "تعذر الوصول إلى مخزن الحساب المشفر. تحقق من إعدادات الجهاز ثم أعد المحاولة." }
        )
    }

    when {
        authenticated -> AppNavHost(container = container, onSignOut = { authenticated = false; retry++ })
        accountExists != null -> AuthScreen(
            authStore = container.auth,
            hasAccount = accountExists == true,
            onAuthenticated = { authenticated = true }
        )
        loadError != null -> Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("تعذر تحميل الحساب", style = MaterialTheme.typography.headlineSmall)
                Text(loadError.orEmpty(), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
                Button(onClick = { retry++ }) { Text("إعادة المحاولة") }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("جارٍ التحقق من الحساب…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(
    authStore: LocalAuthStore,
    hasAccount: Boolean,
    onAuthenticated: () -> Unit
) {
    var mode by remember(hasAccount) { mutableStateOf(if (hasAccount) AuthMode.SIGN_IN else AuthMode.SIGN_UP) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        emailError = null
        passwordError = null
        confirmationError = null
        error = null
        success = null
        val normalizedEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            emailError = "أدخل بريداً إلكترونياً صحيحاً."
        }
        if (password.isEmpty()) {
            passwordError = "أدخل كلمة المرور."
        } else if (mode == AuthMode.SIGN_UP && password.length < 8) {
            passwordError = "استخدم ٨ أحرف على الأقل."
        }
        if (mode == AuthMode.SIGN_UP && confirmation != password) {
            confirmationError = "كلمتا المرور غير متطابقتين."
        }
        if (emailError != null || passwordError != null || confirmationError != null) return

        busy = true
        val requestedMode = mode
        val secret = password.toCharArray()
        password = ""
        confirmation = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (requestedMode == AuthMode.SIGN_UP) authStore.createAccount(normalizedEmail, secret)
                    .map { true }
                else authStore.authenticate(normalizedEmail, secret)
            }
            secret.fill('\u0000')
            if (result.isSuccess) {
                if (result.getOrThrow()) {
                    success = if (requestedMode == AuthMode.SIGN_UP) "تم إنشاء الحساب بنجاح." else "تم تسجيل الدخول بنجاح."
                    delay(650)
                    busy = false
                    onAuthenticated()
                } else {
                    busy = false
                    error = if (hasAccount) "البريد الإلكتروني أو كلمة المرور غير صحيحة." else "لا يوجد حساب محفوظ على هذا الجهاز. أنشئ حساباً أولاً."
                }
            } else {
                busy = false
                error = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    ?: "تعذر إكمال العملية بأمان. تحقق من إعدادات الجهاز ثم أعد المحاولة."
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 22.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        if (mode == AuthMode.SIGN_UP) Icons.Outlined.PersonAdd else Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Text("Forgeprom", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    if (mode == AuthMode.SIGN_UP) "أنشئ حساباً محلياً للمتابعة" else "سجّل الدخول إلى حسابك المحلي",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                if (!hasAccount && mode == AuthMode.SIGN_UP) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            "لا يوجد حساب محفوظ بعد على هذا الجهاز. أنشئ حسابك الأول للبدء؛ بيانات الحساب لا تُرسل إلى الإنترنت.",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (hasAccount) {
                    Text(
                        "يوجد حساب واحد محفوظ على هذا الجهاز.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = { emailError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    textStyle = TextStyle(textDirection = TextDirection.Ltr)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = { passwordError?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (mode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done),
                    textStyle = TextStyle(textDirection = TextDirection.Ltr)
                )
                if (mode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it; confirmationError = null; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        isError = confirmationError != null,
                        supportingText = { confirmationError?.let { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        textStyle = TextStyle(textDirection = TextDirection.Ltr)
                    )
                }

                if (error != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                error.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            TextButton(onClick = ::submit, enabled = !busy) { Text("إعادة المحاولة") }
                        }
                    }
                }
                if (success != null) Text(
                    success.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("جارٍ التحقق…")
                    } else Text(if (mode == AuthMode.SIGN_UP) "إنشاء الحساب" else "تسجيل الدخول")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "الحساب محلي على هذا الجهاز وليس حساباً سحابياً. استخدم كلمة مرور تتذكرها؛ لا تتوفر استعادة كلمة المرور محلياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
