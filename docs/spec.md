# Quarkus support in the Kotlin Toolchain plugin — specification

What the Quarkus Maven plugin (`devtools/maven`, 32 goals) and the Quarkus Gradle application plugin
(`devtools/gradle/gradle-application-plugin`, 27 tasks) do, and which of it the Kotlin Toolchain (KTC) plugin
in `plugins/quarkus` should implement.

Baseline: KTC `0.12.0`, Quarkus `3.39.2`, plugin source read at Quarkus commit `e1c73424`.
Today the KTC plugin implements two tasks: `quarkusBuild` and `quarkusNative`.

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
| Effective build configuration | `track-config-changes` | `quarkusShowEffectiveConfig` | **todo** | `EffectiveConfig`, `SmallRyeConfig` |
| Run the packaged application | `run` | `quarkusRun` | done | `AugmentAction.performCustomBuild(StartDevServicesAndRunCommandHandler)` |
| Container image build | `image-build` | `imageBuild` | **todo** | forces `quarkus.container-image.build` |
| Container image push | `image-push` | `imagePush` | **todo** | forces `quarkus.container-image.push` |
| Deploy (k8s / openshift / minikube / kind / knative) | `deploy` | `deploy` | **todo** | `DeployCommandDeclarationHandler`, `DeployCommandHandler` |
| Local module dependencies in the app model | `QuarkusMavenWorkspaceBuilder` | `ApplicationDeploymentClasspathBuilder` | done | `WorkspaceModule`, `ArtifactSources` |
| Incremental up-to-date check on configuration | `track-config-changes` | `quarkusShowEffectiveConfig` | **todo** | config dump file compared between builds |
| Code generation, main sources | `generate-code` | `quarkusGenerateCode` | **blocked** | `CodeGenerator.initAndRun(...)` |
| Code generation, dev sources | `generate-code` (`launchMode=DEVELOPMENT`) | `quarkusGenerateCodeDev` | blocked | same |
| Code generation, test sources | `generate-code-tests` | `quarkusGenerateCodeTests` | **blocked** | same |
| `@QuarkusTest` in the module's own test task | `argLine` injection in `GenerateCodeTestsMojo` | `BeforeTestAction` on `Test` | **blocked**, workaround in §4.1 | `BootstrapConstants.SERIALIZED_TEST_APP_MODEL` |
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

`QuarkusSettings` has 5 properties today. Maven and Gradle expose the following that KTC does not.

| Setting | Maven | Gradle | Effect | Priority |
|---|---|---|---|---|
| `finalName` | `finalName` | `finalName` | base name of the runner jar and native binary; sets `quarkus.build.base-name` | high |
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

#### Workaround

Everything except one environment variable can be done from the plugin.

* Add a `quarkusTestModel` task that writes, into `${taskOutputDir}`:
  * the serialized TEST-mode `ApplicationModel` (`ApplicationModelSerializer.serialize`);
  * a properties file with the values `BeforeTestAction` would have set as system properties;
  * a precompiled `LauncherSessionListener` class and its `META-INF/services` entry. The class cannot be a
    `generated.sources` entry, because compiling it needs `junit-platform-launcher` on the module's test compile
    classpath and a plugin cannot add dependencies. Ship the bytes from the plugin's own jar instead.
* Declare the output as a test-scope resource, which also wires the task into `compileJvmTest`:

  ```yaml
  generated:
    resources:
      - directory: ${tasks.quarkusTestModel.action.outputDir}
        fragment:
          isTest: true
  ```

* At test-session start the listener reads the properties file from the classpath and replays it through
  `System.setProperty`. Write the listener in Java inside the plugin module, so the class loaded from
  `resources-output` does not pull the plugin's Kotlin stdlib version into the test JVM.

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

End-to-end boot is not demonstrated yet: it waits on `quarkusTestModel` (§5.7). Two open points there — the
`WorkspaceModule` the plugin builds has no test `ArtifactSources`, and `PathTestHelper.getProjectBuildDir` has not
been checked against the `build/artifacts/...` layout.

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

## 5. Recommended order

1. §3.1 image build / push / deploy — thin commands, immediate value.
2. §3.2 use `module.classes` — removes the unpack step, prerequisite for dev mode.
3. §3.3 `quarkusRun` — small, high value.
4. §3.4 effective configuration — unblocks correct naming and §3.7.
5. §3.5 local module dependencies — removes the documented limitation.
6. §3.7 incrementality.
7. File the two remaining KTC feature requests (§3.6 resolved dependencies without the module's own output; §4.1
   test-JVM properties and test-scope references), and ship the `quarkusTestModel` workaround from §4.1.
   Also file the one-line `PathTestHelper` fallback against Quarkus. §4.5 is filed as KTC-5843.
8. §3.8 dev mode.
9. §4.2 code generation, once §3.6 lands.
