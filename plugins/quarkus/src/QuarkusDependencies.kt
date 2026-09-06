package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.quarkus.bootstrap.resolver.maven.DependencyLoggingConfig
import io.quarkus.maven.dependency.DependencyFlags
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.function.Consumer

private const val VERBOSE = "quarkus.dependency.verbose"
private const val GRAPH = "quarkus.dependency.graph"
private const val RUNTIME_ONLY = "quarkus.dependency.runtime-only"
private const val FLAGS = "quarkus.dependency.flags"

/** Prints the graph the Quarkus resolver builds, deployment artifacts included. */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusDependencyTree(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val mode = launchMode()
    println("Quarkus application ${mode.name} mode build dependency tree:")

    resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = mode,
        runtimeOnly = flag(RUNTIME_ONLY),
        dependencyLogging = DependencyLoggingConfig.builder()
            .setMessageConsumer(Consumer(::println))
            .setVerbose(flag(VERBOSE))
            .setGraph(flag(GRAPH))
            .build(),
    )
}

/** The same graph, flattened and sorted. `quarkus.dependency.flags` narrows it to dependencies that carry them all. */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusDependencyList(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val mode = launchMode()
    val flags = parseDependencyFlags(System.getProperty(FLAGS))
    val verbose = flag(VERBOSE)

    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = mode,
        runtimeOnly = flags and DependencyFlags.RUNTIME_CP != 0,
    )

    val heading = buildString {
        append("Quarkus application ${mode.name} mode dependencies")
        if (flags != 0) append(" with flags ${DependencyFlags.toNames(flags)}")
        append(":")
    }
    println(heading)

    val dependencies = if (flags == 0) {
        application.applicationModel.dependencies
    } else {
        application.applicationModel.getDependencies(flags)
    }
    dependencies
        .map { dependency ->
            if (verbose) {
                "${dependency.toCompactCoords()} [${dependency.scope}] ${DependencyFlags.toNames(dependency.flags)}"
            } else {
                dependency.toCompactCoords()
            }
        }
        .sorted()
        .forEach(::println)
}

private val DEPENDENCY_FLAGS = mapOf(
    "optional" to DependencyFlags.OPTIONAL,
    "direct" to DependencyFlags.DIRECT,
    "runtime-cp" to DependencyFlags.RUNTIME_CP,
    "deployment-cp" to DependencyFlags.DEPLOYMENT_CP,
    "runtime-extension-artifact" to DependencyFlags.RUNTIME_EXTENSION_ARTIFACT,
    "workspace-module" to DependencyFlags.WORKSPACE_MODULE,
    "reloadable" to DependencyFlags.RELOADABLE,
    "top-level-runtime-extension-artifact" to DependencyFlags.TOP_LEVEL_RUNTIME_EXTENSION_ARTIFACT,
    "classloader-parent-first" to DependencyFlags.CLASSLOADER_PARENT_FIRST,
    "classloader-runner-parent-first" to DependencyFlags.CLASSLOADER_RUNNER_PARENT_FIRST,
    "classloader-lesser-priority" to DependencyFlags.CLASSLOADER_LESSER_PRIORITY,
    "compile-only" to DependencyFlags.COMPILE_ONLY,
)

internal fun parseDependencyFlags(value: String?): Int {
    if (value.isNullOrBlank()) return 0
    return value.split(",").filter { it.isNotBlank() }.fold(0) { flags, name ->
        val normalized = name.trim().lowercase().replace('_', '-')
        val flag = DEPENDENCY_FLAGS[normalized]
            ?: error("Unknown dependency flag '${name.trim()}'. Choose from ${DEPENDENCY_FLAGS.keys.sorted()}.")
        flags or flag
    }
}
