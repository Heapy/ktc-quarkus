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
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.system.exitProcess

private const val RUN_COMMAND_HANDLER = "io.quarkus.deployment.cmd.StartDevServicesAndRunCommandHandler"
private const val RUN_COMMAND_RESULT = "io.quarkus.deployment.cmd.RunCommandActionResultBuildItem"
private const val DEV_SERVICES_RESULT = "io.quarkus.deployment.builditem.DevServicesLauncherConfigResultBuildItem"

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusRun(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Input packagedApplication: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val run = settings.run
    val target = run.target ?: System.getProperty("quarkus.run.target")

    bootstrapQuarkus(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = QuarkusBootstrap.Mode.RUN,
        targetDirectory = packagedApplication,
    ).use { application ->
        var exitCode = 0
        val consumer = Consumer<Map<String, List<*>>> { commands ->
            val command = selectCommand(commands, target)
            exitCode = launch(command, run, moduleDir)
        }

        application.createAugmentor().performCustomBuild(
            RUN_COMMAND_HANDLER,
            consumer,
            RUN_COMMAND_RESULT,
            DEV_SERVICES_RESULT,
        )

        if (exitCode != 0) {
            exitProcess(exitCode)
        }
    }
}

/**
 * Mirrors the selection the Maven and Gradle plugins do: an explicit target wins, a single command is taken as
 * is, and with exactly two the non-`java` one wins because it comes from an extension.
 */
private fun selectCommand(commands: Map<String, List<*>>, target: String?): List<*> {
    if (target != null) {
        return commands[target]
            ?: error("quarkus.run.target '$target' is not among ${commands.keys.sorted()}")
    }
    return when (commands.size) {
        0 -> error("No extension provided a run command")
        1 -> commands.values.first()
        2 -> commands.entries.first { it.key != "java" }.value
        else -> error(
            "Several extensions support running this application: ${commands.keys.sorted()}. " +
                "Choose one with the 'run.target' plugin setting."
        )
    }
}

private fun launch(command: List<*>, run: QuarkusRunSettings, moduleDir: Path): Int {
    @Suppress("UNCHECKED_CAST")
    val arguments = (command[0] as List<String>).toMutableList()
    val commandWorkingDirectory = command[1] as Path?

    arguments.addAll(1, run.jvmArgs)
    arguments.addAll(1, run.systemProperties.map { (key, value) -> "-D$key=$value" })
    arguments.addAll(programArguments(run))

    val workingDirectory = commandWorkingDirectory
        ?: run.workingDirectory?.let(moduleDir::resolve)
        ?: moduleDir

    println("Executing ${arguments.joinToString(" ")}")

    val process = ProcessBuilder(arguments)
        .directory(workingDirectory.toFile())
        .inheritIO()
        .also { it.environment().putAll(run.environment) }
        .start()

    val hook = Thread {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }
    Runtime.getRuntime().addShutdownHook(hook)
    try {
        return process.waitFor()
    } finally {
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }
}

private fun programArguments(run: QuarkusRunSettings): List<String> =
    run.arguments.ifEmpty {
        System.getenv("QUARKUS_RUN_ARGS")?.split(" ")?.filter { it.isNotEmpty() } ?: emptyList()
    }
