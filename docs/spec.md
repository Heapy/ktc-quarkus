# Quarkus support in the Kotlin Toolchain plugin — specification

What the Quarkus Maven plugin (`devtools/maven`, 32 goals) and the Quarkus Gradle application plugin
(`devtools/gradle/gradle-application-plugin`, 27 tasks) do, and which of it the Kotlin Toolchain (KTC) plugin
in `plugins/quarkus` should implement.

Baseline: KTC `0.12.0`, Quarkus `3.39.2`, plugin source read at Quarkus commit `e1c73424`.
Today the KTC plugin implements seven tasks: `quarkusBuild`, `quarkusNative`, `quarkusRun`, `quarkusImageBuild`,
`quarkusImagePush`, `quarkusDeploy` and `quarkusShowEffectiveConfig`.

Status values used below:

* **done** — implemented in `plugins/quarkus`.
* **todo** — implementable with the current KTC plugin API.
* **blocked** — needs a KTC feature that does not exist in `0.12.0`.
* **skip** — build-system specific, or belongs to a project generator, not to a build plugin.

## 1. Feature matrix

| Purpose | Maven goal | Gradle task | Status | Key Quarkus API |
|---|---|---|---|---|
| Package the application (JVM) | `build` | `quarkusBuild` | done | `AugmentAction.createProductionApplication()` |
| Native executable | `build` + `quarkus.native.enabled` | `buildNative` | done | same, `quarkus.native.*` build properties |
| Effective build configuration | `track-config-changes` | `quarkusShowEffectiveConfig` | done | `EffectiveConfig`, `SmallRyeConfig` |
| Run the packaged application | `run` | `quarkusRun` | done | `AugmentAction.performCustomBuild(StartDevServicesAndRunCommandHandler)` |
| Container image build | `image-build` | `imageBuild` | done | forces `quarkus.container-image.build` |
| Container image push | `image-push` | `imagePush` | done | forces `quarkus.container-image.push` |
| Deploy (k8s / openshift / minikube / kind / knative) | `deploy` | `deploy` | done | `DeployCommandDeclarationHandler`, `DeployCommandHandler` |
| Local module dependencies in the app model | `QuarkusMavenWorkspaceBuilder` | `ApplicationDeploymentClasspathBuilder` | done | `WorkspaceModule`, `ArtifactSources` |
| Incremental up-to-date check on configuration | `track-config-changes` | `quarkusShowEffectiveConfig` | **todo** | config dump file compared between builds |
| Code generation, main sources | `generate-code` | `quarkusGenerateCode` | **blocked** | `CodeGenerator.initAndRun(...)` |
| Code generation, dev sources | `generate-code` (`launchMode=DEVELOPMENT`) | `quarkusGenerateCodeDev` | blocked | same |
| Code generation, test sources | `generate-code-tests` | `quarkusGenerateCodeTests` | **blocked** | same |
| `@QuarkusTest` in the module's own test task | `argLine` injection in `GenerateCodeTestsMojo` | `BeforeTestAction` on `Test` | done, with the §4.1 caveats | `BootstrapConstants.SERIALIZED_TEST_APP_MODEL` |
| Continuous testing | `test` | `quarkusTest` | blocked | depends on the above |
| Integration tests against the artifact | failsafe + `quarkus-artifact.properties` | `quarkusIntTest`, `testNative` | blocked | depends on the above |
| Dev mode | `dev` | `quarkusDev` | **todo** (large) | `DevModeCommandLineBuilder`, `DevModeMain` |
| Remote dev mode | `remote-dev` | `quarkusRemoteDev` | todo (after dev mode) | `DevModeCommandLineBuilder.remoteDev(true)` |
| Cacheable split of deps and app parts | — | `quarkusDependenciesBuild`, `quarkusAppPartsBuild` | optional | `AugmentAction` with restricted output sets |
| Prefetch every artifact needed offline | `go-offline` | `quarkusGoOffline` | optional | `BootstrapAppModelResolver` |
| Print platform BOMs and extensions | `info` | `quarkusInfo` | optional | `QuarkusProjectStateMojoBase` |
| Dependency tree of runtime + deployment | `dependency-tree` | — | optional | `DependencyTreeMojo` |
| Flat dependency list | `dependency-list` | — | optional | — |
| SBOM (CycloneDX) | `dependency-sbom` | — | optional | `SbomGenerator` |
| Suggest and apply project updates | `update` | `quarkusUpdate` | skip | needs to rewrite the build file |
| Add / remove / list extensions, categories, platforms | `add-extension(s)`, `remove-extension(s)`, `list-extensions`, `list-categories`, `list-platforms` | same names | skip | needs a `module.yaml`-aware `ExtensionManager` in `devtools-common` |
| Create a project / extension / JBang script | `create`, `create-extension`, `create-jbang` | — | skip | `kotlin init` territory |
| Trim native-image agent configuration | `native-image-agent` | — | skip | post-processing of a native profiling run |
| AOT / PGO enhanced artifact | `build-enhanced-artifact` | `buildAotEnhancedImage` | skip | needs integration tests first |
| Analyse the native call tree | `analyze-call-tree` | — | skip | diagnostic tool, not a build step |
| Deprecated aliases | `prepare`, `prepare-tests` | `quarkusTestConfig` | skip | aliases of `generate-code*`; `quarkusTestConfig` is a no-op |
| Maven lifecycle mapping, artifact handler, version enforcer, build analytics | several classes | — | skip | build-system internals |

