package io.heapy.ktc.quarkus

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs a process in the foreground with the terminal of the task JVM, so the launched application can read stdin
 * and draw a console. The shutdown hook is what makes `Ctrl-C` reach it.
 */
internal fun runProcess(
    arguments: List<String>,
    environment: Map<String, String>,
    workingDirectory: Path,
): Int {
    println("Executing ${arguments.joinToString(" ")}")

    val process = ProcessBuilder(arguments)
        .directory(workingDirectory.toFile())
        .inheritIO()
        .also { it.environment().putAll(environment) }
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
