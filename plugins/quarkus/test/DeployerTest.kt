package io.heapy.ktc.quarkus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeployerTest {
    @Test
    fun `an explicit deployer wins over the classpath`() {
        assertEquals(
            Deployer.MINIKUBE,
            selectDeployer("minikube", emptyMap(), setOf("quarkus-openshift", "quarkus-kubernetes")),
        )
    }

    @Test
    fun `a build property wins over the classpath`() {
        assertEquals(
            Deployer.KNATIVE,
            selectDeployer(null, mapOf("quarkus.knative.deploy" to "true"), setOf("quarkus-kubernetes")),
        )
    }

    @Test
    fun `the specific extension wins over the kubernetes it depends on`() {
        assertEquals(
            Deployer.OPENSHIFT,
            selectDeployer(null, emptyMap(), setOf("quarkus-openshift", "quarkus-kubernetes")),
        )
        assertEquals(
            Deployer.MINIKUBE,
            selectDeployer(null, emptyMap(), setOf("quarkus-minikube", "quarkus-kubernetes")),
        )
    }

    @Test
    fun `plain kubernetes is still recognised`() {
        assertEquals(Deployer.KUBERNETES, selectDeployer(null, emptyMap(), setOf("quarkus-kubernetes")))
    }

    @Test
    fun `kubernetes is the fallback when nothing is on the classpath`() {
        assertEquals(Deployer.KUBERNETES, selectDeployer(null, emptyMap(), setOf("quarkus-rest")))
    }

    @Test
    fun `rejects an unknown deployer`() {
        assertFailsWith<IllegalStateException> { selectDeployer("nomad", emptyMap(), emptySet()) }
    }
}