## 2. Settings parity

`QuarkusSettings` has 9 properties today. The table lists what Maven and Gradle expose, with the ones KTC
already covers marked `done`.

| Setting | Maven | Gradle | Effect | Priority |
|---|---|---|---|---|
| `finalName` | `finalName` | `finalName` | base name of the runner jar and native binary; sets `quarkus.build.base-name` | done |
| `skip` | `quarkus.build.skip` | `quarkus.build.skip` | skip augmentation without removing the plugin | medium |
| `manifestEntries` | `manifestEntries` | `manifest { attributes }` | extra `MANIFEST.MF` attributes | medium |
| `manifestSections` | `manifestSections` | `manifest { manifestSections }` | per-section manifest attributes | low |
| `ignoredEntries` | `ignoredEntries` | `ignoredEntries` | maps to `quarkus.package.jar.user-configured-ignored-entries` | medium |
| `cachingRelevantProperties` | — | `cachingRelevantProperties` | property patterns that take part in the up-to-date check | medium (with §3.7) |
| `cleanupBuildOutput` | — | `cleanupBuildOutput` | delete previous output before augmentation | low |
| `codeGenerationInputs` | derived from source roots | `codeGenerationInputs` | extra input directories for code generators | with §4.2 |
| `codeGenerationProviders` | — | `codeGenerationProviders` | restrict which generators run | with §4.2 |
| `nativeBuilderImage` | via `quarkus.native.builder-image` | same | already reachable through `buildProperties` | none |
| `jvmArgs`, `workingDirectory` | `run` goal | `quarkusRun` | for `quarkusRun` | with §3.3 |
| `builderName` | `quarkus.container-image.builder` | `ImageCheckRequirementsTask` | for `quarkusImageBuild` | done, as `image.builder` |
| `deployer`, `image-build`, `image-builder` | `deploy` goal | `deploy` | for `quarkusDeploy` | done, as `deploy.*` |
| dev-mode knobs (`debug`, `suspend`, `debugHost`, `debugPort`, `jvmArgs`, `applicationArgs`, `compilerOptions`, `openJavaLang`, `tests`) | `dev` goal | `quarkusDev` | for dev mode | with §3.8 |

Maven-only settings that have no KTC meaning: `skipOriginalJarRename`, `attachRunnerAsMainArtifact`, `attachSboms`,
`appArtifact`, `reloadPoms`, `quarkusCloseBootstrappedApp`.

## 3. Work items

### 3.1 Container image and deploy

Reference. `ImageBuild`, `ImagePush` (Gradle) and `ImageBuilder` (Maven) are thin: they force properties and let the
normal build run.

* `imageBuild` → `quarkus.container-image.build=true`, plus `quarkus.container-image.builder=<docker|jib|podman|buildpack|openshift>`.
* `imagePush` → `quarkus.container-image.build=true` **and** `quarkus.container-image.push=true`.

`Deploy` is a little more than that. It runs a custom build with `DeployCommandDeclarationHandler` to ask the
extensions which deployers they support, picks one (`quarkus.deploy.target`, or the only one available), then forces
`quarkus.<deployer>.deploy=true` — not a single fixed key — together with `quarkus.container-image.build` and
`quarkus.container-image.builder`, and finally runs `DeployCommandHandler`.
Both plugins also check up front that the module depends on the deployer extension
(`kubernetes`/`knative` → `quarkus-kubernetes`, `openshift` → `quarkus-openshift`, `minikube` → `quarkus-minikube`,
`kind` → `quarkus-kind`) and on one `quarkus-container-image-*` extension.

Do. Three commands that reuse the existing `quarkusBuild` action with extra forced properties, plus the deployer
discovery step for `deploy`. Cheapest wins in this document.

Done. `quarkusImageBuild`, `quarkusImagePush` and `quarkusDeploy`.

`quarkusImage` is one action with a `push` flag, the way `quarkusBuild` carries `nativeImage`. It forces
`quarkus.container-image.build`, `quarkus.container-image.builder` and, for a push,
`quarkus.container-image.push`. The builder is chosen the way `ImageCheckRequirementsTask` chooses it:
`settings.image.builder`, then the `quarkus.container-image.builder` system property, then the container-image
extension on the runtime classpath, then `docker`. `quarkus-openshift` counts as the `openshift` builder without
the `quarkus-container-image-` prefix. A builder without its extension fails before augmentation starts.

