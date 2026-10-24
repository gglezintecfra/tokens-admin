package com.keymax.tokensadmin.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copia un secreto al portapapeles marcándolo como sensible en Android 13+
 * (evita que aparezca en la vista previa del portapapeles).
 */
fun copiarAlPortapapeles(context: Context, texto: String) {
    if (texto.isEmpty()) return

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("credencial", texto)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }

    clipboard.setPrimaryClip(clip)
}
