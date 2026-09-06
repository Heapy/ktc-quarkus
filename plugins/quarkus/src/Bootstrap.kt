package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.CuratedApplication
import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.quarkus.bootstrap.model.ApplicationModel
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver
import io.quarkus.bootstrap.workspace.ArtifactSources
import io.quarkus.bootstrap.workspace.DefaultArtifactSources
import io.quarkus.bootstrap.workspace.DefaultWorkspaceModule
import io.quarkus.bootstrap.workspace.SourceDir
import io.quarkus.bootstrap.workspace.WorkspaceModuleId
import io.quarkus.maven.dependency.ArtifactCoords
import io.quarkus.maven.dependency.ArtifactDependency
import io.quarkus.maven.dependency.Dependency
import io.quarkus.paths.PathList
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ModuleSources
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipFile

/**
 * A resolved application model that can be bootstrapped several times. `quarkusDeploy` asks the extensions which
 * deployers they support before it knows which properties to force, and Quarkus bakes the build system properties
 * into the bootstrap, so the second bootstrap has to reuse the model of the first one.
 */
internal class QuarkusApplication(
    private val applicationModel: ApplicationModel,
    private val applicationRoots: PathList,
    private val moduleDir: Path,
    private val outputDir: Path,
    private val moduleName: String,
    private val settings: QuarkusSettings,
    private val quarkusVersion: String,
    val artifactIds: Set<String>,
) {
    fun bootstrap(
        mode: QuarkusBootstrap.Mode,
        targetDirectory: Path = outputDir,
        extraBuildProperties: Map<String, String> = emptyMap(),
    ): CuratedApplication {
        println("Bootstrapping '$moduleName' with Quarkus $quarkusVersion in $mode mode")

        return QuarkusBootstrap.builder()
            .setExistingModel(applicationModel)
            .setApplicationRoot(applicationRoots)
            .setProjectRoot(moduleDir)
            .setTargetDirectory(targetDirectory)
            .setBaseName(moduleName)
            .setBaseClassLoader(QuarkusBootstrap::class.java.classLoader)
            .setIsolateDeployment(true)
            .setMode(mode)
            .setBuildSystemProperties(buildSystemProperties(moduleName, settings, extraBuildProperties))
            .build()
            .bootstrap()
    }
}

internal fun resolveApplication(
    classes: CompilationArtifact,
    resources: ModuleSources,
    runtimeClasspath: Classpath,
    moduleDir: Path,
    outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
): QuarkusApplication {
    val classpath = readClasspath(runtimeClasspath, moduleName)
    val applicationSources = applicationSources(moduleDir, classes.artifact, outputDir, classpath.localModules)
    val resourceSources = resources.sourceDirectories.map { SourceDir.of(it, it) }
    val dependencies = classpath.mavenArtifacts
    val quarkusVersion = dependencies
        .firstOrNull { it.groupId == "io.quarkus" && it.artifactId == "quarkus-core" }
        ?.version
        ?: error(
            "Module '$moduleName' has no Quarkus on its runtime classpath. " +
                "Add a Quarkus extension, for example io.quarkus:quarkus-rest."
        )

    warnOnBootstrapSkew(quarkusVersion)

    val module = DefaultWorkspaceModule.builder()
        .setModuleId(WorkspaceModuleId.of(settings.group, moduleName, settings.version))
        .setModuleDir(moduleDir)
        .setBuildDir(outputDir)
        .addArtifactSources(
            DefaultArtifactSources(
                ArtifactSources.MAIN,
                applicationSources,
                resourceSources,
            )
        )
        .addDependencyConstraint(platformBom(settings.platformBom, quarkusVersion))
        .setDependencies(dependencies.map(::toDependency))
        .build()

    disableRemoteRepositoryFilters()

    val mavenConfig = BootstrapMavenContext.config()
    mavenConfig.setWorkspaceDiscovery(false)
    val resolver = MavenArtifactResolver(BootstrapMavenContext(mavenConfig))

    return QuarkusApplication(
        applicationModel = BootstrapAppModelResolver(resolver).resolveModel(module),
        applicationRoots = PathList.from((applicationSources + resourceSources).map { it.outputDir }),
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        quarkusVersion = quarkusVersion,
        artifactIds = dependencies.mapTo(mutableSetOf()) { it.artifactId },
    )
}

internal fun CuratedApplication.buildProductionApplication() {
    val result = createAugmentor().createProductionApplication()
    println("Quarkus application: ${result.nativeResult ?: result.jar?.path}")
}

private fun buildSystemProperties(
    moduleName: String,
    settings: QuarkusSettings,
    extra: Map<String, String>,
): Properties {
    val properties = Properties()
    properties.setProperty("quarkus.application.name", moduleName)
    properties.setProperty("quarkus.application.version", settings.version)
    extra.forEach { (key, value) -> properties.setProperty(key, value) }
    settings.buildProperties.forEach { (key, value) -> properties.setProperty(key, value) }
    return properties
}

