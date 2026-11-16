package com.kreativekoala.cleanup.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards every shipped translation against the default (English) resources:
 * same keys, same format placeholders, required CLDR plural categories, and
 * a per-app language list that matches the shipped locales.
 * Runs on the JVM and reads the XML straight from src/main/res.
 */
class StringResourcesTest {

    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private data class Res(
        val strings: Map<String, String>,
        val formatted: Map<String, Boolean>,
        val plurals: Map<String, Map<String, String>>
    )

    private fun load(dir: File): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "strings.xml"))
        val strings = linkedMapOf<String, String>()
        val formatted = linkedMapOf<String, Boolean>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            when (n.tagName) {
                "string" -> {
                    if (n.getAttribute("translatable") == "false") continue
                    strings[n.getAttribute("name")] = n.textContent
                    formatted[n.getAttribute("name")] = n.getAttribute("formatted") != "false"
                }
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val kids = n.getElementsByTagName("item")
                    for (j in 0 until kids.length) {
                        val it = kids.item(j) as Element
                        items[it.getAttribute("quantity")] = it.textContent
                    }
                    plurals[n.getAttribute("name")] = items
                }
            }
        }
        return Res(strings, formatted, plurals)
    }

    private val default = load(File(resDir, "values"))

    private val localeDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
        .sortedBy { it.name }

    private val spec = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?([a-zA-Z])")

    /** Placeholders as "position:conversion", sequential when no explicit position. */
    private fun specs(text: String): List<String> {
        val cleaned = text.replace("%%", "")
        var next = 1
        return spec.findAll(cleaned).map { m ->
            val pos = m.groupValues[1].removeSuffix("$").ifEmpty { (next++).toString() }
            "$pos:${m.groupValues[3]}"
        }.sorted().toList()
    }

    private val required = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "ru" to setOf("one", "few", "many", "other"),
        "uk" to setOf("one", "few", "many", "other"),
        "cs" to setOf("one", "few", "many", "other"),
        "ro" to setOf("one", "few", "other"),
        "el" to setOf("one", "other"), "fi" to setOf("one", "other"),
        "hu" to setOf("one", "other"), "sv" to setOf("one", "other"),
        "pl" to setOf("one", "few", "many", "other"),
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "it" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "de" to setOf("one", "other"),
        "nl" to setOf("one", "other"),
        "tr" to setOf("one", "other"),
        "hi" to setOf("one", "other"),
        "tl" to setOf("one", "other"),
        "en" to setOf("one", "other"),
        "ja" to setOf("other"), "ko" to setOf("other"), "th" to setOf("other"), "vi" to setOf("other"),
        "zh" to setOf("other"), "in" to setOf("other"), "ms" to setOf("other")
    )

    @Test
    fun defaultPluralsHaveOneAndOther() {
        for ((name, items) in default.plurals) {
            assertEquals("plurals $name in values/", setOf("one", "other"), items.keys)
        }
    }

    @Test
    fun everyLocaleHasTheSameKeys() {
        for (dir in localeDirs) {
            val r = load(dir)
            assertEquals("${dir.name}: missing strings", emptySet<String>(), default.strings.keys - r.strings.keys)
            assertEquals("${dir.name}: extra strings", emptySet<String>(), r.strings.keys - default.strings.keys)
            assertEquals("${dir.name}: missing plurals", emptySet<String>(), default.plurals.keys - r.plurals.keys)
            assertEquals("${dir.name}: extra plurals", emptySet<String>(), r.plurals.keys - default.plurals.keys)
        }
    }

    @Test
    fun formatPlaceholdersMatchTheDefault() {
        for (dir in localeDirs) {
            val r = load(dir)
            for ((k, v) in r.strings) {
                val base = default.strings[k] ?: continue
                if (default.formatted[k] == false) continue // plain text with a literal %, no placeholders
                assertEquals("${dir.name}:$k", specs(base), specs(v))
            }
            for ((k, items) in r.plurals) {
                val base = specs(default.plurals[k]?.get("other") ?: continue)
                for ((q, v) in items) assertEquals("${dir.name}:$k[$q]", base, specs(v))
            }
        }
    }

    @Test
    fun pluralCategoriesMatchTheLanguage() {
        for (dir in localeDirs) {
            val lang = dir.name.removePrefix("values-").substringBefore("-r")
            val want = required[lang] ?: error("add plural categories for $lang")
            for ((k, items) in load(dir).plurals) assertEquals("${dir.name}:$k", want, items.keys)
        }
    }

    @Test
    fun percentSignsAreRenderedOnce() {
        for (dir in listOf(File(resDir, "values")) + localeDirs) {
            val r = load(dir)
            for ((k, v) in r.strings) {
                if (r.formatted[k] == false) assertTrue("${dir.name}:$k must not contain %%", !v.contains("%%"))
            }
        }
    }

    @Test
    fun localeConfigListsTheShippedLanguages() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "xml/locales_config.xml"))
        val listed = doc.getElementsByTagName("locale").let { l ->
            (0 until l.length).map { (l.item(it) as Element).getAttribute("android:name") }
        }.map { it.substringBefore("-").lowercase() }.toSet()
        val shipped = localeDirs.map { it.name.removePrefix("values-").substringBefore("-r") }
            .map { if (it == "in") "id" else it }.toSet() + "en"
        assertEquals(shipped, listed)
    }
}
