package com.ellysia.acheronmobile.data.security

/**
 * Decide si el vault debe bloquearse al volver a primer plano, según el
 * tiempo transcurrido en segundo plano y la preferencia de autobloqueo (ver
 * S2/F4 en docs/code-review.md). No conoce Android más allá de
 * `System.currentTimeMillis`; `MainActivity` es quien llama a [onBackground]
 * desde `onStop` y a [onForeground] desde `onResume`.
 */
class AutoLockController(private val preferences: AutoLockPreferences) {

    fun onBackground() {
        preferences.backgroundedAt = System.currentTimeMillis()
    }

    /** @return `true` si ha pasado más tiempo del configurado y el vault debería bloquearse. */
    fun onForeground(): Boolean {
        val since = preferences.backgroundedAt
        if (since == 0L) return false
        preferences.backgroundedAt = 0L
        val timeoutMillis = preferences.timeoutMinutes * 60_000L
        return System.currentTimeMillis() - since >= timeoutMillis
    }
}
