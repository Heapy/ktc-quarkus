package io.heapy.ktc.quarkus

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * `Properties.store` stamps the current time into a comment and writes entries in hash order, either of which would
 * make the file differ between two identical builds. Sorting the escaped lines is safe because `store` writes one
 * logical line per entry.
 *
 * The `OutputStream` overload escapes non-ASCII to `\uXXXX`, so the result round-trips through the
 * `Properties.load(InputStream)` that reads these files back, which decodes ISO-8859-1.
 */
internal fun renderProperties(values: Map<String, String>): String {
    val properties = Properties()
    values.forEach { (key, value) -> properties.setProperty(key, value) }
    val bytes = ByteArrayOutputStream()
    properties.store(bytes, null)
    return bytes.toString(Charsets.ISO_8859_1)
        .lineSequence()
        .filterNot { it.startsWith("#") || it.isEmpty() }
        .sorted()
        .joinToString(separator = "\n", postfix = "\n")
}

/** The toolchain compares modification times, so an unconditional write would make every build dirty. */
internal fun writeIfChanged(file: Path, content: ByteArray) {
    if (Files.exists(file) && Files.readAllBytes(file).contentEquals(content)) {
        return
    }
    Files.createDirectories(file.parent)
    Files.write(file, content)
}