private fun warnOnBootstrapSkew(quarkusVersion: String) {
    val bootstrapVersion = bootstrapVersion() ?: return
    if (bootstrapVersion != quarkusVersion) {
        System.err.println(
            "Quarkus plugin: the plugin runs Quarkus bootstrap $bootstrapVersion but the module depends on " +
                "Quarkus $quarkusVersion. Align them in plugins/quarkus/module.yaml."
        )
    }
}

private fun bootstrapVersion(): String? {
    val resource = QuarkusBootstrap::class.java.classLoader
        .getResourceAsStream("META-INF/maven/io.quarkus/quarkus-bootstrap-core/pom.properties")
        ?: return null
    return resource.use {
        Properties().apply { load(it) }.getProperty("version")
    }
}

/**
 * KTC-5843: the Kotlin Toolchain ignores Maven dependency exclusions, so the plugin classpath ends up with
 * maven-resolver 2.x while smallrye-beanbag wires it as 1.9.x. The remote repository filters are the
 * only components that break under that wiring, and Quarkus copies system properties into the
 * resolver session, so turning them off here keeps resolution working.
 */
private fun disableRemoteRepositoryFilters() {
    System.setProperty("aether.remoteRepositoryFilter.prefixes", "false")
    System.setProperty("aether.remoteRepositoryFilter.groupId", "false")
}

private fun platformBom(coords: String?, quarkusVersion: String): Dependency {
    if (coords == null) {
        return Dependency.pomImport("io.quarkus", "quarkus-bom", quarkusVersion)
    }
    val parts = coords.split(":")
    require(parts.size == 3) { "platformBom must be 'groupId:artifactId:version', got '$coords'" }
    return Dependency.pomImport(parts[0], parts[1], parts[2])
}

private fun toDependency(coords: JarCoords): Dependency =
    ArtifactDependency(
        coords.groupId,
        coords.artifactId,
        coords.classifier,
        ArtifactCoords.TYPE_JAR,
        coords.version,
    )

/**
 * A local module is part of the application, not a resolved artifact, so its classes are unpacked next to the
 * module's own classes and registered as another source directory of the application.
 */
private fun applicationSources(
    moduleDir: Path,
    classesDir: Path,
    outputDir: Path,
    localModules: Map<String, Path>,
): List<SourceDir> {
    val sources = mutableListOf(SourceDir.of(moduleDir.resolve("src"), classesDir))
    for ((name, jar) in localModules) {
        val root = outputDir.resolve("local").resolve(name)
        unpackJar(jar, root)
        sources.add(SourceDir.of(moduleDir.resolveSibling(name).resolve("src"), root))
    }
    return sources
}

private class ClasspathEntries(
    val mavenArtifacts: List<JarCoords>,
    val localModules: Map<String, Path>,
)

private fun readClasspath(classpath: Classpath, moduleName: String): ClasspathEntries {
    val coords = mutableListOf<JarCoords>()
    val localModules = linkedMapOf<String, Path>()
    val unmapped = mutableListOf<Path>()
    for (file in classpath.resolvedFiles) {
        val coordinates = readMavenCoords(file)
        val localModule = localModuleName(file)
        when {
            coordinates != null -> coords.add(coordinates)
            localModule == moduleName -> Unit
            localModule != null -> localModules[localModule] = file
            else -> unmapped.add(file)
        }
    }
    if (unmapped.isNotEmpty()) {
        System.err.println("Quarkus plugin: skipping classpath entries that are neither Maven artifacts nor modules:")
        unmapped.forEach { System.err.println("  $it") }
    }
    return ClasspathEntries(coords, localModules)
}

private val LOCAL_MODULE_DIR = Regex("^_(.+)_jarJvm$")

/** Matches the directory the Kotlin Toolchain writes a module JAR into. That layout is not a public contract. */
private fun localModuleName(jar: Path): String? {
    val directory = jar.parent?.fileName?.toString() ?: return null
    val name = LOCAL_MODULE_DIR.matchEntire(directory)?.groupValues?.get(1) ?: return null
    return name.takeIf { jar.fileName?.toString() == "$it-jvm.jar" }
}

private fun unpackJar(jar: Path, target: Path) {
    target.toFile().deleteRecursively()
    Files.createDirectories(target)
    ZipFile(jar.toFile()).use { zip ->
        for (entry in zip.entries()) {
            val destination = target.resolve(entry.name).normalize()
            require(destination.startsWith(target)) { "Entry '${entry.name}' escapes $target" }
            if (entry.isDirectory) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                zip.getInputStream(entry).use {
                    Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
