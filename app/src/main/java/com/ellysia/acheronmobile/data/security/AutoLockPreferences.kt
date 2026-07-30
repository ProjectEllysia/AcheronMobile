package com.ellysia.acheronmobile.data.security

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencia de autobloqueo del vault (ver S2/F4 en docs/code-review.md):
 * minutos de inactividad en segundo plano antes de bloquear automáticamente.
 * No es un dato sensible —es solo un número de minutos—, así que se guarda en
 * `SharedPreferences` normales, sin cifrar.
 *
 * También persiste [backgroundedAt] (en vez de guardarlo en memoria) para que
 * sobreviva a la recreación de la Activity en una rotación: si no,
 * `MainActivity.onCreate` en cada rotación reiniciaría el contador y una
 * rotación mientras la app está en segundo plano se saltaría el bloqueo.
 */
class AutoLockPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Minutos de inactividad antes de bloquear. 0 = inmediato. */
    var timeoutMinutes: Int
        get() = prefs.getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES)
        set(value) = prefs.edit { putInt(KEY_TIMEOUT_MINUTES, value) }

    /** Instante (epoch ms) en que la app pasó a segundo plano; 0 = ninguno pendiente. */
    var backgroundedAt: Long
        get() = prefs.getLong(KEY_BACKGROUNDED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_BACKGROUNDED_AT, value) }

    companion object {
        private const val PREFS_NAME = "acheron_autolock_prefs"
        private const val KEY_TIMEOUT_MINUTES = "timeout_minutes"
        private const val KEY_BACKGROUNDED_AT = "backgrounded_at"

        const val DEFAULT_TIMEOUT_MINUTES = 5

        /** Opciones ofrecidas en Ajustes (ver F4): inmediato, 1, 5, 15 minutos. */
        val OPTIONS = listOf(0, 1, 5, 15)
    }
}
