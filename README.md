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

settings:
  kotlin:
    allOpen:
      enabled: true
      presets: [ quarkus ]

plugins:
  quarkus: enabled
```

The `quarkus` all-open preset is a toolchain built-in. It opens
`javax.enterprise.context.ApplicationScoped` and `RequestScoped` only, which Quarkus 3 no longer uses, so beans stay
final and Quarkus makes them proxyable during augmentation. Add the `jakarta` annotations under
`allOpen.annotations` to open them at compile time instead:

```yaml
settings:
  kotlin:
    allOpen:
      enabled: true
      presets: [ quarkus ]
      annotations: [ jakarta.enterprise.context.ApplicationScoped ]
```

Build it:

```shell
./kotlin do quarkusBuild -m app       # JVM fast-jar
./kotlin do quarkusNative -m app      # native executable
./kotlin do quarkusRun -m app         # build, then run the packaged application
./kotlin do quarkusDev -m app         # dev mode with live reload
./kotlin do quarkusImageBuild -m app  # container image
./kotlin do quarkusImagePush -m app   # container image, then push it
./kotlin do quarkusDeploy -m app      # deploy to Kubernetes, OpenShift, minikube or kind
./kotlin do quarkusShowEffectiveConfig -m app   # print the configuration the build will use
./kotlin do quarkusInfo -m app              # platform BOMs and extensions
./kotlin do quarkusDependencyTree -m app    # dependency graph, deployment artifacts included
./kotlin do quarkusDependencyList -m app    # the same graph, flat and sorted
./kotlin do quarkusDependencySbom -m app    # CycloneDX SBOM
./kotlin do quarkusGoOffline -m app         # download everything a later build needs
```

`quarkusImageBuild` needs a `quarkus-container-image-*` extension, and `quarkusDeploy` needs a deployer
extension such as `quarkus-kubernetes`.

Output goes to `build/tasks/_app_quarkusBuild@quarkus/`:

* `quarkus-app/quarkus-run.jar` — run with `java -jar`
* `app-runner` — native executable (from `quarkusNative`)

## Settings

| Setting           | Default             | Meaning                                                                 |
|-------------------|---------------------|-------------------------------------------------------------------------|
| `group`           | `io.heapy.ktc`      | Group ID of the synthetic application artifact                          |
| `version`         | `1.0.0-SNAPSHOT`    | Version of the application, also `quarkus.application.version`          |
| `platformBom`     | derived             | `groupId:artifactId:version` of the platform BOM used for the deployment graph. Defaults to `io.quarkus:quarkus-bom` at the Quarkus version found on the runtime classpath |
| `finalName`       | module name         | Base name of the runner jar and the native binary, also `quarkus.build.base-name` |
| `buildProperties` | empty               | Extra build-time Quarkus configuration. Only `quarkus.*` keys reach augmentation |
| `skip`            | `false`             | Skip augmentation without removing the plugin. Falls back to the `quarkus.build.skip` system property |
| `manifestEntries` | empty               | Extra attributes of the main section of `MANIFEST.MF`                   |
| `manifestSections`| empty               | Extra `MANIFEST.MF` attributes, keyed by section name                   |
| `ignoredEntries`  | empty               | Paths kept out of the runner jar. A value in `application.properties` wins |
| `cleanupBuildOutput` | `true`           | Delete the previous output before augmenting, so a changed package type leaves nothing behind |
| `cachingRelevantProperties` | `quarkus[.].*`, `platform[.]quarkus[.].*` | Anchored regular expressions over property names whose values take part in the up-to-date check of `quarkusBuild` and `quarkusNative`. A pattern that matches no property is looked up as an environment variable |
| `containerBuild`  | `true`              | Run `native-image` inside the Mandrel builder container                 |
| `run`             | see below           | Options for `quarkusRun`                                                |
| `dev`             | see below           | Options for `quarkusDev`                                                |
| `image`           | see below           | Options for `quarkusImageBuild` and `quarkusImagePush`                  |
| `deploy`          | see below           | Options for `quarkusDeploy`                                             |

### `run`

| Setting            | Default | Meaning                                                                                  |
|--------------------|---------|------------------------------------------------------------------------------------------|
| `jvmArgs`          | empty   | JVM options, inserted right after the executable                                          |
| `systemProperties` | empty   | Passed to the process as `-Dkey=value`                                                    |
| `environment`      | empty   | Added to the environment of the process                                                   |
| `arguments`        | empty   | Program arguments. When empty, the `QUARKUS_RUN_ARGS` environment variable is split on spaces |
| `workingDirectory` | module root | Working directory, relative to the module root. An extension that names its own directory wins |
| `target`           | derived | Which run command to use when several extensions provide one. Falls back to the `quarkus.run.target` system property |

### `dev`

| Setting            | Default     | Meaning                                                                    |
|--------------------|-------------|----------------------------------------------------------------------------|
| `jvmArgs`          | empty       | JVM options for the dev-mode process                                        |
| `arguments`        | empty       | Program arguments for the application                                       |
| `environment`      | empty       | Environment variables added to the dev-mode process                         |
| `workingDirectory` | module root | Working directory, relative to the module root                              |
| `debug`            | derived     | `true`, `false`, `client` or a port. Falls back to the `debug` system property. Quarkus listens on `debugPort` unless this is `false` |
| `suspend`          | derived     | Wait for a debugger before starting. Falls back to the `suspend` system property |
| `debugHost`        | `localhost` | Falls back to the `debugHost` system property                               |
| `debugPort`        | `5005`      | Falls back to the `debugPort` system property                               |
| `openJavaLang`     | `false`     | Add `--add-opens=java.base/java.lang=ALL-UNNAMED`                            |
| `modules`          | empty       | Java modules to add with `--add-modules`                                     |
| `compilerArgs`     | empty       | Extra arguments for the Kotlin compiler that recompiles changed sources      |
| `forceC2`          | `false`     | Keep the C2 compiler enabled. Dev mode disables it for faster startup        |

### `image`

| Setting   | Default | Meaning                                                                                          |
|-----------|---------|--------------------------------------------------------------------------------------------------|
| `builder` | derived | `docker`, `podman`, `jib`, `buildpack` or `openshift`. Defaults to the container-image extension on the runtime classpath, then `docker`. Falls back to the `quarkus.container-image.builder` system property |

### `deploy`

| Setting        | Default | Meaning                                                                                     |
|----------------|---------|-----------------------------------------------------------------------------------------------|
| `target`       | derived | Which extension-provided deploy command to run when several extensions declare one. Falls back to the `quarkus.deploy.target` system property |
| `deployer`     | derived | `kubernetes`, `minikube`, `kind`, `knative` or `openshift`. Used when no extension declares a deploy command. Defaults to the deployer extension on the runtime classpath, then `kubernetes` |
| `imageBuild`   | `false` | Build the container image as part of the deployment                                          |
| `imageBuilder` | none    | Which extension builds that image. Implies `imageBuild`                                      |

```yaml
plugins:
  quarkus:
    enabled: true
    containerBuild: false
    buildProperties:
      quarkus.package.jar.type: uber-jar
    manifestEntries:
      Built-By: ktc-quarkus
    manifestSections:
      Extras:
        Note: hello
    ignoredEntries:
      - META-INF/nothing
    image:
      builder: jib
    deploy:
      deployer: minikube
      imageBuild: true
