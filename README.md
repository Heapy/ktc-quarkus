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
3. Builds a Quarkus `WorkspaceModule` from that and resolves an `ApplicationModel` with
   `BootstrapAppModelResolver`. No `pom.xml` is generated and no Maven or Gradle process is started.
4. Runs `QuarkusBootstrap` in `PROD` mode and calls `createProductionApplication()`. For `quarkusNative` it sets
   `quarkus.native.enabled` and `quarkus.native.container-build`.

Deployment-time artifacts are resolved by Quarkus itself into the regular local Maven repository
(`~/.m2/repository`).

## Limitations

* Only classpath entries that live in a Maven repository layout are passed to Quarkus. A dependency on another
  local Kotlin Toolchain module is reported and skipped.
* Native builds need Docker or Podman, unless `containerBuild` is set to `false` and a local GraalVM is on
  `PATH`.
* The toolchain ignores Maven dependency exclusions, so the plugin classpath mixes maven-resolver 2.x with a
  1.9.x-era wiring layer. The plugin turns off the two remote repository filters that break under that mix.

## Notes

* The plugin pins `quarkus-bootstrap-core` in `plugins/quarkus/module.yaml`. Keep that version equal to the
  Quarkus version the application depends on. The plugin prints a warning when the two differ.
* With `containerBuild: true` (the default) on macOS or Windows, the native executable is a Linux binary. Run it
  in a container or on a Linux host.