`quarkusDeploy` bootstraps once and runs `DeployCommandDeclarationHandler`. When an extension declares a deploy
command it picks one (`settings.deploy.target`, then the `quarkus.deploy.target` system property, then the single
entry) and runs `DeployCommandHandler` on the same application. When none does — the Kubernetes and OpenShift
extensions still deploy through configuration — it bootstraps a second time with
`quarkus.<deployer>.deploy=true` plus `quarkus.container-image.build`, and runs a normal production build.
`settings.deploy.deployer` names the deployer; otherwise it is the first one enabled in `settings.buildProperties`,
then the deployer extension on the runtime classpath, then `kubernetes`.
`quarkus.container-image.builder` is forced only when `settings.deploy.imageBuilder` names one, as in Gradle.

The two bootstraps share one resolved `ApplicationModel`, because Quarkus bakes the build system properties into
`QuarkusBootstrap` and the forced properties are only known after the first custom build.

Not copied from Gradle: `Deploy.requiresOneOf` rejects a Kubernetes deployment whose only container-image
extension is `podman` or `openshift`, and with `imageBuild` and no named builder it demands
`quarkus-container-image-docker` even when `quarkus-container-image-jib` is present. Both are false rejections.
The cost of dropping the check: `quarkusDeploy` with `imageBuild` and no named builder reports a missing
container-image extension from inside augmentation instead of before it. `quarkusImageBuild` always reports it
first, because it always selects a builder.

Verified: `quarkusImageBuild` with `quarkus-container-image-jib` produces a real image
(`<user>/app:1.0.0-SNAPSHOT` in the local Docker daemon), and `quarkusDeploy` with `quarkus-kubernetes` reaches
the Kubernetes deployer, which then fails on the missing cluster. `quarkusImagePush` is not verified — it needs a
registry.

`jib` is the only container-image builder that works in a KTC layout. `PathsUtil.findMainSourcesRoot` walks up
from the task output directory looking for a `src/main` directory, and a KTC module has `src` and `resources`
instead, so the helper returns null. What each builder does with that:

* `docker` and `podman` share `CommonProcessor`, which reports "Unable to find root of Dockerfile files".
  `quarkus.docker.dockerfile-jvm-path` does not help: `ProvidedDockerfile.get` calls the same helper and fails with
  "Unable to determine project root".
* `buildpack` throws "Buildpack build unable to determine project dir".
* `openshift` dereferences the null result, so it fails with an NPE under the `docker` build strategy. Its default
  `binary` strategy does not reach that code.
* `jib` uses the helper only for the optional `src/main/jib` extra-files layer and returns early when it is null.

A one-line Quarkus change — fall back to the bootstrap project root when no `src/main` is found — would close this,
the same shape as the `PathTestHelper` request in §4.1.

### 3.2 Use compiled classes instead of unpacking the jar

Today `quarkusBuild` unpacks `${module.jar}` into `app-classes`. KTC `0.12` added the `module.classes` reference,
which is the directory of compiled classes. Maven and Gradle both pass a classes directory, never a jar.

On disk KTC keeps compiled output outside `build/tasks`:

```
build/artifacts/CompiledJvmArtifact/<module>jvm/kotlin-output       # main classes
build/artifacts/CompiledJvmArtifact/<module>jvm/resources-output    # processed resources
build/artifacts/CompiledJvmArtifact/<module>jvmTest/kotlin-output   # test classes
```

Done. `${module.classes}` resolves to `kotlin-output`, which holds classes only, so the resource directories come
from `${module.resources}` and are registered as their own `SourceDir` entries with the source directory used as
its own output. Every root goes into `QuarkusBootstrap.setApplicationRoot(PathList)`.

The module JAR is no longer read, but `${module.runtimeClasspath}` still builds it, because it resolves
`${module.self}` in `jars` mode. Setting `settings.jvm.runtimeClasspathMode: classes` would drop that step.

### 3.3 `quarkusRun`

Reference. `QuarkusRun.runQuarkus`: bootstrap in `Mode.RUN`, then
`action.performCustomBuild(StartDevServicesAndRunCommandHandler.class.getName(), consumer)`.
The handler returns a map of run commands keyed by target; the task picks one (`quarkus.run.target`, or the single
entry, or the non-`java` entry when there are two), inserts `jvmArgs` after the executable, and forks the process.

Do. Same, as a KTC command. Add `settings.run.jvmArgs` and `settings.run.workingDirectory`. Small task, high value.

Risk. Gradle documents that Ctrl-C handling of the forked process is fragile. Check how `./kotlin do` propagates
signals before promising clean shutdown.

### 3.4 Effective configuration and property precedence

Problem. The plugin builds `Properties` from `settings.buildProperties` only, and it overwrites
`quarkus.application.name` and `quarkus.application.version` unconditionally. Both `application.properties` and
`-Dquarkus.*` on the command line are invisible to the plugin. Quarkus still reads `application.properties` during
augmentation, but the plugin cannot see any of those values, so it cannot know its own package type or output paths.

