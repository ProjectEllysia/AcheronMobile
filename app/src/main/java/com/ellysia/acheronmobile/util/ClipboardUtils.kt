package com.ellysia.acheronmobile.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tiempo tras el cual se borra automáticamente un secreto copiado (ver F5). */
private const val AUTO_CLEAR_MILLIS = 45_000L

/**
 * Copia [text] al portapapeles del sistema marcándolo como contenido sensible
 * (Android 13+ evita entonces mostrar su vista previa) y programa su borrado
 * automático a los 45 s si nadie ha copiado otra cosa entretanto (ver S4 y F5
 * en docs/code-review.md).
 *
 * Se usa el [ClipboardManager] de plataforma en vez de
 * `LocalClipboardManager` de Compose porque este último no expone forma de
 * fijar `ClipDescription.EXTRA_IS_SENSITIVE`.
 *
 * @return el [Job] del borrado programado, por si el llamante quiere cancelarlo.
 */
fun copySensitiveText(context: Context, scope: CoroutineScope, label: String, text: String): Job {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    manager.setPrimaryClip(clip)

    return scope.launch {
        delay(AUTO_CLEAR_MILLIS)
        // Solo borra si el portapapeles sigue teniendo ESTE valor: si el
        // usuario copió otra cosa mientras tanto, no hay que tocarla.
        val current = manager.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        if (current == text) {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
