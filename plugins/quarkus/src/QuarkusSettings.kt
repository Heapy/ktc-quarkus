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
}
