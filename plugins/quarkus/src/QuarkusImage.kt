package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

internal const val CONTAINER_IMAGE_BUILD = "quarkus.container-image.build"
internal const val CONTAINER_IMAGE_BUILDER = "quarkus.container-image.builder"
private const val CONTAINER_IMAGE_PUSH = "quarkus.container-image.push"

private const val CONTAINER_IMAGE_EXTENSION_PREFIX = "quarkus-container-image-"

private val IMAGE_BUILDERS = listOf("docker", "podman", "jib", "buildpack", "openshift")

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusImage(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
    push: Boolean = false,
) {
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
    )

    val builder = selectImageBuilder(settings.image.builder, application.artifactIds)
    val properties = buildMap {
        put(CONTAINER_IMAGE_BUILD, "true")
        put(CONTAINER_IMAGE_BUILDER, builder)
        if (push) put(CONTAINER_IMAGE_PUSH, "true")
    }

    println("Container image builder: $builder")

    application.bootstrap(
        mode = QuarkusBootstrap.Mode.PROD,
        forcedProperties = properties,
    ).use { it.buildProductionApplication() }
}

/**
 * An explicit name wins, then the first container-image extension on the classpath, then `docker`.
 * `quarkus-openshift` brings its own builder, so the `quarkus-container-image-` prefix is not required.
 */
internal fun selectImageBuilder(configured: String?, artifactIds: Set<String>): String {
    val available = IMAGE_BUILDERS.filter {
        "$CONTAINER_IMAGE_EXTENSION_PREFIX$it" in artifactIds || "quarkus-$it" in artifactIds
    }
    val builder = configured
        ?: System.getProperty(CONTAINER_IMAGE_BUILDER)
        ?: available.firstOrNull()
        ?: IMAGE_BUILDERS.first()

    require(builder in IMAGE_BUILDERS) {
        "Unknown container image builder '$builder'. Choose one of $IMAGE_BUILDERS."
    }
    check(builder in available) {
        "Container image builder '$builder' needs the $CONTAINER_IMAGE_EXTENSION_PREFIX$builder extension. " +
            "Add it to the dependencies of the module."
    }
    return builder
}
