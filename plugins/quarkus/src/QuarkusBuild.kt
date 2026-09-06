package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

/**
 * `effectiveConfig` is declared but not read: it is the file `quarkusEffectiveConfig` writes, and taking it as an
 * input is what makes a changed system property or environment variable invalidate this task.
 */
@TaskAction
fun quarkusBuild(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    @Input effectiveConfig: Path,
    moduleName: String,
    settings: QuarkusSettings,
    nativeImage: Boolean = false,
) {
    val nativeProperties = if (nativeImage) {
        mapOf(
            "quarkus.native.enabled" to "true",
            "quarkus.native.container-build" to settings.containerBuild.toString(),
        )
    } else {
        emptyMap()
    }

    resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
    ).bootstrap(
        mode = QuarkusBootstrap.Mode.PROD,
        extraBuildProperties = nativeProperties,
    ).use { application ->
        application.buildProductionApplication()
    }
}
