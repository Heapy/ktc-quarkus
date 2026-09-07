package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.BootstrapConstants
import io.quarkus.bootstrap.app.ApplicationModelSerializer
import io.quarkus.bootstrap.app.ConfiguredClassLoading
import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.quarkus.bootstrap.model.ApplicationModel
import io.quarkus.bootstrap.model.PathsCollection
import io.quarkus.deployment.dev.DevModeCommandLine
import io.quarkus.deployment.dev.DevModeCommandLineBuilder
import io.quarkus.deployment.dev.DevModeContext
import io.quarkus.maven.dependency.ArtifactCoords
import io.quarkus.maven.dependency.ArtifactKey
import io.quarkus.paths.PathList
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.IOException
import java.nio.file.FileSystemLoopException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

private const val ALL_OPEN_PLUGIN = "org.jetbrains.kotlin:kotlin-allopen-compiler-plugin-embeddable"

/**
 * The toolchain's `presets` is an enum the plugin cannot read, so the Quarkus preset is assumed. It covers only
 * the `javax.enterprise.context` annotations, which Quarkus 3 no longer uses, so it changes nothing on its own -
 * ArC transforms unproxyable classes instead. Declared annotations are forwarded and do have an effect.
 */
private const val ALL_OPEN_QUARKUS_PRESET = "all-open:preset=quarkus"

private const val IDE_LAUNCHER = "quarkus-ide-launcher"
private const val CLASS_CHANGE_AGENT = "quarkus-class-change-agent"
private const val CORE_DEPLOYMENT = "quarkus-core-deployment"
private const val MAVEN_RESOLVER = "quarkus-bootstrap-maven-resolver"

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusDev(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
    allOpen: Boolean,
    allOpenAnnotations: List<String>?,
    kotlinVersion: String,
    jvmRelease: Int?,
) {
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = QuarkusBootstrap.Mode.DEV,
    )

    val dev = settings.dev
    val config = application.effectiveConfig(QuarkusBootstrap.Mode.DEV)
    val devClasses = stage(
        outputDir.resolve("dev-classes"),
        listOf(application.classesDir) + application.localModuleRoots.values,
    )
    val devResources = stage(outputDir.resolve("dev-resources"), application.resourceDirectories)

    val builder = DevModeCommandLine.builder(null)
        .projectDir(moduleDir.toFile())
        .buildDir(outputDir.toFile())
        .outputDir(outputDir.toFile())
        .baseName(config.baseName)
        .applicationName(moduleName)
        .applicationVersion(settings.version)
        .forceC2(dev.forceC2)
        .debug(dev.debug ?: System.getProperty("debug"))
        .suspend(dev.suspend ?: System.getProperty("suspend"))
        .debugHost(dev.debugHost ?: System.getProperty("debugHost"))
        .debugPort(dev.debugPort ?: System.getProperty("debugPort"))
        .extensionDevModeConfig(application.applicationModel.extensionDevModeConfig)
        .mainModule(mainModule(application, devClasses, devResources))

    config.buildSystemProperties.forEach { (key, value) ->
        builder.buildSystemProperty(key.toString(), value.toString())
    }
    dev.jvmArgs.forEach(builder::jvmArgs)
    if (dev.openJavaLang) {
        builder.addOpens("java.base/java.lang=ALL-UNNAMED")
    }
    if (dev.modules.isNotEmpty()) {
        builder.addModules(dev.modules)
    }
    if (jvmRelease != null) {
        builder.releaseJavaVersion(jvmRelease.toString())
        builder.sourceJavaVersion(jvmRelease.toString())
        builder.targetJavaVersion(jvmRelease.toString())
    }
    if (dev.compilerArgs.isNotEmpty()) {
        builder.compilerOptions("kotlin", dev.compilerArgs)
    }
    if (allOpen) {
        builder.compilerPluginArtifacts(listOf(allOpenPlugin(application, kotlinVersion)))
        builder.compilerPluginOptions(
            listOf(ALL_OPEN_QUARKUS_PRESET) + allOpenAnnotations.orEmpty().map { "all-open:annotation=$it" }
        )
    }
    if (dev.arguments.isNotEmpty()) {
        builder.applicationArgs(dev.arguments.joinToString(" "))
    }

    addDevModeClasspath(builder, application)
    addSerializedModel(builder, application.applicationModel, outputDir)

    val command = builder.build()
    val workingDirectory = dev.workingDirectory?.let(moduleDir::resolve) ?: moduleDir
    val exitCode = runProcess(command.arguments, dev.environment, workingDirectory)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

/**
 * Dev mode takes exactly one classes directory and one resources directory as the application root, so the
 * module's own classes and the unpacked local modules are staged into one tree. Quarkus recompiles into that
 * tree, which keeps the toolchain's own compilation output untouched.
 */