```

## How it works

1. Takes the compiled classes (`${module.classes}`) and the resource directories (`${module.resources}`) as the
   roots of the Quarkus application. Nothing is unpacked for the module itself.
2. Reads `${module.runtimeClasspath}` and recovers Maven coordinates for every JAR from its position in the
   Maven repository layout plus the group ID in the sibling POM.
3. Unpacks every local module JAR into its own directory and adds it as another root of the application, so beans
   declared in a module the application depends on are discovered and its classes are packaged with the
   application. Maven dependencies of those modules are already on the flattened runtime classpath.
4. Builds a Quarkus `WorkspaceModule` from that and resolves an `ApplicationModel` with
   `BootstrapAppModelResolver`. No `pom.xml` is generated and no Maven or Gradle process is started.
5. Layers the effective configuration on a `SmallRyeConfig`: forced task properties, the system properties and
   environment of the toolchain JVM, `buildProperties`, the module's `application.yaml` and
   `application.properties`, and the platform properties of the resolved model.
6. Runs `QuarkusBootstrap` in `PROD` mode and calls `createProductionApplication()`. For `quarkusNative` it sets
   `quarkus.native.enabled` and `quarkus.native.container-build`.

`quarkusRun` runs `QuarkusBootstrap` in `RUN` mode instead and asks the extensions for a launch command through
`StartDevServicesAndRunCommandHandler`. Dev Services start as part of that build, and their configuration is
injected into the launch command. The task reads the packaged application produced by `quarkusBuild`, so the
toolchain runs `quarkusBuild` first. `Ctrl-C` stops the launched process.

`quarkusShowEffectiveConfig` prints the configuration that the build will use, and the sources it came from, in
descending priority: forced task properties, system properties, environment, `buildProperties`, `application.yaml`,
`application.properties`, platform properties, defaults. Pass system properties through the toolchain JVM:

```shell
KOTLIN_CLI_JAVA_OPTIONS="-Dquarkus.package.jar.type=uber-jar" ./kotlin do quarkusBuild -m app
```

`quarkusEffectiveConfig` writes those values, narrowed by `cachingRelevantProperties`, to
`effective-config.properties`. `quarkusBuild` and `quarkusNative` take that file as an input, so changing a system
property or an environment variable re-runs augmentation and repeating the same one does not. The task itself runs
on every invocation and rewrites the file only when the content differs.

`quarkusDev` resolves the model in dev mode, builds a command line with `DevModeCommandLineBuilder` and forks a JVM
that runs `DevModeMain`. The dev-mode process gets its own classpath: `quarkus-core-deployment` and
`quarkus-bootstrap-maven-resolver` at the application's Quarkus version, plus the parent-first artifacts of the
model. Dev mode takes one classes directory and one resources directory, so the module's classes and the unpacked
local modules are staged into `dev-classes` and the resources into `dev-resources` under the task output directory.
Quarkus recompiles into those, so `./kotlin build` and `./kotlin test` are unaffected by a dev session. Kotlin
sources of the module and of its local modules are recompiled and live-reloaded; the annotations of
`settings.kotlin.allOpen` are passed to that compiler.

`quarkusInfo`, `quarkusDependencyTree`, `quarkusDependencyList`, `quarkusDependencySbom` and `quarkusGoOffline`
only read the resolved model, so none of them augments the application. `quarkusInfo` reads the platform BOMs and
the extensions from the model rather than from a `QuarkusProject`, which would need an extension manager that can
rewrite `module.yaml`. `quarkusGoOffline` resolves the model in all three modes, so a later build needs no network.

Their switches travel as system properties, because `plugin.yaml` arguments are static:

| Property | Values | Applies to |
|---|---|---|
| `quarkus.mode` | `prod` (default), `test`, `dev` | all five |
| `quarkus.dependency.verbose` | `true` | tree, list |
| `quarkus.dependency.graph` | `true` | tree |
| `quarkus.dependency.runtime-only` | `true` | tree |
| `quarkus.dependency.flags` | comma-separated flag names, e.g. `direct,runtime-cp` | list |
| `quarkus.dependency.sbom.format` | `json` (default), `xml` | sbom |
| `quarkus.dependency.sbom.schema-version` | e.g. `1.5` | sbom |
| `quarkus.dependency.sbom.pretty-print` | `true` | sbom |
| `quarkus.dependency.sbom.include-license-text` | `true` | sbom |
| `quarkus.dependency.sbom.runtime-only` | `true` | sbom |
| `quarkus.dependency.sbom.include-quarkus-component-scope` | `true` | sbom |

```shell
KOTLIN_CLI_JAVA_OPTIONS="-Dquarkus.mode=test -Dquarkus.dependency.flags=reloadable" ./kotlin do quarkusDependencyList -m app
```

`quarkusImageBuild` and `quarkusImagePush` are the same production build with `quarkus.container-image.*` forced,
so the container-image extension does the work during augmentation.

`quarkusDeploy` bootstraps once and asks the extensions whether any of them declares a deploy command. If one
does, it runs that command. If none does — which is still the case for Kubernetes and OpenShift — it bootstraps a
second time with `quarkus.<deployer>.deploy` forced and runs a normal production build, which is what those
extensions hook into. Both bootstraps share one resolved application model.

Deployment-time artifacts are resolved by Quarkus itself into the regular local Maven repository
(`~/.m2/repository`).

## Limitations

* A classpath entry that is neither a Maven artifact nor a module JAR is reported and skipped.
* The SBOM carries no licence or description for the application component itself. Those come from a component's
  POM, and the toolchain publishes none for a module.
* Dev mode does not watch `module.yaml`. A change there needs a restart.
* Dev mode restarts the whole application when a local module changes; local modules are not separate reloadable
  archives.
* The `quarkus` preset of `settings.kotlin.allOpen` covers `javax.enterprise.context.ApplicationScoped` and
  `RequestScoped`, which Quarkus 3 no longer uses, so it opens nothing. Beans stay final and Quarkus makes them
  proxyable itself. List the `jakarta` annotations under `allOpen.annotations` to open them at compile time.
* `quarkusRun` and `quarkusDev` fork the JVM that runs the toolchain. A `KOTLIN_CLI_JAVA_HOME` older than
  `settings.jvm.release` fails there.
* Inside augmentation Quarkus ranks `application.properties` above the properties the build hands it, so a key set
  in both `buildProperties` and `application.properties` takes the value from the file, while
  `quarkusShowEffectiveConfig` reports the other one. Maven and Gradle build the same view. Use a system property
  to override a file, or the `finalName` setting for the base name.
* Native builds need Docker or Podman, unless `containerBuild` is set to `false` and a local GraalVM is on
  `PATH`.
* `jib` is the only container-image builder that works. Quarkus looks for project directories by walking up from
  the build directory in search of a `src/main` directory, and a Kotlin Toolchain module has `src` and `resources`
  instead. `docker` and `podman` then fail with "Unable to find root of Dockerfile files"
  (`quarkus.docker.dockerfile-jvm-path` does not help, because the same lookup decides the build context),
  `buildpack` with "Buildpack build unable to determine project dir", and `openshift` with an NPE under its
  `docker` build strategy. The sample application depends on `io.quarkus:quarkus-container-image-jib` for that
  reason.
* The toolchain ignores Maven dependency exclusions
  ([KTC-5843](https://youtrack.jetbrains.com/issue/KTC-5843)), so the plugin classpath mixes maven-resolver 2.x with
  a 1.9.x-era wiring layer. The plugin turns off the two remote repository filters that break under that mix.

## Notes

* The plugin pins `quarkus-bootstrap-core` in `plugins/quarkus/module.yaml`. Keep that version equal to the
  Quarkus version the application depends on. The plugin prints a warning when the two differ.
* The same file pins `quarkus-core-deployment`, which `quarkusDev` needs, `quarkus-cyclonedx-generator`, which
  `quarkusDependencySbom` needs, and `smallrye-config-core` and `smallrye-config-source-yaml`. Keep the first two
  equal to the Quarkus version and the other two equal to the `smallrye-config.version` property of `quarkus-bom`
  for that release.
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
