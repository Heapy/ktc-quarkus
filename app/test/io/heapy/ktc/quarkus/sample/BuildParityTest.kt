package io.heapy.ktc.quarkus.sample

import io.quarkus.test.junit.QuarkusTest
import org.eclipse.microprofile.config.ConfigProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** buildProperties are not in application.properties, so only the plugin can carry them into the test JVM. */
@QuarkusTest
class BuildParityTest {
    @Test
    fun `the test JVM reads the configuration the build was made with`() {
        val name = ConfigProvider.getConfig().getValue("quarkus.application.name", String::class.java)

        assertEquals("ktc-sample", name)
    }
}
