package com.ellysia.acheronmobile.util

import java.security.SecureRandom

/**
 * Generador de contraseñas para el campo "password" del formulario de alta
 * (ver F2 en docs/code-review.md): sin esto, el usuario tiende a reutilizar
 * contraseñas, justo lo que un gestor de contraseñas existe para evitar.
 */
object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}?"

    /**
     * Genera una contraseña de [length] caracteres garantizando al menos uno
     * de cada conjunto de caracteres habilitado, y baraja el resultado con
     * [SecureRandom] para que las posiciones garantizadas no queden siempre
     * al principio.
     */
    fun generate(
        length: Int = 16,
        useUpper: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true,
    ): String {
        val len = length.coerceAtLeast(4)
        val pools = buildList {
            add(LOWER)
            if (useUpper) add(UPPER)
            if (useDigits) add(DIGITS)
            if (useSymbols) add(SYMBOLS)
        }
        val all = pools.joinToString("")
        val random = SecureRandom()
        val chars = CharArray(len)

        pools.forEachIndexed { i, pool -> chars[i] = pool[random.nextInt(pool.length)] }
        for (i in pools.size until len) {
            chars[i] = all[random.nextInt(all.length)]
        }
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return String(chars)
    }
}
