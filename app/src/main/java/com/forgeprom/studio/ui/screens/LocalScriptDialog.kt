package com.forgeprom.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.domain.LocalScript

@Composable
internal fun LocalScriptDialog(onDismiss: () -> Unit) {
    var source by remember { mutableStateOf("let subtotal = 12.5 * 3\nprint subtotal + 2") }
    var output by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشغيل سكربت حسابي محلي") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("يدعم let وprint والحساب والأقواس فقط. لا يشغّل نص المحرّر أو أوامر النظام، ولا يصل إلى الملفات أو الشبكة.")
                OutlinedTextField(source, { changed ->
                    if (changed.length <= LocalScript.MAX_SOURCE) {
                        source = changed
                        output = null
                        error = null
                    }
                }, label = { Text("السكربت الحسابي") }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 9,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                output?.let { Text(if (it.isEmpty()) "لا توجد مخرجات. استخدم print لعرض قيمة." else it.joinToString("\n")) }
            }
        },
        confirmButton = {
            Button(enabled = source.isNotBlank(), onClick = {
                output = null
                error = null
                try {
                    output = LocalScript.run(source)
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "السكربت غير صالح."
                }
            }) { Text("تشغيل محلي") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
