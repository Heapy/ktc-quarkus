package io.heapy.ktc.quarkus

import io.quarkus.maven.dependency.DependencyFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DependencyFlagsTest {
    @Test
    fun `no filter is no flags`() {
        assertEquals(0, parseDependencyFlags(null))
        assertEquals(0, parseDependencyFlags("  "))
    }

    @Test
    fun `combines the named flags`() {
        assertEquals(
            DependencyFlags.DIRECT or DependencyFlags.RUNTIME_CP,
            parseDependencyFlags("direct,runtime-cp"),
        )
    }

    @Test
    fun `accepts the constant spelling`() {
        assertEquals(DependencyFlags.DEPLOYMENT_CP, parseDependencyFlags("DEPLOYMENT_CP"))
    }

    @Test
    fun `names the choices when a flag is unknown`() {
        val failure = assertFailsWith<IllegalStateException> { parseDependencyFlags("direct,nonsense") }

        assertEquals(true, failure.message?.startsWith("Unknown dependency flag 'nonsense'."))
    }
}
