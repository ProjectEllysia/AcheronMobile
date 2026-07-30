package com.ellysia.acheronmobile.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorableTypesTest {

    @Test
    fun `every registered kind resolves to itself via of()`() {
        StorableTypes.all.forEach { spec ->
            assertEquals(spec.kind, StorableTypes.of(spec.kind).kind)
        }
    }

    @Test
    fun `unknown kind falls back to a generic descriptor instead of throwing`() {
        val fallback = StorableTypes.of("kind-inexistente")
        assertEquals("kind-inexistente", fallback.kind)
        assertTrue(fallback.fields.isEmpty())
    }
}