Reference. `EffectiveConfig` (Gradle) layers config sources by ordinal:
`forcedProperties` (600) > task properties (500) > system properties and environment >
`quarkusBuildProperties` (290) > project properties (280) > YAML and `.properties` files > platform properties (0).
`QuarkusBootstrapProvider.getBuildSystemProperties` (Maven) uses `putIfAbsent` for
`quarkus.application.name` / `quarkus.application.version` and `put` for `quarkus.build.base-name`.

Do.

* Add an `effectiveConfig(applicationModel)` step that mirrors `EffectiveConfig`, reading the module's
  `resources/application.properties` and `application.yaml`, the system properties of the task JVM,
  `settings.buildProperties`, and `applicationModel.getPlatformProperties()`.
* Change `quarkus.application.name` / `quarkus.application.version` to `putIfAbsent`.
* Set `quarkus.build.base-name` from the new `finalName` setting, defaulting to the module name.
* Expose the result through a new `quarkusShowEffectiveConfig` command.

Done. `EffectiveConfig.kt` layers the same sources at the same ordinals on a `SmallRyeConfig` built from
`smallrye-config-core` and `smallrye-config-source-yaml`. `addPropertiesSources()` and
`YamlConfigSourceLoader.InClassPath` read the module's resource directories through a `URLClassLoader` that hides
`META-INF/services`, exactly as `EffectiveConfig.toUrlClassloader` does. `addDefaultInterceptors()` is required:
without it `%<profile>.` prefixes and `${...}` expressions are not resolved. The profile follows the bootstrap
mode (`PROD`/`RUN` -> `prod`, `TEST`/`CONTINUOUS_TEST` -> `test`, the dev modes -> `dev`) unless
`quarkus.profile` is set as a system property, as `QUARKUS_PROFILE`, or in `buildProperties`.

Notes established while implementing it.

* `-Dquarkus.*` reaches the task JVM as `KOTLIN_CLI_JAVA_OPTIONS="-Dquarkus.http.port=9999" ./kotlin do ... -m app`,
  so the system-property source is live and §3.7 can rely on it.
* `config/application.properties` and `config/application.(yaml|yml)` are resolved by SmallRye against the process
  `user.dir`, which for the toolchain is the project root, not the module. Gradle has the same behaviour.
* Only `quarkus.*` and `platform.quarkus.*` keys reach augmentation, as in Gradle's `generateQuarkusConfigMap`.
  A build-time property under any other prefix cannot be set through `buildProperties`.
* `quarkus.build.base-name` is informational. `QuarkusAugmentor` overwrites it from the bootstrap base name, so
  `finalName` always wins, which is what Maven does with its unconditional `put`.
