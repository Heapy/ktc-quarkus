package io.heapy.ktc.quarkus

import io.quarkus.bootstrap.app.CuratedApplication
import io.quarkus.bootstrap.app.QuarkusBootstrap
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.function.Consumer

private const val DECLARATION_HANDLER = "io.quarkus.deployment.cmd.DeployCommandDeclarationHandler"
private const val DECLARATION_RESULT = "io.quarkus.deployment.cmd.DeployCommandDeclarationResultBuildItem"
private const val DEPLOY_HANDLER = "io.quarkus.deployment.cmd.DeployCommandHandler"
private const val DEPLOY_RESULT = "io.quarkus.deployment.cmd.DeployCommandActionResultBuildItem"

private const val DEPLOY_TARGET = "quarkus.deploy.target"

internal enum class Deployer(val extension: String) {
    KUBERNETES("quarkus-kubernetes"),
    MINIKUBE("quarkus-minikube"),
    KIND("quarkus-kind"),
    KNATIVE("quarkus-kubernetes"),
    OPENSHIFT("quarkus-openshift");

    val id: String = name.lowercase()
}

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun quarkusDeploy(
    @Input classes: CompilationArtifact,
    @Input resources: ModuleSources,
    @Input runtimeClasspath: Classpath,
    @Input moduleDir: Path,
    @Output outputDir: Path,
    moduleName: String,
    settings: QuarkusSettings,
) {
    val application = resolveApplication(
        classes = classes,
        resources = resources,
        runtimeClasspath = runtimeClasspath,
        moduleDir = moduleDir,
        outputDir = outputDir,
        moduleName = moduleName,
        settings = settings,
    )
    val target = settings.deploy.target ?: System.getProperty(DEPLOY_TARGET)

    val deployed = application.bootstrap(QuarkusBootstrap.Mode.PROD).use { bootstrapped ->
        deployThroughExtension(bootstrapped, target)
    }
    if (!deployed) {
        deployThroughConfiguration(application, settings)
    }
}

/**
 * The path an extension takes when it declares a deploy command. Returns false when no extension declares one,
 * which is still the common case: the Kubernetes and OpenShift extensions deploy through configuration instead.
 */
private fun deployThroughExtension(application: CuratedApplication, target: String?): Boolean {
    val declared = declaredTargets(application)
    if (declared.isEmpty() && target == null) return false

    val selected = when {
        target == null && declared.size > 1 -> error(
            "Several extensions support deployment: ${declared.sorted()}. " +
                "Choose one with the 'deploy.target' plugin setting."
        )

        target != null && target !in declared -> error(
            "Deploy target '$target' is not among ${declared.sorted()}"
        )

        else -> target ?: declared.first()
    }

    println("Deploy target: $selected")
    val previous = System.getProperty(DEPLOY_TARGET)
    System.setProperty(DEPLOY_TARGET, selected)
    try {
        application.createAugmentor().performCustomBuild(DEPLOY_HANDLER, Consumer<Boolean> { }, DEPLOY_RESULT)
    } finally {
        if (previous == null) System.clearProperty(DEPLOY_TARGET) else System.setProperty(DEPLOY_TARGET, previous)
    }
    return true
}

private fun declaredTargets(application: CuratedApplication): List<String> {
    var declared = emptyList<String>()
    val consumer = Consumer<List<String>> { declared = it }
    application.createAugmentor().performCustomBuild(DECLARATION_HANDLER, consumer, DECLARATION_RESULT)
    return declared
}

/** Forces `quarkus.<deployer>.deploy` and runs a normal production build, which is what the deployer hooks into. */
private fun deployThroughConfiguration(application: QuarkusApplication, settings: QuarkusSettings) {
    val deploy = settings.deploy
    val deployer = selectDeployer(deploy.deployer, settings.buildProperties, application.artifactIds)
    check(deployer.extension in application.artifactIds) {
        "Deployer '${deployer.id}' needs the ${deployer.extension} extension. " +
            "Add it to the dependencies of the module."
    }

    val imageBuilder = deploy.imageBuilder
    val properties = buildMap {
        put("quarkus.${deployer.id}.deploy", "true")
        put(CONTAINER_IMAGE_BUILD, (deploy.imageBuild || imageBuilder != null).toString())
        if (imageBuilder != null) {
            put(CONTAINER_IMAGE_BUILDER, selectImageBuilder(imageBuilder, application.artifactIds))
        }
    }

    println("Deploying with '${deployer.id}'")

    application.bootstrap(
        mode = QuarkusBootstrap.Mode.PROD,
        extraBuildProperties = properties,
    ).use { it.buildProductionApplication() }
}

/**
 * Every other deployer extension depends on `quarkus-kubernetes`, so the classpath alone can never single out
 * Kubernetes; the deployers that name their own extension are matched first and it stays the fallback.
 */
internal fun selectDeployer(
    configured: String?,
    buildProperties: Map<String, String>,
    artifactIds: Set<String>,
): Deployer {
    if (configured != null) {
        return Deployer.entries.firstOrNull { it.id == configured }
            ?: error("Unknown deployer '$configured'. Choose one of ${Deployer.entries.map { it.id }}.")
    }
    return Deployer.entries.firstOrNull { buildProperties["quarkus.${it.id}.deploy"] == "true" }
        ?: Deployer.entries
            .sortedBy { it.extension == Deployer.KUBERNETES.extension }
            .firstOrNull { it.extension in artifactIds }
        ?: Deployer.KUBERNETES
}
