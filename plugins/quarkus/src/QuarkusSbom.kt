package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.QuarkusBootstrap
import io.quarkus.bootstrap.resolver.maven.EffectiveModelResolver
import io.quarkus.cyclonedx.generator.CycloneDxSbomGenerator
import io.quarkus.maven.dependency.ArtifactCoords
import io.quarkus.sbom.CoreSbomContributionConfig
import org.apache.maven.model.Model
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Files
import java.nio.file.Path

private const val FORMAT = "quarkus.dependency.sbom.format"
private const val SCHEMA_VERSION = "quarkus.dependency.sbom.schema-version"
private const val INCLUDE_LICENSE_TEXT = "quarkus.dependency.sbom.include-license-text"
private const val PRETTY_PRINT = "quarkus.dependency.sbom.pretty-print"
private const val RUNTIME_ONLY = "quarkus.dependency.sbom.runtime-only"
private const val COMPONENT_SCOPE = "quarkus.dependency.sbom.include-quarkus-component-scope"

private val FORMATS = setOf("json", "xml")

/** Writes a CycloneDX SBOM of the application, deployment dependencies included. */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusDependencySbom(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val format = System.getProperty(FORMAT) ?: "json"
    require(format in FORMATS) { "$FORMAT was set to '$format'. Choose one of ${FORMATS.sorted()}." }

    val mode = launchMode()
    val runtimeOnly = flag(RUNTIME_ONLY)
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
        mode = mode,
        runtimeOnly = runtimeOnly,
    )

    val file = outputDir.resolve(sbomFileName(settings.finalName ?: moduleName, settings.version, mode, format))
    Files.createDirectories(outputDir)

    val contribution = CoreSbomContributionConfig()
        .setApplicationModel(application.applicationModel)
        .toSbomContribution()

    CycloneDxSbomGenerator.newInstance()
        .setContributions(listOf(contribution))
        .setOutputFile(file)
        .setFormat(format)
        .setEffectiveModelResolver(ModuleModelResolver(application))
        .setSchemaVersion(System.getProperty(SCHEMA_VERSION))
        .setIncludeLicenseText(flag(INCLUDE_LICENSE_TEXT))
        .setPrettyPrint(flag(PRETTY_PRINT))
        .setRuntimeOnly(runtimeOnly)
        .setIncludeQuarkusComponentScope(flag(COMPONENT_SCOPE))
        .generate()

    println("SBOM: $file")
}

/**
 * The application artifact is synthetic: the toolchain publishes no POM for a module, so the generator cannot
 * resolve one for the root component. An empty model is the truth there; every other artifact comes from a
 * repository and resolves normally.
 */
private class ModuleModelResolver(application: QuarkusApplication) : EffectiveModelResolver {
    private val delegate = EffectiveModelResolver.of(application.resolver)
    private val group = application.settings.group
    private val artifact = application.moduleName

    override fun resolveEffectiveModel(coords: ArtifactCoords, repos: List<RemoteRepository>): Model =
        if (coords.groupId == group && coords.artifactId == artifact) {
            Model()
        } else {
            delegate.resolveEffectiveModel(coords, repos)
        }
}

private fun sbomFileName(
    baseName: String,
    version: String,
    mode: QuarkusBootstrap.Mode,
    format: String,
): String {
    val qualifier = if (mode == QuarkusBootstrap.Mode.PROD) "" else "${mode.name.lowercase()}-"
    return "$baseName-$version-${qualifier}dependency-cyclonedx.$format"
}
