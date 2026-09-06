package io.heapy.ktc.quarkus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManifestTest {
    @Test
    fun `maps a main-section entry to a quoted property`() {
        val settings = settings(manifestEntries = mapOf("Implementation-Title" to "app"))

        assertEquals(
            mapOf("quarkus.package.jar.manifest.attributes.\"Implementation-Title\"" to "app"),
            manifestProperties(settings),
        )
    }

    @Test
    fun `maps a section entry to a two-part property`() {
        val settings = settings(manifestSections = mapOf("Signed" to mapOf("Digest" to "sha")))

        assertEquals(
            mapOf("quarkus.package.jar.manifest.sections.\"Signed\".\"Digest\"" to "sha"),
            manifestProperties(settings),
        )
    }

    @Test
    fun `rejects a quote in a name`() {
        val entry = assertFailsWith<IllegalArgumentException> {
            manifestProperties(settings(manifestEntries = mapOf("Bad\"Name" to "x")))
        }
        assertEquals("Manifest entry name 'Bad\"Name' is invalid: \" characters are not allowed.", entry.message)

        assertFailsWith<IllegalArgumentException> {
            manifestProperties(settings(manifestSections = mapOf("Bad\"Section" to mapOf("k" to "v"))))
        }
    }

    @Test
    fun `joins the ignored entries`() {
        val settings = settings(ignoredEntries = listOf("META-INF/first", "META-INF/second"))

        assertEquals(
            mapOf("quarkus.package.jar.user-configured-ignored-entries" to "META-INF/first,META-INF/second"),
            ignoredEntriesProperties(settings),
        )
    }

    @Test
    fun `writes nothing when there is nothing to write`() {
        assertEquals(emptyMap(), manifestProperties(settings()))
        assertEquals(emptyMap(), ignoredEntriesProperties(settings()))
    }

    @Test
    fun `the ignored entries lose to the module configuration`() {
        val effective = resolveEffectiveConfig(
            resourceDirectories = emptyList(),
            platformProperties = emptyMap(),
            buildProperties = mapOf("quarkus.package.jar.user-configured-ignored-entries" to "from-build"),
            forcedProperties = emptyMap(),
            defaultProperties = ignoredEntriesProperties(settings(ignoredEntries = listOf("from-settings"))),
            applicationName = "app",
            applicationVersion = "1.0.0",
            baseName = "app",
            profile = "prod",
        )

        assertEquals(
            "from-build",
            effective.quarkusValues["quarkus.package.jar.user-configured-ignored-entries"],
        )
    }

    @Test
    fun `manifest attributes win over the module configuration`() {
        val attribute = "quarkus.package.jar.manifest.attributes.\"Built-By\""
        val effective = resolveEffectiveConfig(
            resourceDirectories = emptyList(),
            platformProperties = emptyMap(),
            buildProperties = mapOf(attribute to "from-build"),
            forcedProperties = emptyMap(),
            taskProperties = manifestProperties(settings(manifestEntries = mapOf("Built-By" to "from-settings"))),
            applicationName = "app",
            applicationVersion = "1.0.0",
            baseName = "app",
            profile = "prod",
        )

        assertEquals("from-settings", effective.quarkusValues[attribute])
    }

    private fun settings(
        manifestEntries: Map<String, String> = emptyMap(),
        manifestSections: Map<String, Map<String, String>> = emptyMap(),
        ignoredEntries: List<String> = emptyList(),
    ): QuarkusSettings = object : QuarkusSettings {
        override val platformBom: String? = null
        override val finalName: String? = null
        override val manifestEntries = manifestEntries
        override val manifestSections = manifestSections
        override val ignoredEntries = ignoredEntries
        override val run: QuarkusRunSettings get() = error("not used")
        override val dev: QuarkusDevSettings get() = error("not used")
        override val image: QuarkusImageSettings get() = error("not used")
        override val deploy: QuarkusDeploySettings get() = error("not used")
    }
}
