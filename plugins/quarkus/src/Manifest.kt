package io.heapy.ktc.quarkus

private const val MANIFEST_ATTRIBUTES = "quarkus.package.jar.manifest.attributes"
private const val MANIFEST_SECTIONS = "quarkus.package.jar.manifest.sections"

private const val IGNORED_ENTRIES = "quarkus.package.jar.user-configured-ignored-entries"

/** Quotes the name, so a `"` in it would produce a key that reads as two. */
private fun quote(kind: String, name: String): String {
    require('"' !in name) { "$kind '$name' is invalid: \" characters are not allowed." }
    return "\"$name\""
}

internal fun manifestProperties(settings: QuarkusSettings): Map<String, String> = buildMap {
    settings.manifestEntries.forEach { (key, value) ->
        put("$MANIFEST_ATTRIBUTES.${quote("Manifest entry name", key)}", value)
    }
    settings.manifestSections.forEach { (section, attributes) ->
        val name = quote("Manifest section name", section)
        attributes.forEach { (key, value) ->
            put("$MANIFEST_SECTIONS.$name.${quote("Manifest entry name", key)}", value)
        }
    }
}

internal fun ignoredEntriesProperties(settings: QuarkusSettings): Map<String, String> =
    if (settings.ignoredEntries.isEmpty()) {
        emptyMap()
    } else {
        mapOf(IGNORED_ENTRIES to settings.ignoredEntries.joinToString(","))
    }
