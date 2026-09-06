package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.smallrye.config.DefaultValuesConfigSource
import io.smallrye.config.Expressions
import io.smallrye.config.PropertiesConfigSource
import io.smallrye.config.SmallRyeConfig
import io.smallrye.config.SmallRyeConfigBuilder
import io.smallrye.config.SysPropConfigSource
import io.smallrye.config.source.yaml.YamlConfigSourceLoader
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.Properties
import java.util.function.Supplier

internal const val QUARKUS_PROFILE = "quarkus.profile"
internal const val BUILD_BASE_NAME = "quarkus.build.base-name"

private const val APPLICATION_NAME = "quarkus.application.name"
private const val APPLICATION_VERSION = "quarkus.application.version"

/**
 * Config source ordinals, mirroring `io.quarkus.gradle.tasks.EffectiveConfig`:
 * 600 forced, 400 system properties, 300 environment, 290 build properties,
 * 265/255 YAML, 260/250 `application.properties`, 0 platform properties, then default values.
 */
private const val FORCED_ORDINAL = 600
private const val BUILD_PROPERTIES_ORDINAL = 290
private const val PLATFORM_ORDINAL = 0

/**
 * `quarkus.native.builder-image` expands `${platform.quarkus.native.builder-image}`, which fails when the
 * application model carries no platform properties. Quarkus itself plants the same placeholder.
 */
private const val PLATFORM_PLACEHOLDER = "platform.quarkus.native.builder-image"

internal class EffectiveConfig(
    private val config: SmallRyeConfig,
    val profile: String,
    val baseName: String,
    propagatedSources: Set<String>,
) {
    val sourceNames: List<String> = config.configSources.map { it.name }

    /** Every `quarkus.*` value, with `${...}` left unexpanded. */
    val quarkusValues: Map<String, String> = withoutExpansion { name, _ -> name.startsWith("quarkus.") }

    /**
     * What `setBuildSystemProperties` has to carry into augmentation: the values Quarkus cannot read for itself.
     * Values that come from `application.properties`, `application.yaml` or the environment are left out, because
     * augmentation reads those sources again from the application root.
     */
    val buildSystemProperties: Properties = Properties().apply {
        putAll(
            withoutExpansion { name, source ->
                (name.startsWith("quarkus.") || name.startsWith("platform.quarkus.")) &&
                    (name.startsWith("quarkus.test.") || source in propagatedSources)
            }
        )
    }

    private fun withoutExpansion(keep: (String, String?) -> Boolean): Map<String, String> =
        Expressions.withoutExpansion(
            Supplier {
                val values = sortedMapOf<String, String>()
                for (name in config.propertyNames) {
                    val value = config.getConfigValue(name)
                    val raw = value.value ?: continue
                    if (keep(name, value.configSourceName)) {
                        values[name] = raw
                    }
                }
                values
            }
        )
}

internal fun resolveEffectiveConfig(
    resourceDirectories: List<Path>,
    platformProperties: Map<String, String>,
    buildProperties: Map<String, String>,
    forcedProperties: Map<String, String>,
    applicationName: String,
    applicationVersion: String,
    baseName: String,
    profile: String,
): EffectiveConfig {
    val forced = PropertiesConfigSource(forcedProperties, "forcedProperties", FORCED_ORDINAL)
    val build = PropertiesConfigSource(
        buildProperties + (BUILD_BASE_NAME to baseName),
        "quarkusBuildProperties",
        BUILD_PROPERTIES_ORDINAL,
    )
    val platform = PropertiesConfigSource(
        platformProperties.ifEmpty { mapOf(PLATFORM_PLACEHOLDER to "<<ignored>>") },
        "platformProperties",
        PLATFORM_ORDINAL,
    )

    val config = SmallRyeConfigBuilder()
        .forClassLoader(resourceClassLoader(resourceDirectories))
        .addDefaultInterceptors()
        .withSources(forced)
        .addSystemSources()
        .withSources(build)
        .withSources(YamlConfigSourceLoader.InFileSystem())
        .withSources(YamlConfigSourceLoader.InClassPath())
        .addPropertiesSources()
        .withSources(platform)
        .withDefaultValues(mapOf(APPLICATION_NAME to applicationName, APPLICATION_VERSION to applicationVersion))
        .withProfile(profile)
        .build()

    return EffectiveConfig(
        config = config,
        profile = profile,
        baseName = baseName,
        propagatedSources = setOf(
            forced.name,
            build.name,
            platform.name,
            SysPropConfigSource.NAME,
            DefaultValuesConfigSource.NAME,
        ),
    )
}

internal fun quarkusProfile(buildProperties: Map<String, String>, mode: QuarkusBootstrap.Mode): String =
    System.getProperty(QUARKUS_PROFILE)
        ?: System.getenv("QUARKUS_PROFILE")
        ?: buildProperties[QUARKUS_PROFILE]
        ?: defaultProfile(mode)

private fun defaultProfile(mode: QuarkusBootstrap.Mode): String = when (mode) {
    QuarkusBootstrap.Mode.DEV,
    QuarkusBootstrap.Mode.REMOTE_DEV_SERVER,
    QuarkusBootstrap.Mode.REMOTE_DEV_CLIENT -> "dev"

    QuarkusBootstrap.Mode.TEST,
    QuarkusBootstrap.Mode.CONTINUOUS_TEST -> "test"

    QuarkusBootstrap.Mode.RUN,
    QuarkusBootstrap.Mode.PROD -> "prod"
}

/**
 * `META-INF/services` is hidden because the resource directories belong to an application that has not been
 * augmented yet: the service files name implementations the config loader must not try to instantiate.
 */
private fun resourceClassLoader(directories: List<Path>): ClassLoader {
    val urls = directories.map { it.toUri().toURL() }.toTypedArray()
    return object : URLClassLoader(urls, Thread.currentThread().contextClassLoader) {
        override fun getResource(name: String): URL? =
            if (name.startsWith("META-INF/services/")) null else super.getResource(name)

        @Throws(IOException::class)
        override fun getResources(name: String): Enumeration<URL> =
            if (name.startsWith("META-INF/services/")) Collections.emptyEnumeration() else super.getResources(name)
    }
}
