package io.heapy.ktc.quarkus

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MavenLayoutTest {
    @Test
    fun `reads coordinates with the group id declared in the pom`() {
        val jar = artifact("io/quarkus/quarkus-rest/3.39.2", "quarkus-rest-3.39.2", "<groupId>io.quarkus</groupId>")

        assertEquals(JarCoords("io.quarkus", "quarkus-rest", "3.39.2", null), readMavenCoords(jar))
    }

    @Test
    fun `falls back to the parent group id`() {
        val jar = artifact(
            "org/example/lib/1.0",
            "lib-1.0",
            "<parent><groupId>org.example</groupId><artifactId>parent</artifactId></parent>",
        )

        assertEquals(JarCoords("org.example", "lib", "1.0", null), readMavenCoords(jar))
    }

    @Test
    fun `reads the classifier`() {
        val jar = artifact(
            "io/netty/netty-transport-native-epoll/4.2.13.Final",
            "netty-transport-native-epoll-4.2.13.Final-linux-aarch_64",
            "<groupId>io.netty</groupId>",
            pomName = "netty-transport-native-epoll-4.2.13.Final",
        )

        assertEquals(
            JarCoords("io.netty", "netty-transport-native-epoll", "4.2.13.Final", "linux-aarch_64"),
            readMavenCoords(jar),
        )
    }

    @Test
    fun `rejects a jar outside of a maven layout`() {
        val jar = tempDir.resolve("build/tasks/_app_jarJvm/app-jvm.jar")
        jar.parent.createDirectories()
        jar.writeText("")

        assertNull(readMavenCoords(jar))
    }

    private val tempDir: Path = Files.createTempDirectory("maven-layout-test")

    private fun artifact(
        directory: String,
        jarName: String,
        pomBody: String,
        pomName: String = jarName,
    ): Path {
        val dir = tempDir.resolve(directory)
        dir.createDirectories()
        dir.resolve("$pomName.pom").writeText("<project>$pomBody<artifactId>ignored</artifactId></project>")
        val jar = dir.resolve("$jarName.jar")
        jar.writeText("")
        return jar
    }
}
