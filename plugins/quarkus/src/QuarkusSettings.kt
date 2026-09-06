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

    /**
     * Base name of the runner jar and the native binary, also `quarkus.build.base-name`.
     * Defaults to the module name.
     */
    val finalName: String?

    val buildProperties: Map<String, String> get() = emptyMap()

    /** Skip augmentation without removing the plugin. Falls back to the `quarkus.build.skip` system property. */
    val skip: Boolean get() = false

    /** Extra attributes of the main section of `MANIFEST.MF`. */
    val manifestEntries: Map<String, String> get() = emptyMap()

    /** Extra `MANIFEST.MF` attributes, keyed by section name. */
    val manifestSections: Map<String, Map<String, String>> get() = emptyMap()

    /**
     * Paths that are not copied into the runner jar, as
     * `quarkus.package.jar.user-configured-ignored-entries`. A value in the module's configuration wins.
     */
    val ignoredEntries: List<String> get() = emptyList()

    /** Delete the output of the previous build before augmenting, so a changed package type leaves nothing behind. */
    val cleanupBuildOutput: Boolean get() = true

    /**
     * Anchored regular expressions over property names. Their values take part in the up-to-date check of
     * `quarkusBuild` and `quarkusNative`. A pattern that matches no property is looked up as an environment
     * variable, so a build can be keyed on one.
     */
    val cachingRelevantProperties: List<String> get() = listOf("quarkus[.].*", "platform[.]quarkus[.].*")

    /** Run `native-image` inside a builder container instead of requiring a local GraalVM. */
    val containerBuild: Boolean get() = true

    val run: QuarkusRunSettings

    val dev: QuarkusDevSettings

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
interface QuarkusDevSettings {
    /** JVM options for the dev-mode process. */
    val jvmArgs: List<String> get() = emptyList()

    /** Program arguments appended to the command line of the application. */
    val arguments: List<String> get() = emptyList()

    /** Environment variables added to the environment of the dev-mode process. */
    val environment: Map<String, String> get() = emptyMap()

    /** Working directory of the dev-mode process, relative to the module root. */
    val workingDirectory: String?

    /**
     * `true`, `false`, `client`, or a port number. Falls back to the `debug` system property.
     * Quarkus listens for a debugger on `debugPort` unless this is `false`.
     */
    val debug: String?

    /** Wait for a debugger to attach before starting. Falls back to the `suspend` system property. */
    val suspend: String?

    /** Falls back to the `debugHost` system property, then `localhost`. */
    val debugHost: String?

    /** Falls back to the `debugPort` system property, then `5005`. */
    val debugPort: String?

    /** Add `--add-opens=java.base/java.lang=ALL-UNNAMED` to the dev-mode process. */
    val openJavaLang: Boolean get() = false

    /** Java modules to add with `--add-modules`. */
    val modules: List<String> get() = emptyList()

    /** Extra arguments for the Kotlin compiler that recompiles changed sources. */
    val compilerArgs: List<String> get() = emptyList()

    /** Keep the C2 compiler enabled. Dev mode disables it by default for faster startup. */
    val forceC2: Boolean get() = false
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
