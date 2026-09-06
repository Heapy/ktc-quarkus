package io.heapy.ktc.quarkus

import io.quarkus.maven.dependency.DependencyFlags
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

/**
 * What the Maven `info` goal reports, read from the application model rather than from a `QuarkusProject`: the
 * latter needs an `ExtensionManager` that can rewrite the build file, and none exists for `module.yaml`.
 */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusInfo(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = launchMode(),
    )
    val model = application.applicationModel
    val platforms = model.platforms

    println("Application:  ${model.appArtifact.toCompactCoords()}")
    println("Quarkus:      ${application.quarkusVersion}")

    println()
    println("Imported platform BOMs:")
    platforms.importedPlatformBoms.forEach { println("    ${it.toCompactCoords()}") }

    val releases = platforms.platformReleaseInfo
    if (releases.isNotEmpty()) {
        println()
        println("Platform releases:")
        releases.forEach { println("    ${it.platformKey} ${it.stream} ${it.version}") }
    }
    if (!platforms.isAligned) {
        println()
        println("Platform BOMs are misaligned:")
        println(platforms.misalignmentReport)
    }

    println()
    println("Extensions:")
    model.getDependencies(DependencyFlags.TOP_LEVEL_RUNTIME_EXTENSION_ARTIFACT)
        .map { it.toCompactCoords() }
        .sorted()
        .forEach { println("    $it") }

    val transitive = model.getDependencies(DependencyFlags.RUNTIME_EXTENSION_ARTIFACT)
        .filterNot { it.flags and DependencyFlags.TOP_LEVEL_RUNTIME_EXTENSION_ARTIFACT != 0 }
        .map { it.toCompactCoords() }
        .sorted()
    if (transitive.isNotEmpty()) {
        println()
        println("Extensions pulled in by other extensions:")
        transitive.forEach { println("    $it") }
    }
}