* Augmentation ranks the build system properties **below** `application.properties`: SmallRye gives the
  `Build system` source ordinal 100 and `application.properties` 250. So `buildProperties` loses to
  `application.properties` inside augmentation, while `quarkusShowEffectiveConfig` ranks it at 290 and reports the
  opposite. Gradle papers over this by re-setting the values as real system properties, but only in a forked
  worker; in-process it deliberately does not, because that corrupts the running build
  ([quarkusio/quarkus#55131](https://github.com/quarkusio/quarkus/issues/55131)). The plugin augments in the task
  JVM, so it takes the same in-process path and inherits the skew.

### 3.5 Local module dependencies in the application model

Problem (already in `README.md`). `readDependencies` maps each classpath file to Maven coordinates from its position
in the local Maven repository. A jar produced by another KTC module is not in that layout, so it is reported and
dropped. The application then augments without that module's classes.

Reference. Maven uses `QuarkusMavenWorkspaceBuilder`; Gradle uses `ApplicationDeploymentClasspathBuilder`. Both
produce a `WorkspaceModule` per local project with `ArtifactSources` pointing at source and output directories, and
mark the dependency `WORKSPACE_MODULE` / `RELOADABLE`.

Do.

* Detect local module jars by their path: KTC writes them to
  `<project>/build/tasks/_<moduleName>_jarJvm/<moduleName>-jvm.jar`.
* Build a `DefaultWorkspaceModule` for each, with a synthetic
  `WorkspaceModuleId(settings.group, moduleName, settings.version)` and `ArtifactSources.main(...)` pointing at that
  module's sources and classes.
* Attach them with `ArtifactDependency` flags `DIRECT | RUNTIME_CP | DEPLOYMENT_CP | WORKSPACE_MODULE | RELOADABLE`.

Done, with a cheaper design than the one sketched above. A classpath entry whose parent directory matches
`_<name>_jarJvm` and whose file name is `<name>-jvm.jar` is a local module. Its JAR is unpacked into
`${taskOutputDir}/local/<name>` and registered as another `SourceDir` of the application's own `WorkspaceModule`,
and every unpacked root is passed to `QuarkusBootstrap.setApplicationRoot(PathList)`. No `project.yaml` parsing and
no Maven workspace reader.

Trade-off taken: local modules become part of the application archive rather than separate reloadable archives.
Dev mode (§3.8) will therefore not hot-reload them separately, and that is the point at which the workspace-reader
design becomes worth its cost.

The `_<name>_jarJvm` layout is not a public KTC contract. A `module.localDependencies` reference in `plugin.yaml`
would remove the heuristic — worth a KTC feature request.

### 3.6 Dependency-model fidelity

`readDependencies` recovers coordinates from the Maven repository layout, then `BootstrapAppModelResolver` resolves
the graph again from scratch. Two consequences: KTC's own conflict resolution and exclusions are not honoured on the
Quarkus side, and the augmented application may use different versions than the module compiled against.
Maven and Gradle both hand Quarkus the graph their own resolver produced.

A `module.runtimeDependencies` reference that exposes resolved coordinates and scope, not only files, would remove
both this and the `MavenLayout` heuristic, and would also unblock §4.2. Second KTC feature request.

### 3.7 Incrementality

Today `quarkusBuild` declares `@Input appJar, runtimeClasspath, moduleDir` and `@Output outputDir`.
KTC compares file attributes and modification times only — it does not hash content. So a touched but unchanged
`application.properties` re-runs augmentation, and a changed `-Dquarkus.*` system property does not.

Note that plugin.yaml arguments are static references (`${module.*}`, `${pluginSettings}`, `${taskOutputDir}`).
Nothing computes a value there, and a value computed inside the action is invisible to the up-to-date check. So the
configuration has to become a *file*.

Reference. `TrackConfigChangesMojo` writes the build-time configuration the previous build actually read, so a cache
can compare the two files.

Do.

* Add a `quarkusEffectiveConfig` task that writes `${taskOutputDir}/effective-config.properties` from §3.4.
  Rewrite the file only when its content differs from what is already there — KTC looks at modification times, so an
  unconditional write makes every build dirty.
* Give `quarkusBuild` and `quarkusNative` an `@Input` on that file. KTC infers the task order from the matching
  path, so no manual wiring is needed.
* Add `settings.cachingRelevantProperties` to narrow which properties are written into the file.

### 3.8 Dev mode

Reference. `DevMojo` and `QuarkusDev` both build a command line with `DevModeCommandLineBuilder`
(`projectDir`, `buildDir`, `outputDir`, `buildSystemProperties`, `applicationName`, `applicationVersion`,
`sourceEncoding`, `compilerOptions`, `releaseJavaVersion`, `mainModule`, `dependency`, `classpathEntry`,
`jvmArgs`, `debug`, `suspend`, `debugHost`, `debugPort`, `addOpens`, `addModules`, `extensionDevModeConfig`),
serialize the `ApplicationModel` to disk, pass it as
`-Dquarkus-internal.serialized-app-model.path=<file>`, and fork a JVM that runs `DevModeMain`.

Do. Port `QuarkusDev.newLauncher` against KTC references: `module.rootDir`, `module.classes`,
`module.kotlinJavaSources`, `module.resources`, `module.compileClasspath`.
Each reloadable module becomes a `DevModeContext.ModuleInfo`.

Risks, in order of severity.

1. Kotlin hot reload needs `quarkus-kotlin`'s `KotlinCompilationProvider` on the dev-mode classpath and the module's
   Kotlin compiler options. Verify whether `module.settings.kotlin.*` (`freeCompilerArgs`, `languageVersion`,
   `apiVersion`, `compilerPlugins`) covers everything `DevModeCommandLineBuilder.compilerOptions` needs.
2. The task blocks for the lifetime of the application. Register it as a command
   (`./kotlin do quarkusDev -m app`) and set `@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)`.
   Confirm that `./kotlin do` forwards stdin and terminal signals; continuous testing needs both.
3. Live reload of `module.yaml` has no equivalent of `watchedBuildFile`.

Recommendation: do this last, after §3.1–§3.7 have made the application model correct.

## 4. Blocked on the toolchain

### 4.1 `@QuarkusTest` (the biggest gap)

What Gradle does, in `BeforeTestAction`, on the module's own `Test` task:

* system properties (`task.getSystemProperties()`):
  * `quarkus-internal-test.serialized-app-model.path` — path to the serialized TEST-mode `ApplicationModel`;
  * every `quarkus.*` value from the effective TEST configuration, plus `quarkus.test.profile`;
  * `OUTPUT_SOURCES_DIR` — comma-separated output directories added as application roots;
  * `native.image.path` for native integration tests;
* environment (`task.environment(...)`): `TEST_TO_MAIN_MAPPINGS`, and nothing else.

`OUTPUT_SOURCES_DIR` is named like an environment variable but is read with `System.getProperty` in
`AppMakerHelper` and `IntegrationTestUtil`. `TEST_TO_MAIN_MAPPINGS` is the only value that has to be an
environment variable.

`TEST_TO_MAIN_MAPPINGS` is a comma-separated list of `<from>:<to>` **path fragments**, not full paths.
`PathTestHelper` reads it with `System.getenv` in a static initializer, into `TEST_TO_MAIN_DIR_FRAGMENTS` and the
derived `private static final List TEST_DIRS`. Both users of that state matter:
`getAppClassLocationForTestLocation` replaces the last occurrence of `<from>` with `<to>`, and
`getTestClassesLocation` throws when the test-classes path contains no known fragment.

For KTC the pair is `jvmTest/kotlin-output:jvm/kotlin-output`. It is module-independent, but not
platform-independent: the built-in fragments are built from `File.separator` and the value is matched with
`String.contains`, so Windows needs `jvmTest\kotlin-output:jvm\kotlin-output`.

Why it is blocked. A KTC plugin can register tasks and contribute generated sources and resources. It cannot
contribute system properties, environment variables, or JVM arguments to the module's test run.
`settings.jvm.test` has `systemProperties`, `extraEnvironment` and `freeJvmArgs`, but only the user can write them
in `module.yaml`. `ModuleDataForPlugin` in `frontend-api-jvm.jar` exposes exactly ten references — `name`,
`rootDir`, `runtimeClasspath`, `compileClasspath`, `kotlinJavaSources`, `resources`, `jar`, `classes`, `self`,
`settings` — so a plugin also cannot see the module's test sources, test classes, or test-scope dependencies.

#### Measured behaviour of the test JVM (KTC 0.12.0, verified in this project)

* Tests run in a forked JVM started from `junit-platform-console-standalone`. It inherits the environment of the
  `./kotlin` process, so `TEST_TO_MAIN_MAPPINGS=... ./kotlin test` reaches the test JVM.
* `./kotlin test --jvm-args="-Dfoo=bar"` reaches the same JVM.
* Its working directory is the **module** directory, not the project root. Quarkus uses that as `projectRoot`, and
  `BootstrapAppModelFactory` resolves a relative `quarkus-internal-test.serialized-app-model.path` against it.
* Test classes are a directory: `build/artifacts/CompiledJvmArtifact/<module>jvmTest/kotlin-output`.
  Test resources are a second directory, `.../<module>jvmTest/resources-output`.
* The module's own main classes reach the test classpath as `build/tasks/_<module>_jarJvm/<module>-jvm.jar`, not as
  `<module>jvm/kotlin-output`. The fragment mapping points at the classes directory, which exists but is not itself
  on the test classpath. `settings.jvm.runtimeClasspathMode: classes` changes that.
* A `META-INF/services/org.junit.platform.launcher.LauncherSessionListener` on the test classpath **is**
  auto-registered, and `System.setProperty` from `launcherSessionOpened` is visible to the tests.
* `generated.resources` accepts `fragment: { isTest: true }`. The directory is copied into
  `<module>jvmTest/resources-output`, and `./kotlin show tasks` reports the producing plugin task as a dependency of
  `:<module>:compileJvmTest`, so `./kotlin test` runs it without a manual `./kotlin do` step.

#### Workaround, implemented

`./kotlin test -m app` boots the application and passes a `@QuarkusTest` that calls the endpoint. Everything
except one environment variable is done by the plugin.

* `quarkusTestModel` writes the serialized `ApplicationModel` into `${taskOutputDir}/model`, and into
  `${taskOutputDir}/test-resources`:
  * `META-INF/ktc-quarkus-test.properties` — `quarkus-internal-test.serialized-app-model.path` and
    `OUTPUT_SOURCES_DIR`;
  * `META-INF/services/org.junit.platform.launcher.LauncherSessionListener`;
  * `QuarkusTestListener.class`. The listener is Java, so it does not drag the plugin's Kotlin stdlib into the test
    JVM, and it cannot be a `generated.sources` entry: compiling it needs `junit-platform-launcher` on the module's
    test compile classpath, and a plugin cannot add dependencies. The task copies the bytes out of the plugin's own
    classpath, and names the class by string — loading it would need the compile-only interface the plugin JVM
    does not have.
* The second directory is declared as a test-scope resource, which also wires the task into `compileJvmTest`:

  ```yaml
  generated:
    resources:
      - directory: ${tasks.quarkusTestModel.action.testResourcesDir}
        fragment:
          isTest: true
  ```

* At test-session start the listener reads the properties file from the classpath and applies every value that is
  not already set, so `--jvm-args` and `settings.jvm.test.systemProperties` still win.
* The `WorkspaceModule` needs `setBuildFile`. Without it `ApplicationModelSerializer.deserialize` fails with
  `NullPointerException: "buildFilesStr" is null` — the serialized form has no default for that key.

The listener only covers properties that are read lazily. `quarkus-internal-test.serialized-app-model.path`,
`OUTPUT_SOURCES_DIR` and the `quarkus.*` values all are. A property read during JVM or launcher bootstrap is not:
every probe run in this project logged `The LogManager accessed before the "java.util.logging.manager" system
property was set`, and Quarkus documents that one as a real `-D`. Such properties still need a JVM argument, which
is the second half of the KTC request.

What stays with the user: one constant line per module, or once in a shared template.

```yaml
test-settings:
  jvm:
    extraEnvironment:
      TEST_TO_MAIN_MAPPINGS: "jvmTest/kotlin-output:jvm/kotlin-output"
```

`TEST_TO_MAIN_MAPPINGS=jvmTest/kotlin-output:jvm/kotlin-output ./kotlin test` is the same thing without editing
`module.yaml`, and CI can export it once.

The environment half cannot be closed in-process. `System.getenv` is immutable, `PathTestHelper` caches the parsed
value in a `static final` field at class-initialisation time, and writing into `java.lang.ProcessEnvironment` needs
`--add-opens`, which is the same JVM-argument gap.

#### What the module has to declare

```yaml
dependencies:
  - io.quarkus:quarkus-junit                    # not quarkus-junit5, see §4.6
  - io.quarkus:quarkus-bootstrap-core:3.39.2    # dropped from quarkus-junit's graph, see §4.6
  - io.rest-assured:rest-assured

settings:
  jvm:
    test:
      extraEnvironment:
        TEST_TO_MAIN_MAPPINGS: jvmTest/kotlin-output:jvm/kotlin-output
```

The test framework sits in `dependencies`, not `test-dependencies`, and that is the sharp edge. The Quarkus test
classloader is built from the application model, the model comes from `${module.runtimeClasspath}`, and no reference
exposes the test classpath. Anything that classloader has to load — `quarkus-junit`, and everything
`quarkus-test-common` touches, which is why RestAssured is there — has to be on the main runtime classpath.
Moving them to `test-dependencies` fails: first
`ClassCastException: BuildChainBuilder cannot be cast to BuildChainBuilder`, because `TestBuildChainFunction` is
then loaded by the system classloader rather than the augmentation one, and after that
`NoClassDefFoundError: groovy/lang/GroovyObject`, because `RestAssuredStateManager` runs inside the Quarkus
classloader and Groovy is not in the model.

This is the strongest argument for the test-scope references in the KTC request below: with
`module.testRuntimeClasspath` the plugin would build the model Maven and Gradle build, and `test-dependencies` would
work normally.

`PathTestHelper.getProjectBuildDir` returns `<module>/target` for this layout, because the test classes live outside
the module directory. Quarkus did not create it in these runs, but it is the directory it would write to.

Known edge: `generated.resources` applies to every module that enables the plugin, so `quarkusTestModel` becomes a
dependency of `compileJvmTest` there as well, and it fails hard on a module whose runtime classpath has no Quarkus.
Make the task tolerant before enabling the plugin on such a module.

Two ways to remove that last line, in order of cost:

1. Upstream Quarkus: let `PathTestHelper` fall back to `System.getProperty(BootstrapConstants.TEST_TO_MAIN_MAPPINGS)`
   when the environment variable is absent. One line, and the workaround above becomes complete.
2. KTC feature request: let a plugin contribute test-JVM system properties, environment variables and JVM arguments,
   the same shape as `generated.sources`. The same request should add test-scope references
   (test classes, test resources, test sources, test runtime classpath) to `ModuleDataForPlugin`. File it against
   `KTC`.

### 4.2 Code generation (`quarkusGenerateCode`)

Reference. `GenerateCodeMojo.generateCode` and `CodeGenWorker.execute` both call
`CodeGenerator.initAndRun(deploymentClassLoader, sourceParentDirs, generatedSourcesDir, buildDir,
sourceRegistrar, appModel, properties, launchMode, test)`.
Each provider resolves `sourceParentDir.resolve(provider.inputDirectory())` — `proto`, `avro`, `grpc`, `openapi` —
and writes to `generatedSourcesDir.resolve(provider.providerId())`.

Why it is blocked. Code generation must run **before** compilation: its output is attached with `generated.sources`,
which feeds the module's compilation. But building the deployment classloader needs the module's dependencies, and
neither classpath reference can supply them without a cycle:

* `module.compileClasspath` is documented as "compile classpath **plus the module's compilation result**";
* `module.runtimeClasspath` also contains the module's own jar — `readDependencies` already skips it, and
  `./kotlin show tasks` confirms `:app::app:quarkusBuild@quarkus*resolve-[runtimeClasspath] -> :app:jarJvm`.

A task with either as `@Input` runs after compilation, so it cannot produce sources for that compilation.
There is no reference in the KTC table that exposes resolved dependencies without the module's own output.
This is the same missing feature as §3.6.

Interim, if code generation is needed before that lands: parse the module's `module.yaml` `dependencies:` block
directly and resolve them with the `BootstrapMavenContext` the plugin already creates, instead of taking a KTC
classpath reference. The task then has no `@Input` on any classpath and no cycle. Cost: the plugin re-implements
dependency resolution, and BOM/catalog references in `module.yaml` have to be handled by hand.

Once unblocked, register the output as:

```yaml
generated:
  sources:
    - language: java
      directory: ${tasks.quarkusGenerateCode.action.generatedSourceDir}
```

Most Quarkus generators emit Java. A generator that emits Kotlin needs a second `generated.sources` entry and a
second `@Output` directory, one per language.

Source parents in a KTC module: `${module.rootDir}`, so `app/proto` and `app/avro` sit next to `app/src`.
Add `settings.codeGenerationInputs` for extra parents.

### 4.3 Test-scope generated sources

`generated.sources` in `plugin.yaml` attaches a directory to the module's compilation. Entries accept an optional
`fragment`, which is a `FragmentDescriptor` with two keys: `modifier` (the platform qualifier without `@`, empty by
default, and the shorthand target so `fragment: jvm` works) and `isTest` (`false` by default).
`fragment: { isTest: true }` is accepted, the directory lands in `<module>jvmTest/kotlin-output` or
`<module>jvmTest/resources-output`, and the producing task becomes a dependency of `:<module>:compileJvmTest`.
So the mechanism itself is available; `quarkusGenerateCodeTests` is still blocked behind §4.2.

### 4.4 Replacing the module's package output

`./kotlin package` produces a plain executable jar. There is no hook that lets a plugin replace or post-process a
module's packaging output, so the Quarkus artifact stays behind `./kotlin do quarkusBuild`. Gradle solves this by
making `quarkusBuild` a dependency of `build`.

### 4.5 Dependency exclusions are ignored ([KTC-5843](https://youtrack.jetbrains.com/issue/KTC-5843))

The toolchain resolves the transitive dependencies that a POM excludes. `quarkus-bootstrap-maven-resolver` declares
`quarkus-bootstrap-maven4-resolver` with a `*:*` exclusion so that a build running under Maven 3 never sees the
Maven 4 resolver stack. The plugin classpath gets it anyway, and the Maven 4 artifacts then win conflict
resolution: `maven-resolver-api` lands on 2.0.13 instead of 1.9.25, `maven-resolver-provider` on 4.0.0-rc-5 instead
of 3.9.16.

`smallrye-beanbag-maven`, which Quarkus uses to wire the repository system, is built for maven-resolver 1.9.x and
mis-wires the 2.x remote repository filters, so any artifact resolution fails with
`ClassCastException: DefaultMetadataResolver cannot be cast to RemoteRepositoryManager`.

Neither a direct version declaration in `module.yaml` nor importing `quarkus-bootstrap-bom` corrects the versions —
conflict resolution takes the highest. There is no `exclusions` key in `module.yaml` either.

Workaround in `Bootstrap.kt`: both broken components default to enabled in maven-resolver 2.x, and Quarkus copies
system properties into the resolver session, so `aether.remoteRepositoryFilter.prefixes` and
`aether.remoteRepositoryFilter.groupId` are set to `false` before the context is built. This covers the components
that actually break today, nothing more.

### 4.6 Maven metadata the toolchain does not apply

Two resolution gaps found while getting `@QuarkusTest` to run. Both are independent of §4.1 and of each other.

* **Relocation POMs are not followed.** `io.quarkus:quarkus-junit5:3.39.2` is a relocation to
  `io.quarkus:quarkus-junit`, declared in `distributionManagement/relocation`. The toolchain resolves the relocation
  artifact itself — a 5 KB JAR with nothing but `META-INF` — and reports no problem. Maven and Gradle follow the
  relocation. Workaround: name the target artifact.
* **A transitive dependency declared with `<exclusions>` and no version is dropped.** `quarkus-junit` declares
  `quarkus-bootstrap-core` that way; `io.quarkus.platform:quarkus-bom` manages its version, and every other child of
  `quarkus-junit` resolves. `quarkus-bootstrap-core` never appears in `./kotlin show dependencies`, with no warning,
  and the test JVM fails with `NoClassDefFoundError: io/quarkus/bootstrap/classloading/QuarkusClassLoader`.
  Workaround: declare it in `module.yaml` with an explicit version. Possibly the same root cause as §4.5.

Transitive versions that come from an artifact's own parent POM are applied correctly — `rest-assured` pulls
`org.apache.groovy:groovy:5.0.3` that way without help.

## 5. Recommended order

1. ~~§3.1 image build / push / deploy~~ — done.
2. ~~§3.2 use `module.classes`~~ — done.
3. ~~§3.3 `quarkusRun`~~ — done.
4. ~~§3.4 effective configuration~~ — done.
5. ~~§3.5 local module dependencies~~ — done.
6. §3.7 incrementality. **Next.**
7. File the two remaining KTC feature requests (§3.6 resolved dependencies without the module's own output; §4.1
   test-JVM properties; test-scope references). Also file the one-line `PathTestHelper` fallback against Quarkus,
   and the two resolution gaps of §4.6. Drafts are in `docs/tickets.md`. §4.5 is filed as KTC-5843.
   `quarkusTestModel` is shipped.
8. §3.8 dev mode.
9. §4.2 code generation, once §3.6 lands.
