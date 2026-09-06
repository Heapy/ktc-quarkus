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

    val image: QuarkusImageSettings

    val deploy: QuarkusDeploySettings
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

@Configurable
interface QuarkusImageSettings {
    /**
     * Which extension builds the container image: `docker`, `podman`, `jib`, `buildpack` or `openshift`.
     * Defaults to the container-image extension on the runtime classpath, then to `docker`.
     * Falls back to the `quarkus.container-image.builder` system property.
     */
    val builder: String?
}

@Configurable
interface QuarkusDeploySettings {
    /**
     * Which extension-provided deploy command to run when several extensions declare one.
     * Falls back to the `quarkus.deploy.target` system property.
     */
    val target: String?

    /**
     * Which deployer to enable when no extension declares a deploy command:
     * `kubernetes`, `minikube`, `kind`, `knative` or `openshift`.
     * Defaults to the deployer extension on the runtime classpath, then to `kubernetes`.
     */
    val deployer: String?

    /** Build the container image as part of the deployment. */
    val imageBuild: Boolean get() = false

    /** Which extension builds that image. Implies `imageBuild`. */
    val imageBuilder: String?
}
