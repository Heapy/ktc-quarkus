package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.ApplicationModelSerializer
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val SERIALIZED_TEST_APP_MODEL = "quarkus-internal-test.serialized-app-model.path"
private const val OUTPUT_SOURCES_DIR = "OUTPUT_SOURCES_DIR"

private const val SERVICES_RESOURCE = "META-INF/services/org.junit.platform.launcher.LauncherSessionListener"

/** Named, not referenced: the listener implements a compile-only interface that the plugin JVM does not have. */
private const val LISTENER_CLASS = "io.heapy.ktc.quarkus.test.QuarkusTestListener"
private const val PROPERTIES_RESOURCE = "META-INF/ktc-quarkus-test.properties"

/**
 * Writes what `@QuarkusTest` needs from the build: the application model, and the system properties that point at it.
 * The toolchain has no way for a plugin to set system properties on the test JVM, so the values travel as a test
 * resource and a `LauncherSessionListener` applies them. See `docs/spec.md` §4.1.
 */
@TaskAction
fun quarkusTestModel(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output modelDir: Path,
    @Output testResourcesDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = modelDir,
        moduleName = moduleName,
        settings = settings,
    )

    val modelFile = modelDir.resolve("app-model.dat")
    Files.createDirectories(modelDir)
    ApplicationModelSerializer.serialize(application.applicationModel, modelFile)

    val properties = Properties()
    properties.setProperty(SERIALIZED_TEST_APP_MODEL, modelFile.toAbsolutePath().toString())
    properties.setProperty(OUTPUT_SOURCES_DIR, outputSourcesDir(classes, resources))

    writeIfChanged(
        testResourcesDir.resolve(PROPERTIES_RESOURCE),
        render(properties).toByteArray(),
    )
    writeIfChanged(
        testResourcesDir.resolve(SERVICES_RESOURCE),
        "$LISTENER_CLASS\n".toByteArray(),
    )
    writeIfChanged(
        testResourcesDir.resolve(listenerResource()),
        readListenerClass(),
    )

    println("Quarkus test model: $modelFile")
}

private fun outputSourcesDir(classes: CompilationArtifact, resources: ModuleSources): String =
    (listOf(classes.artifact) + resources.sourceDirectories)
        .joinToString(",") { it.toAbsolutePath().toString() }

/** `Properties.store` stamps the current time into a comment, which would make every build dirty. */
private fun render(properties: Properties): String {
    val writer = StringWriter()
    properties.store(writer, null)
    return writer.toString()
        .lineSequence()
        .filterNot { it.startsWith("#") }
        .joinToString("\n")
}

private fun listenerResource(): String =
    LISTENER_CLASS.replace('.', '/') + ".class"

private fun readListenerClass(): ByteArray {
    val resource = listenerResource()
    val stream = QuarkusSettings::class.java.classLoader.getResourceAsStream(resource)
        ?: error("The Quarkus plugin classpath has no $resource")
    return stream.use { it.readBytes() }
}

private fun writeIfChanged(file: Path, content: ByteArray) {
    if (Files.exists(file) && Files.readAllBytes(file).contentEquals(content)) {
        return
    }
    Files.createDirectories(file.parent)
    Files.write(file, content)
}
