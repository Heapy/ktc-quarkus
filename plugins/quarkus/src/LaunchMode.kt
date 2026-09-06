package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap

private const val MODE = "quarkus.mode"

/**
 * Which dependency graph the diagnostic commands report on. `plugin.yaml` arguments are static, so the choice
 * travels as a system property, the way Maven takes `-Dmode`.
 */
internal fun launchMode(): QuarkusBootstrap.Mode =
    when (val mode = System.getProperty(MODE)?.lowercase() ?: "prod") {
        "prod" -> QuarkusBootstrap.Mode.PROD
        "test" -> QuarkusBootstrap.Mode.TEST
        "dev", "development" -> QuarkusBootstrap.Mode.DEV
        else -> error("$MODE was set to '$mode'. Choose one of 'prod', 'test' or 'dev'.")
    }

internal fun flag(name: String): Boolean = System.getProperty(name) == "true"
