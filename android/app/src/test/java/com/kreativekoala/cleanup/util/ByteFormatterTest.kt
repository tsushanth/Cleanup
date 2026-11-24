package com.kreativekoala.cleanup.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

class ByteFormatterTest {
    private val en = listOf("%1\$s B", "%1\$s KB", "%1\$s MB", "%1\$s GB", "%1\$s TB")
    private val ru = listOf("%1\$s Б", "%1\$s КБ", "%1\$s МБ", "%1\$s ГБ", "%1\$s ТБ")

    @Test
    fun smallValuesUseBytesWithoutDecimals() {
        assertEquals("0 B", ByteFormatter.format(0, Locale.US, en))
        assertEquals("1,023 B", ByteFormatter.format(1023, Locale.US, en))
    }

    @Test
    fun scalesByPowersOf1024WithOneDecimal() {
        assertEquals("1.0 KB", ByteFormatter.format(1024, Locale.US, en))
        assertEquals("1.5 KB", ByteFormatter.format(1536, Locale.US, en))
        assertEquals("1.0 MB", ByteFormatter.format(1024L * 1024, Locale.US, en))
        assertEquals("2.5 GB", ByteFormatter.format((2.5 * 1024 * 1024 * 1024).toLong(), Locale.US, en))
        assertEquals("1.0 TB", ByteFormatter.format(1024L * 1024 * 1024 * 1024, Locale.US, en))
    }

    @Test
    fun numberFollowsTheLocale() {
        assertEquals("1,5 KB", ByteFormatter.format(1536, Locale.GERMANY, en))
        assertEquals("1,5 KB", ByteFormatter.format(1536, Locale.FRANCE, en))
    }

    @Test
    fun unitComesFromTheTemplates() {
        assertEquals("1,5 КБ", ByteFormatter.format(1536, Locale("ru", "RU"), ru))
        assertEquals("512 Б", ByteFormatter.format(512, Locale("ru", "RU"), ru))
    }

    @Test
    fun hugeValuesStayInTerabytes() {
        assertEquals(true, ByteFormatter.format(Long.MAX_VALUE, Locale.US, en).endsWith(" TB"))
    }

    @Test
    fun fallsBackToEnglishBeforeInit() {
        Locale.setDefault(Locale.US)
        assertEquals("1.0 KB", ByteFormatter.format(1024))
    }

    private fun shippedTemplates(folder: String): List<String> {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val xml = File(res, "$folder/strings.xml").readText()
        return listOf("b", "kb", "mb", "gb", "tb").map { u ->
            Regex("<string name=\"size_unit_$u\">([^<]*)</string>").find(xml)!!.groupValues[1]
        }
    }

    @Test
    fun traditionalChineseLocalesFormatWithShippedUnits() {
        val cases = mapOf(
            "values-zh-rTW" to Locale.TAIWAN,
            "values-zh-rHK" to Locale("zh", "HK"),
            "values-b+zh+Hant" to Locale.forLanguageTag("zh-Hant-MO")
        )
        for ((folder, locale) in cases) {
            val t = shippedTemplates(folder)
            assertEquals("$folder B", "512 B", ByteFormatter.format(512, locale, t))
            assertEquals("$folder KB", "1.5 KB", ByteFormatter.format(1536, locale, t))
            assertEquals("$folder GB", "2.5 GB", ByteFormatter.format((2.5 * 1024 * 1024 * 1024).toLong(), locale, t))
            assertEquals("$folder grouping", "1,023 B", ByteFormatter.format(1023, locale, t))
        }
    }
}
