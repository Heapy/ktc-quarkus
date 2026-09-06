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

/**
 * Downloads everything a later build needs, so it can run without a network. The Maven goal walks its own
 * workspace to do this; resolving the model once per mode reaches the same artifacts, because the deployment
 * graph is what the local runtime classpath cannot cover.
 */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusGoOffline(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val modes = listOf(QuarkusBootstrap.Mode.PROD, QuarkusBootstrap.Mode.TEST, QuarkusBootstrap.Mode.DEV)
    val resolved = sortedSetOf<String>()

    for (mode in modes) {
        println("Resolving the ${mode.name} mode dependencies of '$moduleName'")
        val application = resolveApplication(
            classes = classes,
            resources = resources,
            runtimeClasspath = runtimeClasspath,
            moduleDir = moduleDir,
            outputDir = outputDir,
            moduleName = moduleName,
            settings = settings,
            mode = mode,
        )
        application.applicationModel.dependencies.mapTo(resolved) { it.toCompactCoords() }
    }

    println("${resolved.size} artifacts are available offline")
}
