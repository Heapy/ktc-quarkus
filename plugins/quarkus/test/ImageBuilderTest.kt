package io.heapy.ktc.quarkus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageBuilderTest {
    @Test
    fun `an explicit builder wins over the classpath`() {
        val artifacts = setOf("quarkus-container-image-jib", "quarkus-container-image-docker")

        assertEquals("jib", selectImageBuilder("jib", artifacts))
    }

    @Test
    fun `takes the builder found on the classpath`() {
        assertEquals("jib", selectImageBuilder(null, setOf("quarkus-container-image-jib")))
    }

    @Test
    fun `counts quarkus-openshift as the openshift builder`() {
        assertEquals("openshift", selectImageBuilder(null, setOf("quarkus-openshift")))
    }

    @Test
    fun `reports the missing extension when nothing is on the classpath`() {
        val failure = assertFailsWith<IllegalStateException> { selectImageBuilder(null, setOf("quarkus-rest")) }

        assertEquals(true, failure.message?.contains("quarkus-container-image-docker"))
    }

    @Test
    fun `rejects an unknown builder`() {
        assertFailsWith<IllegalArgumentException> { selectImageBuilder("containerd", setOf("quarkus-rest")) }
    }
}
