package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

/**
 * Writes the configuration that decides whether an augmented application is still current. `quarkusBuild` and
 * `quarkusNative` take the file as an input, so a change in it re-runs them. That is the only way system properties
 * and environment variables can reach the up-to-date check: `plugin.yaml` arguments are static references, and a
 * value an action computes is invisible to the toolchain.
 *
 * The task always runs and rewrites the file only when its content differs. It does not resolve the application
 * model, because doing so on every build costs a full dependency resolution, and the platform properties it would
 * add change only with the runtime classpath, which `quarkusBuild` already declares as an input.
 */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusEffectiveConfig(
    @Input resources: ModuleSources,
    @Output configFile: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val config = settingsConfig(
        resourceDirectories = resources.sourceDirectories,
        moduleName = moduleName,
        settings = settings,
        mode = QuarkusBootstrap.Mode.PROD,
    )

    writeIfChanged(
        configFile,
        renderProperties(config.cachingRelevantValues(settings.cachingRelevantProperties)).toByteArray(),
    )
}
