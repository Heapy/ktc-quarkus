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

private const val JAR_TYPE = "quarkus.package.jar.type"
private const val DEFAULT_JAR_TYPE = "fast-jar"

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusShowEffectiveConfig(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val config = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
    ).effectiveConfig(QuarkusBootstrap.Mode.PROD)

    println("Effective Quarkus configuration options:")
    config.quarkusValues.forEach { (key, value) -> println("    $key=$value") }

    println()
    println("Profile:          ${config.profile}")
    println("Quarkus JAR type: ${config.quarkusValues[JAR_TYPE] ?: DEFAULT_JAR_TYPE}")
    println("Final name:       ${config.baseName}")

    println()
    println("Configuration sources, highest ordinal first:")
    config.sourceNames.forEach { println("    $it") }
}