private fun mainModule(
    application: QuarkusApplication,
    devClasses: Path,
    devResources: Path,
): DevModeContext.ModuleInfo {
    val sources = listOf(application.moduleDir.resolve("src")) + application.localModuleSources.values
    val existingSources = sources.filter { it.isDirectory() }

    return DevModeContext.ModuleInfo.Builder()
        .setArtifactKey(ArtifactKey.of(application.settings.group, application.moduleName, null, ArtifactCoords.TYPE_JAR))
        .setName(application.moduleName)
        .setProjectDirectory(application.moduleDir.toString())
        .setSourcePaths(PathList.from(existingSources))
        .setSourceParents(PathList.from(existingSources.map { it.parent }))
        .setClassesPath(devClasses.toString())
        .setResourcePaths(PathList.from(application.resourceDirectories))
        .setResourcesOutputPath(devResources.toString())
        .setPreBuildOutputDir(application.outputDir.resolve("generated-sources").toString())
        .setTargetDir(application.outputDir.toString())
        .build()
}

/**
 * The dev-mode process runs `DevModeMain` from `quarkus-core-deployment`, which is not on the application's own
 * runtime classpath. Mirrors `QuarkusDev.addQuarkusDevModeDeps`.
 */
private fun addDevModeClasspath(builder: DevModeCommandLineBuilder, application: QuarkusApplication) {
    val deploymentVersion = application.applicationModel.dependencies
        .firstOrNull { it.isDeploymentCp && it.groupId == "io.quarkus" && it.artifactId == CORE_DEPLOYMENT }
        ?.version
        ?: error("io.quarkus:$CORE_DEPLOYMENT is not on the deployment classpath of '${application.moduleName}'")

    val root = DefaultArtifact("io.quarkus", CORE_DEPLOYMENT, "jar", deploymentVersion)
    val resolver = DefaultArtifact("io.quarkus", MAVEN_RESOLVER, "jar", deploymentVersion)
    val resolved = application.resolver
        .resolveDependencies(root, listOf(Dependency(resolver, "runtime")))
        .artifactResults
        .map { it.artifact }

    for (artifact in resolved) {
        val path = artifact.path ?: continue
        when (artifact.artifactId) {
            IDE_LAUNCHER -> Unit
            CLASS_CHANGE_AGENT -> builder.jvmArgs("-javaagent:${path.toAbsolutePath()}")
            else -> builder.classpathEntry(
                ArtifactKey.of(artifact.groupId, artifact.artifactId, artifact.classifier.ifEmpty { null }, artifact.extension),
                path.toFile(),
            )
        }
    }

    val parentFirst = ConfiguredClassLoading.builder()
        .setApplicationModel(application.applicationModel)
        .setApplicationRoot(PathsCollection.from(application.resourceDirectories))
        .setMode(QuarkusBootstrap.Mode.DEV)
        .build()
        .parentFirstArtifacts

    for (dependency in application.applicationModel.dependencies) {
        if (dependency.key !in parentFirst) continue
        dependency.resolvedPaths.forEach { path ->
            if (Files.exists(path)) {
                builder.classpathEntry(dependency.key, path.toFile())
            }
        }
    }
}

/** Dev mode reads the model from disk; the test model is the same one, as in §4.1 of the specification. */
private fun addSerializedModel(builder: DevModeCommandLineBuilder, model: ApplicationModel, outputDir: Path) {
    val file = outputDir.resolve("dev-app-model.dat")
    Files.createDirectories(outputDir)
    ApplicationModelSerializer.serialize(model, file)
    builder.jvmArgs("-D${BootstrapConstants.SERIALIZED_APP_MODEL}=${file.toAbsolutePath()}")
    builder.jvmArgs("-D${BootstrapConstants.SERIALIZED_TEST_APP_MODEL}=${file.toAbsolutePath()}")
}

private fun allOpenPlugin(application: QuarkusApplication, kotlinVersion: String): String {
    val (group, artifact) = ALL_OPEN_PLUGIN.split(":")
    val resolved = application.resolver
        .resolve(DefaultArtifact(group, artifact, "jar", kotlinVersion))
        .artifact
        .path
        ?: error("Failed to resolve $ALL_OPEN_PLUGIN:$kotlinVersion")
    return resolved.toAbsolutePath().toString()
}

private fun stage(target: Path, sources: List<Path>): Path {
    target.toFile().deleteRecursively()
    Files.createDirectories(target)
    for (source in sources) {
        if (!source.isDirectory()) continue
        Files.walkFileTree(source, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, copyInto(source, target))
    }
    return target
}

/**
 * Symlinked directories are followed, so a linked resource tree is staged rather than left empty. Following them
 * makes the walk report a cycle instead of skipping it, and a link back into the project is not an error here.
 */
private fun copyInto(source: Path, target: Path) = object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
        Files.createDirectories(destinationOf(directory))
        return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
        Files.copy(file, destinationOf(file), StandardCopyOption.REPLACE_EXISTING)
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, failure: IOException): FileVisitResult {
        if (failure !is FileSystemLoopException) throw failure
        System.err.println("Quarkus plugin: not staging $file, it links back into the tree being staged.")
        return FileVisitResult.CONTINUE
    }

    private fun destinationOf(path: Path): Path = target.resolve(source.relativize(path).toString())
}
