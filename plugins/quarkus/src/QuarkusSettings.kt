package io.heapy.ktc.quarkus

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface QuarkusSettings {
    val group: String get() = "io.heapy.ktc"
    val version: String get() = "1.0.0-SNAPSHOT"

    /**
     * Maven coordinates of the platform BOM that aligns the deployment-side dependency graph.
     * When null, `io.quarkus:quarkus-bom` is used with the Quarkus version found on the runtime classpath.
     */
    val platformBom: String?

    val buildProperties: Map<String, String> get() = emptyMap()

    /** Run `native-image` inside a builder container instead of requiring a local GraalVM. */
    val containerBuild: Boolean get() = true

    val run: QuarkusRunSettings
}

@Configurable
interface QuarkusRunSettings {
    /** JVM options for the launched process, inserted right after the executable. */
    val jvmArgs: List<String> get() = emptyList()

    /** System properties for the launched process, passed as `-Dkey=value`. */
    val systemProperties: Map<String, String> get() = emptyMap()

    /** Environment variables added to the environment of the launched process. */
    val environment: Map<String, String> get() = emptyMap()

    /**
     * Program arguments appended to the command line.
     * When empty, the space-separated value of the `QUARKUS_RUN_ARGS` environment variable is used.
     */
    val arguments: List<String> get() = emptyList()

    /** Working directory of the launched process, relative to the module root. */
    val workingDirectory: String?

    /**
     * Which run command to use when several extensions provide one.
     * Falls back to the `quarkus.run.target` system property.
     */
    val target: String?
}
