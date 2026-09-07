package io.heapy.ktc.quarkus

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchModeTest {
    @Test
    fun `a flag set without a value is on`() {
        withProperty("") {
            assertTrue(flag(NAME))
        }
    }

    @Test
    fun `a flag set to true is on`() {
        withProperty("true") {
            assertTrue(flag(NAME))
        }
    }

    @Test
    fun `an unset flag is off`() {
        assertFalse(flag(NAME))
    }

    @Test
    fun `a flag set to false is off`() {
        withProperty("false") {
            assertFalse(flag(NAME))
        }
    }

    private fun withProperty(value: String, block: () -> Unit) {
        System.setProperty(NAME, value)
        try {
            block()
        } finally {
            System.clearProperty(NAME)
        }
    }

    private companion object {
        const val NAME = "quarkus.test.launch-mode-flag"
    }
}
