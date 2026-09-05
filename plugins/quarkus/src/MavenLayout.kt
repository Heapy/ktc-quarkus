package io.heapy.ktc.quarkus

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

internal data class JarCoords(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String?,
)

/**
 * Reads Maven coordinates of a JAR that sits in a Maven repository layout
 * (`<repo>/<group as dirs>/<artifactId>/<version>/<artifactId>-<version>[-<classifier>].jar`).
 * The group is taken from the sibling POM because the repository root is unknown.
 */
internal fun readMavenCoords(jar: Path): JarCoords? {
    val fileName = jar.fileName?.toString() ?: return null
    if (!fileName.endsWith(".jar")) return null
    val version = jar.parent?.fileName?.toString() ?: return null
    val artifactId = jar.parent?.parent?.fileName?.toString() ?: return null

    val prefix = "$artifactId-$version"
    val stem = fileName.removeSuffix(".jar")
    if (!stem.startsWith(prefix)) return null
    val classifier = stem.removePrefix(prefix).removePrefix("-").ifEmpty { null }

    val pom = jar.resolveSibling("$prefix.pom")
    if (!Files.isRegularFile(pom)) return null
    val groupId = readPomGroupId(pom) ?: return null

    return JarCoords(groupId, artifactId, version, classifier)
}

private fun readPomGroupId(pom: Path): String? {
    val factory = XMLInputFactory.newInstance()
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)

    Files.newInputStream(pom).use { input ->
        val reader = factory.createXMLStreamReader(input)
        var depth = 0
        var inParent = false
        var parentGroupId: String? = null
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    when {
                        depth == 2 && reader.localName == "groupId" -> return reader.elementText
                        depth == 2 && reader.localName == "parent" -> inParent = true
                        depth == 3 && inParent && reader.localName == "groupId" -> {
                            parentGroupId = reader.elementText
                            depth--
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (depth == 2 && reader.localName == "parent") inParent = false
                    depth--
                }
            }
        }
        return parentGroupId
    }
}
