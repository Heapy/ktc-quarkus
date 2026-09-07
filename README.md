# Quarkus build plugin for the Kotlin Toolchain

Build, run and package a [Quarkus](https://quarkus.io) application from a
[Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain) project — no `pom.xml`, no `build.gradle`, no
Maven or Gradle process. The plugin runs Quarkus augmentation itself, so a `jvm/app` module becomes a fast-jar, an
uber-jar, a native binary, a container image or a live-reload dev session.

* Kotlin Toolchain: `0.12.0`
* Quarkus: `3.39.2`
* Verified on macOS aarch64 with JDK 25

```
project.yaml              # registers the plugin and the modules
app/                      # sample Quarkus application (Kotlin, REST endpoint)
lib/                      # local module the application depends on
plugins/quarkus/          # the build plugin
```

## Install

The toolchain cannot publish build plugins yet
([KTC-4871](https://youtrack.jetbrains.com/issue/KTC-4871)), so the plugin lives in your project.

**1. Copy the plugin directory into your project.**

```shell
cp -R plugins/quarkus <your-project>/plugins/quarkus
```

**2. Register it in `project.yaml`.** The plugin is both a module and a plugin, so it goes in two lists. `app` is
the sample's module name — use yours here and in every command below.

```yaml
modules:
  - app
  - plugins/quarkus

plugins:
  - //plugins/quarkus
```

**3. Enable it in the application module (`app/module.yaml`).**

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
      annotations: [ jakarta.enterprise.context.ApplicationScoped ]

plugins:
  quarkus: enabled
```

**4. Build it.**

```shell
./kotlin do quarkusBuild -m app
java -jar build/tasks/_app_quarkusBuild@quarkus/quarkus-app/quarkus-run.jar
```

**5. Keep the plugin's own versions in step with yours.** `plugins/quarkus/module.yaml` pins the Quarkus
libraries the plugin runs on. When you move to another Quarkus release, change all of them:

| Pin | Keep equal to |
|---|---|
| `io.quarkus:quarkus-bootstrap-core` | your Quarkus version |
| `io.quarkus:quarkus-bootstrap-maven-resolver` | your Quarkus version |
| `io.quarkus:quarkus-core-deployment` | your Quarkus version |
| `io.quarkus:quarkus-cyclonedx-generator` | your Quarkus version |
| `io.smallrye.config:smallrye-config-core` | `smallrye-config.version` in `quarkus-bom` for that release |
| `io.smallrye.config:smallrye-config-source-yaml` | the same |

The plugin prints a warning when its bootstrap version and your Quarkus version differ.

## Coming from Maven or Gradle

Same result, different command. `-m app` names the module.

| You want | Maven | Gradle | Here |
|---|---|---|---|
| Package the application | `mvn package` | `gradle quarkusBuild` | `./kotlin do quarkusBuild -m app` |
| Native executable | `mvn package -Dquarkus.native.enabled=true` | `gradle buildNative` | `./kotlin do quarkusNative -m app` |
| Run the packaged application | `mvn quarkus:run` | `gradle quarkusRun` | `./kotlin do quarkusRun -m app` |
| Dev mode with live reload | `mvn quarkus:dev` | `gradle quarkusDev` | `./kotlin do quarkusDev -m app` |
| Run the tests | `mvn test` | `gradle test` | `./kotlin test -m app` |
| Container image | `mvn quarkus:image-build` | `gradle imageBuild` | `./kotlin do quarkusImageBuild -m app` |
| Build and push the image | `mvn quarkus:image-push` | `gradle imagePush` | `./kotlin do quarkusImagePush -m app` |
| Deploy to Kubernetes | `mvn quarkus:deploy` | `gradle deploy` | `./kotlin do quarkusDeploy -m app` |
| See the effective configuration | `mvn quarkus:track-config-changes` | `gradle quarkusShowEffectiveConfig` | `./kotlin do quarkusShowEffectiveConfig -m app` |
| Platform BOMs and extensions | `mvn quarkus:info` | `gradle quarkusInfo` | `./kotlin do quarkusInfo -m app` |
| Dependency tree, deployment included | `mvn quarkus:dependency-tree` | — | `./kotlin do quarkusDependencyTree -m app` |
| Flat dependency list | `mvn quarkus:dependency-list` | — | `./kotlin do quarkusDependencyList -m app` |
| CycloneDX SBOM | `mvn quarkus:dependency-sbom` | — | `./kotlin do quarkusDependencySbom -m app` |
| Download everything for offline use | `mvn quarkus:go-offline` | `gradle quarkusGoOffline` | `./kotlin do quarkusGoOffline -m app` |

`quarkusImageBuild` needs a `quarkus-container-image-*` extension. `quarkusDeploy` needs a deployer extension such
as `quarkus-kubernetes`.

### Configuration

| You configure | Maven (`pom.xml`) | Gradle (`build.gradle`) | Here (`module.yaml`) |
|---|---|---|---|
| Extensions | `<dependency>` | `implementation(...)` | `dependencies:` |
| Platform BOM | `<dependencyManagement>` import | `enforcedPlatform(...)` | `- bom: io.quarkus.platform:quarkus-bom:3.39.2` |
| A local module | `<dependency>` on a sibling | `project(":lib")` | `- //lib` |
| Build-time Quarkus properties | `<properties>` | `quarkus { quarkusBuildProperties }` | `plugins.quarkus.buildProperties` |
| Runner jar name | `<finalName>` | `quarkus { finalName }` | `plugins.quarkus.finalName` |
| Manifest attributes | `<manifestEntries>` | `quarkus { manifest { attributes } }` | `plugins.quarkus.manifestEntries` |
| Manifest sections | `<manifestSections>` | `quarkus { manifest { manifestSections } }` | `plugins.quarkus.manifestSections` |
| Entries kept out of the jar | `<ignoredEntries>` | `quarkus { ignoredEntries }` | `plugins.quarkus.ignoredEntries` |
| Skip the Quarkus build | `-Dquarkus.build.skip` | `-Dquarkus.build.skip` | `plugins.quarkus.skip` |
| Dev-mode JVM options | `quarkus:dev -Djvm.args=...` | `quarkusDev { jvmArgs }` | `plugins.quarkus.dev.jvmArgs` |
| Kotlin all-open | `kotlin-maven-plugin`, `all-open` | `kotlin("plugin.allopen")` | `settings.kotlin.allOpen` |
| A one-off `-Dquarkus.*` value | `-Dquarkus.foo=bar` | `-Dquarkus.foo=bar` | `KOTLIN_CLI_JAVA_OPTIONS="-Dquarkus.foo=bar"` |

The last row is the one that surprises people. `./kotlin do quarkusBuild -m app -Dquarkus.foo=bar` fails with
`no such option`, so a one-off property travels in an environment variable instead:

```shell
KOTLIN_CLI_JAVA_OPTIONS="-Dquarkus.package.jar.type=uber-jar" ./kotlin do quarkusBuild -m app
```

That value also takes part in the up-to-date check, so changing it re-runs augmentation and repeating it does not.

### Tests

`@QuarkusTest` runs from the module's own test task:

```shell
./kotlin test -m app
```

Two things have to be declared by hand, and they are the sharp edge of this plugin. The Quarkus test classloader
is built from the application model, the model comes from the module's **main** runtime classpath, and no toolchain
reference exposes the test classpath. So the test framework belongs in `dependencies`, not `test-dependencies`:

```yaml
dependencies:
  - io.quarkus:quarkus-junit                    # not quarkus-junit5
  - io.quarkus:quarkus-bootstrap-core:3.39.2    # dropped from quarkus-junit's own graph
  - io.rest-assured:rest-assured

settings:
  jvm:
    test:
      extraEnvironment:
        TEST_TO_MAIN_MAPPINGS: jvmTest/kotlin-output:jvm/kotlin-output
```

Everything else `@QuarkusTest` needs — the serialized application model and the system properties that point at it
— the plugin supplies on its own.

## What does not work yet

| Thing | State | What to do instead |
|---|---|---|
| Code generation (gRPC, Avro, protobuf) | Blocked. A plugin cannot see the resolved dependency graph, which the generators need | Generate the sources outside the build |
| Integration tests (`quarkusIntTest`, `testNative`) | Blocked. Depends on the test-classpath gap below | Run the packaged artifact yourself |
| Continuous testing | Not wired as a command. The dev-mode console does offer `[r] to resume testing`; untested here | `./kotlin test -m app` |
| Remote dev mode | Not implemented | — |
| `./kotlin package` producing the Quarkus artifact | No hook lets a plugin replace a module's packaging output | `./kotlin do quarkusBuild -m app` |
| Test framework in `test-dependencies` | Fails with `ClassCastException: BuildChainBuilder cannot be cast to BuildChainBuilder` | Put it in `dependencies`, see [Tests](#tests) |
| `TEST_TO_MAIN_MAPPINGS` | Must be written by hand in every module with a `@QuarkusTest`. `PathTestHelper` reads it from the environment only, and a plugin cannot set one | The `settings.jvm.test.extraEnvironment` block, see [Tests](#tests) |
| `docker`, `podman`, `buildpack`, `openshift` image builders | Broken. Quarkus locates the project by walking up for a `src/main` directory, which a toolchain module does not have | Use `jib` |
| Maven dependency exclusions | The toolchain ignores them ([KTC-5843](https://youtrack.jetbrains.com/issue/KTC-5843)) | Declare the affected dependency with an explicit version |
| Maven relocation POMs | Not followed. `quarkus-junit5` resolves to a 5 KB stub with no transitive dependencies | Name the relocation target, `quarkus-junit` |
| `buildProperties` vs `application.properties` | Inside augmentation the file wins, while `quarkusShowEffectiveConfig` reports the other one. Maven and Gradle build the same view | Override with a system property, or use `finalName` for the base name |
| Dev mode and `module.yaml` | The file is not watched | Restart the dev session |
| Dev mode and a local module change | Restarts the whole application; local modules are not separate reloadable archives | — |
| all-open `quarkus` preset | Covers `javax.enterprise.context.*`, which Quarkus 3 no longer uses, so it opens nothing. Quarkus makes final beans proxyable itself | List the `jakarta` annotations under `allOpen.annotations` |
| SBOM licence of the application component | Missing. Licences come from a component's POM and the toolchain publishes none for a module | — |
| A classpath entry that is neither a Maven artifact nor a module JAR | Reported and skipped | — |
| `quarkusInfo` telling a declared extension from a transitive one | `module.runtimeClasspath` is flattened and keeps no record of what the module declared, so every entry counts as declared. The second list holds what the Quarkus model added on top, not what the first list pulled in | Read `module.yaml` for the declared set |
| Native build without a container | Needs Docker or Podman by default | `containerBuild: false` and a local GraalVM, see [below](#a-native-binary-for-the-host-platform) |
| `quarkusRun` and `quarkusDev` on an old JDK | They fork the JVM that runs the toolchain, so a `KOTLIN_CLI_JAVA_HOME` older than `settings.jvm.release` fails | Point `KOTLIN_CLI_JAVA_HOME` at a matching JDK |

Background and evidence for each of these: [`docs/spec.md`](docs/spec.md).

## Settings

Everything below goes under `plugins.quarkus` in `module.yaml`.

| Setting | Default | Meaning |
|---|---|---|
| `group` | `io.heapy.ktc` | Group ID of the synthetic application artifact |
| `version` | `1.0.0-SNAPSHOT` | Version of the application, also `quarkus.application.version` |
| `platformBom` | derived | `groupId:artifactId:version` of the platform BOM used for the deployment graph. Defaults to `io.quarkus:quarkus-bom` at the Quarkus version found on the runtime classpath |
| `finalName` | module name | Base name of the runner jar and the native binary, also `quarkus.build.base-name` |
| `buildProperties` | empty | Extra build-time Quarkus configuration. Only `quarkus.*` keys reach augmentation |
| `skip` | `false` | Skip augmentation without removing the plugin. Falls back to the `quarkus.build.skip` system property |
| `manifestEntries` | empty | Extra attributes of the main section of `MANIFEST.MF` |
| `manifestSections` | empty | Extra `MANIFEST.MF` attributes, keyed by section name |
| `ignoredEntries` | empty | Paths kept out of the runner jar. A value in `application.properties` wins |
| `cleanupBuildOutput` | `true` | Delete the previous output before augmenting, so a changed package type leaves nothing behind |
| `cachingRelevantProperties` | `quarkus[.].*`, `platform[.]quarkus[.].*` | Anchored regular expressions over property names whose values take part in the up-to-date check of `quarkusBuild` and `quarkusNative`. An environment variable is one of those names, under both its own spelling and the dotted lower-case one |
| `containerBuild` | `true` | Run `native-image` inside the Mandrel builder container |
| `run` | see below | Options for `quarkusRun` |
| `dev` | see below | Options for `quarkusDev` |
| `image` | see below | Options for `quarkusImageBuild` and `quarkusImagePush` |
| `deploy` | see below | Options for `quarkusDeploy` |

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

### `run`

| Setting | Default | Meaning |
|---|---|---|
| `jvmArgs` | empty | JVM options, inserted right after the executable |
| `systemProperties` | empty | Passed to the process as `-Dkey=value` |
| `environment` | empty | Added to the environment of the process |
| `arguments` | empty | Program arguments. When empty, the `QUARKUS_RUN_ARGS` environment variable is split on spaces |
| `workingDirectory` | module root | Working directory, relative to the module root. An extension that names its own directory wins |
| `target` | derived | Which run command to use when several extensions provide one. Falls back to the `quarkus.run.target` system property |

### `dev`

| Setting | Default | Meaning |
|---|---|---|
| `jvmArgs` | empty | JVM options for the dev-mode process |
| `arguments` | empty | Program arguments for the application |
| `environment` | empty | Environment variables added to the dev-mode process |
| `workingDirectory` | module root | Working directory, relative to the module root |
| `debug` | derived | `true`, `false`, `client` or a port. Falls back to the `debug` system property. Quarkus listens on `debugPort` unless this is `false` |
| `suspend` | derived | Wait for a debugger before starting. Falls back to the `suspend` system property |
| `debugHost` | `localhost` | Falls back to the `debugHost` system property |
| `debugPort` | `5005` | Falls back to the `debugPort` system property |
| `openJavaLang` | `false` | Add `--add-opens=java.base/java.lang=ALL-UNNAMED` |
| `modules` | empty | Java modules to add with `--add-modules` |
| `compilerArgs` | empty | Extra arguments for the Kotlin compiler that recompiles changed sources |
| `forceC2` | `false` | Keep the C2 compiler enabled. Dev mode disables it for faster startup |

### `image`

| Setting | Default | Meaning |
|---|---|---|
| `builder` | derived | `docker`, `podman`, `jib`, `buildpack` or `openshift`. Defaults to the container-image extension on the runtime classpath, then `docker`. Falls back to the `quarkus.container-image.builder` system property |

### `deploy`

| Setting | Default | Meaning |
|---|---|---|
| `target` | derived | Which extension-provided deploy command to run when several extensions declare one. Falls back to the `quarkus.deploy.target` system property |
| `deployer` | derived | `kubernetes`, `minikube`, `kind`, `knative` or `openshift`. Used when no extension declares a deploy command. Defaults to the deployer extension on the runtime classpath, then `kubernetes` |
| `imageBuild` | `false` | Build the container image as part of the deployment |
| `imageBuilder` | none | Which extension builds that image. Implies `imageBuild` |

### Switches of the diagnostic commands

`plugin.yaml` arguments are static, so these travel as system properties:

| Property | Values | Applies to |
|---|---|---|
| `quarkus.mode` | `prod` (default), `test`, `dev` | info, tree, list, sbom |
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

## Output

Each command writes into its own directory under `build/tasks/`:

| Command | Output |
|---|---|
| `quarkusBuild` | `_app_quarkusBuild@quarkus/quarkus-app/quarkus-run.jar`, or `<finalName>-runner.jar` for an uber or legacy jar |
| `quarkusNative` | `_app_quarkusNative@quarkus/<finalName>-runner` |
| `quarkusDependencySbom` | `_app_quarkusDependencySbom@quarkus/<finalName>-<version>-dependency-cyclonedx.json` |
| `quarkusEffectiveConfig`, run by the build | `_app_quarkusEffectiveConfig@quarkus/effective-config.properties` |

Deployment-time artifacts are resolved by Quarkus into the regular local Maven repository (`~/.m2/repository`).

## A native binary for the host platform

With `containerBuild: true` (the default) on macOS or Windows, the native executable is a Linux binary. Run it in a
container or on a Linux host. For a host binary, set `containerBuild: false` and point Quarkus at a local GraalVM or
Mandrel:

```yaml
plugins:
  quarkus:
    enabled: true
    containerBuild: false
```

```shell
GRAALVM_HOME=$(sdk home java 25.0.2.r25-mandrel) ./kotlin do quarkusNative -m app
```

Use a distribution whose JDK version matches `settings.jvm.release` of the module, which defaults to the toolchain
JDK. Verified on macOS aarch64 with Mandrel `25.0.2.r25`: `app-runner` is a Mach-O arm64 executable.

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
`application.properties`, the profile the plugin derived, platform properties, defaults. The profile sits below
`application.properties` so a module can name one there, as it can under Maven and Gradle; a system property, an
environment variable or a `buildProperties` entry still wins. `quarkusEffectiveConfig` writes those values, narrowed
by `cachingRelevantProperties`, to `effective-config.properties`. `quarkusBuild`, `quarkusNative` and
`quarkusTestModel` take that file as an input, which is how a system property or an environment variable reaches the
up-to-date check at all. That task runs on every invocation and rewrites the file only when the content differs.

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

`quarkusImageBuild` and `quarkusImagePush` are the same production build with `quarkus.container-image.*` forced,
so the container-image extension does the work during augmentation.

`quarkusDeploy` bootstraps once and asks the extensions whether any of them declares a deploy command. If one
does, it runs that command. If none does — which is still the case for Kubernetes and OpenShift — it bootstraps a
second time with `quarkus.<deployer>.deploy` forced and runs a normal production build, which is what those
extensions hook into. Both bootstraps share one resolved application model.

The toolchain ignores Maven dependency exclusions
([KTC-5843](https://youtrack.jetbrains.com/issue/KTC-5843)), so the plugin classpath mixes maven-resolver 2.x with
a 1.9.x-era wiring layer. The plugin turns off the two remote repository filters that break under that mix.
