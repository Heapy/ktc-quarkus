package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EffectiveConfigTest {
    @Test
    fun `reads application properties from a resource directory`() {
        val resources = resourceDirectory("application.properties" to "quarkus.prop.overload=from-properties")

        assertEquals("from-properties", config(resources).quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `the active profile wins over the unprefixed value`() {
        val resources = resourceDirectory(
            "application.properties" to """
                quarkus.prop.overload=from-properties
                %prod.quarkus.prop.overload=from-prod
                %dev.quarkus.prop.overload=from-dev
            """.trimIndent()
        )

        assertEquals("from-prod", config(resources).quarkusValues["quarkus.prop.overload"])
        assertEquals("from-dev", config(resources, profile = "dev").quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `a parent profile supplies what the active profile does not`() {
        val resources = resourceDirectory(
            "application.properties" to """
                quarkus.config.profile.parent=base
                %base.quarkus.prop.overload=from-base
            """.trimIndent()
        )

        assertEquals("from-base", config(resources).quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `application properties can name the profile`() {
        val resources = resourceDirectory(
            "application.properties" to """
                quarkus.profile=staging
                %staging.quarkus.prop.overload=from-staging
            """.trimIndent()
        )

        val effective = config(resources)

        assertEquals("staging", effective.profile)
        assertEquals("from-staging", effective.quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `yaml wins over properties`() {
        val resources = resourceDirectory(
            "application.properties" to "quarkus.prop.overload=from-properties",
            "application.yaml" to "quarkus:\n  prop:\n    overload: from-yaml\n",
        )

        assertEquals("from-yaml", config(resources).quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `build properties win over application properties`() {
        val resources = resourceDirectory("application.properties" to "quarkus.prop.overload=from-properties")

        val effective = config(resources, buildProperties = mapOf("quarkus.prop.overload" to "from-build"))

        assertEquals("from-build", effective.quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `a system property wins over a build property`() {
        val name = "quarkus.prop.overload"
        System.setProperty(name, "from-system")
        try {
            assertEquals("from-system", config(buildProperties = mapOf(name to "from-build")).quarkusValues[name])
        } finally {
            System.clearProperty(name)
        }
    }

    @Test
    fun `forced properties win over build properties`() {
        val effective = config(
            buildProperties = mapOf("quarkus.prop.overload" to "from-build"),
            forcedProperties = mapOf("quarkus.prop.overload" to "from-forced"),
        )

        assertEquals("from-forced", effective.quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `the application name and version are defaults`() {
        val resources = resourceDirectory("application.properties" to "quarkus.application.name=from-properties")

        val effective = config(resources)

        assertEquals("from-properties", effective.quarkusValues["quarkus.application.name"])
        assertEquals("1.0.0-SNAPSHOT", effective.quarkusValues["quarkus.application.version"])
    }

    @Test
    fun `the base name comes from the build properties`() {
        assertEquals("app", config().quarkusValues[BUILD_BASE_NAME])
    }

    @Test
    fun `platform properties lose to everything else`() {
        val effective = config(
            platformProperties = mapOf("quarkus.prop.overload" to "from-platform"),
            buildProperties = mapOf("quarkus.prop.overload" to "from-build"),
        )

        assertEquals("from-build", effective.quarkusValues["quarkus.prop.overload"])
    }

    @Test
    fun `values read from application properties are not passed to augmentation`() {
        val resources = resourceDirectory("application.properties" to "quarkus.prop.overload=from-properties")

        val effective = config(resources, buildProperties = mapOf("quarkus.prop.build" to "yes"))

        assertNull(effective.buildSystemProperties["quarkus.prop.overload"])
        assertEquals("yes", effective.buildSystemProperties["quarkus.prop.build"])
    }

    @Test
    fun `the caching-relevant values default to every quarkus property`() {
        val effective = config(buildProperties = mapOf("quarkus.prop.overload" to "from-build"))

        val values = effective.cachingRelevantValues(listOf("quarkus[.].*", "platform[.]quarkus[.].*"))

        assertEquals("from-build", values["quarkus.prop.overload"])
        assertEquals("app", values[BUILD_BASE_NAME])
    }

    @Test
    fun `a narrower pattern drops the other properties`() {
        val effective = config(
            buildProperties = mapOf("quarkus.kept.value" to "yes", "quarkus.dropped.value" to "no"),
        )

        val values = effective.cachingRelevantValues(listOf("quarkus[.]kept[.].*"))

        assertEquals(mapOf("quarkus.kept.value" to "yes"), values)
    }

    @Test
    fun `an environment variable is matched under both of its names`() {
        val variable = System.getenv().keys.first { it.matches(Regex("[A-Z][A-Z0-9]*(_[A-Z0-9]+)+")) }
        val dotted = variable.lowercase().replace('_', '.')
        val effective = config()

        assertEquals(System.getenv(variable), effective.cachingRelevantValues(listOf(variable))[variable])
        assertEquals(System.getenv(variable), effective.cachingRelevantValues(listOf(dotted.replace(".", "[.]")))[dotted])
    }

    @Test
    fun `rendered properties are sorted and carry no timestamp`() {
        val rendered = renderProperties(mapOf("b" to "2", "a" to "1"))

        assertEquals("a=1\nb=2\n", rendered)
    }

    @Test
    fun `a rendered value survives the Properties load that reads it back`() {
        val path = "/Users/jos\u00e9/caf\u00e9/app-model.dat"

        val rendered = renderProperties(mapOf("path" to path))

        val loaded = Properties().apply { load(rendered.toByteArray().inputStream()) }
        assertEquals(path, loaded.getProperty("path"))
    }

    @Test
    fun `the profile follows the bootstrap mode`() {
        assertEquals("prod", quarkusProfile(emptyMap(), QuarkusBootstrap.Mode.PROD))
        assertEquals("test", quarkusProfile(emptyMap(), QuarkusBootstrap.Mode.TEST))
        assertEquals("dev", quarkusProfile(emptyMap(), QuarkusBootstrap.Mode.DEV))
    }

    @Test
    fun `a configured profile wins over the bootstrap mode`() {
        assertEquals("staging", quarkusProfile(mapOf(QUARKUS_PROFILE to "staging"), QuarkusBootstrap.Mode.PROD))
    }

    private fun config(
        resources: List<Path> = emptyList(),
        platformProperties: Map<String, String> = emptyMap(),
        buildProperties: Map<String, String> = emptyMap(),
        forcedProperties: Map<String, String> = emptyMap(),
        profile: String = "prod",
    ): EffectiveConfig =
        resolveEffectiveConfig(
            resourceDirectories = resources,
            platformProperties = platformProperties,
            buildProperties = buildProperties,
            forcedProperties = forcedProperties,
            applicationName = "app",
            applicationVersion = "1.0.0-SNAPSHOT",
            baseName = "app",
            profile = profile,
        )

    private fun resourceDirectory(vararg files: Pair<String, String>): List<Path> {
        val directory = Files.createTempDirectory("effective-config")
        directory.toFile().deleteOnExit()
        files.forEach { (name, content) -> directory.resolve(name).writeText(content) }
        return listOf(directory)
    }
}
