# Quarkus build plugin for the Kotlin Toolchain

A local [Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain) build plugin that runs Quarkus
augmentation, so a `jvm/app` module can be packaged as a Quarkus JVM application or compiled to a native
executable.

* Toolchain: `0.12.0`
* Quarkus: `3.39.2`

## Layout

```
project.yaml              # registers the plugin and the modules
app/                      # sample Quarkus application (Kotlin, REST endpoint)
lib/                      # local module the application depends on
plugins/quarkus/          # the build plugin
```

## Usage

Register the plugin once in `project.yaml`:

```yaml
modules:
  - app
  - plugins/quarkus

plugins:
  - //plugins/quarkus
```

Enable it in any JVM module that is a Quarkus application:

```yaml
product: jvm/app

dependencies:
  - bom: io.quarkus.platform:quarkus-bom:3.39.2
  - io.quarkus:quarkus-rest
  - io.quarkus:quarkus-kotlin

plugins:
  quarkus: enabled
```

Build it:

```shell
./kotlin do quarkusBuild -m app     # JVM fast-jar
./kotlin do quarkusNative -m app    # native executable
./kotlin do quarkusRun -m app       # build, then run the packaged application
```

Output goes to `build/tasks/_app_quarkusBuild@quarkus/`:

* `quarkus-app/quarkus-run.jar` — run with `java -jar`
* `app-runner` — native executable (from `quarkusNative`)

## Settings

| Setting           | Default             | Meaning                                                                 |
|-------------------|---------------------|-------------------------------------------------------------------------|
| `group`           | `io.heapy.ktc`      | Group ID of the synthetic application artifact                          |
| `version`         | `1.0.0-SNAPSHOT`    | Version of the application, also `quarkus.application.version`          |
| `platformBom`     | derived             | `groupId:artifactId:version` of the platform BOM used for the deployment graph. Defaults to `io.quarkus:quarkus-bom` at the Quarkus version found on the runtime classpath |
| `buildProperties` | empty               | Extra build-time Quarkus configuration                                  |
| `containerBuild`  | `true`              | Run `native-image` inside the Mandrel builder container                 |
| `run`             | see below           | Options for `quarkusRun`                                                |

### `run`

| Setting            | Default | Meaning                                                                                  |
|--------------------|---------|------------------------------------------------------------------------------------------|
| `jvmArgs`          | empty   | JVM options, inserted right after the executable                                          |
| `systemProperties` | empty   | Passed to the process as `-Dkey=value`                                                    |
| `environment`      | empty   | Added to the environment of the process                                                   |
| `arguments`        | empty   | Program arguments. When empty, the `QUARKUS_RUN_ARGS` environment variable is split on spaces |
| `workingDirectory` | module root | Working directory, relative to the module root. An extension that names its own directory wins |
| `target`           | derived | Which run command to use when several extensions provide one. Falls back to the `quarkus.run.target` system property |

```yaml
plugins:
  quarkus:
    enabled: true
    containerBuild: false
    buildProperties:
      quarkus.package.jar.type: uber-jar
```

## How it works

1. Reads the module JAR (`${module.jar}`) and unpacks it into the task output directory. That directory becomes
   the Quarkus application root.
2. Reads `${module.runtimeClasspath}` and recovers Maven coordinates for every JAR from its position in the
   Maven repository layout plus the group ID in the sibling POM.
3. Unpacks every local module JAR into its own directory and adds it as another root of the application, so beans
   declared in a module the application depends on are discovered and its classes are packaged with the
   application. Maven dependencies of those modules are already on the flattened runtime classpath.
4. Builds a Quarkus `WorkspaceModule` from that and resolves an `ApplicationModel` with
   `BootstrapAppModelResolver`. No `pom.xml` is generated and no Maven or Gradle process is started.
5. Runs `QuarkusBootstrap` in `PROD` mode and calls `createProductionApplication()`. For `quarkusNative` it sets
   `quarkus.native.enabled` and `quarkus.native.container-build`.

`quarkusRun` runs `QuarkusBootstrap` in `RUN` mode instead and asks the extensions for a launch command through
`StartDevServicesAndRunCommandHandler`. Dev Services start as part of that build, and their configuration is
injected into the launch command. The task reads the packaged application produced by `quarkusBuild`, so the
toolchain runs `quarkusBuild` first. `Ctrl-C` stops the launched process.

Deployment-time artifacts are resolved by Quarkus itself into the regular local Maven repository
(`~/.m2/repository`).

## Limitations

* A classpath entry that is neither a Maven artifact nor a module JAR is reported and skipped.
* Native builds need Docker or Podman, unless `containerBuild` is set to `false` and a local GraalVM is on
  `PATH`.
* The toolchain ignores Maven dependency exclusions, so the plugin classpath mixes maven-resolver 2.x with a
  1.9.x-era wiring layer. The plugin turns off the two remote repository filters that break under that mix.

## Notes

* The plugin pins `quarkus-bootstrap-core` in `plugins/quarkus/module.yaml`. Keep that version equal to the
  Quarkus version the application depends on. The plugin prints a warning when the two differ.
* With `containerBuild: true` (the default) on macOS or Windows, the native executable is a Linux binary. Run it
  in a container or on a Linux host.

### A native binary for the host platform

Set `containerBuild: false` and point Quarkus at a local GraalVM or Mandrel:

```yaml
plugins:
  quarkus:
    enabled: true
    containerBuild: false
```

```shell
GRAALVM_HOME=$(sdk home java 25.0.2.r25-mandrel) ./kotlin do quarkusNative -m app
```

Use a distribution whose JDK version matches `settings.jvm.release` of the module, which defaults to the
toolchain JDK. Verified on macOS aarch64 with Mandrel `25.0.2.r25`: `app-runner` is a Mach-O arm64 executable.
