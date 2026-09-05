package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.CuratedApplication
import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver
import io.quarkus.bootstrap.workspace.ArtifactSources
import io.quarkus.bootstrap.workspace.DefaultWorkspaceModule
import io.quarkus.bootstrap.workspace.SourceDir
import io.quarkus.bootstrap.workspace.WorkspaceModuleId
import io.quarkus.maven.dependency.ArtifactCoords
import io.quarkus.maven.dependency.ArtifactDependency
import io.quarkus.maven.dependency.Dependency
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipFile

internal fun bootstrapQuarkus(
    appJar: CompilationArtifact,
    runtimeClasspath: Classpath,
    moduleDir: Path,
    outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
    mode: QuarkusBootstrap.Mode,
    targetDirectory: Path = outputDir,
    extraBuildProperties: Map<String, String> = emptyMap(),
): CuratedApplication {
    val applicationRoot = outputDir.resolve("app-classes")
    unpackJar(appJar.artifact, applicationRoot)

    val dependencies = readDependencies(runtimeClasspath, appJar.artifact)
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
            ArtifactSources.main(
                SourceDir.of(moduleDir.resolve("src"), applicationRoot),
                SourceDir.of(moduleDir.resolve("resources"), applicationRoot),
            )
        )
        .addDependencyConstraint(platformBom(settings.platformBom, quarkusVersion))
        .setDependencies(dependencies.map(::toDependency))
        .build()

    disableRemoteRepositoryFilters()

    val mavenConfig = BootstrapMavenContext.config()
    mavenConfig.setWorkspaceDiscovery(false)
    val resolver = MavenArtifactResolver(BootstrapMavenContext(mavenConfig))
    val applicationModel = BootstrapAppModelResolver(resolver).resolveModel(module)

    println("Bootstrapping '$moduleName' with Quarkus $quarkusVersion in $mode mode")

    return QuarkusBootstrap.builder()
        .setExistingModel(applicationModel)
        .setApplicationRoot(applicationRoot)
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
 * The Kotlin Toolchain ignores Maven dependency exclusions, so the plugin classpath ends up with
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

private fun readDependencies(classpath: Classpath, appJar: Path): List<JarCoords> {
    val ownJar = appJar.toAbsolutePath().normalize()
    val coords = mutableListOf<JarCoords>()
    val unmapped = mutableListOf<Path>()
    for (file in classpath.resolvedFiles) {
        if (file.toAbsolutePath().normalize() == ownJar) continue
        val entry = readMavenCoords(file)
        if (entry == null) {
            unmapped.add(file)
        } else {
            coords.add(entry)
        }
    }
    if (unmapped.isNotEmpty()) {
        System.err.println("Quarkus plugin: skipping classpath entries that are not Maven artifacts:")
        unmapped.forEach { System.err.println("  $it") }
    }
    return coords
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
