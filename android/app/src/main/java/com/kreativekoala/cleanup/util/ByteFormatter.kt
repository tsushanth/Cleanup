package com.kreativekoala.cleanup.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.kreativekoala.cleanup.R
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Formats byte counts for display. The number uses the user's locale (decimal separator, digits)
 * and the unit comes from string resources (size_unit_*), so it follows the app language.
 * Call [init] once from Application.onCreate so units can be resolved without a Context at the
 * call site; until then (or in JVM tests) English units are used.
 */
object ByteFormatter {
    private val ENGLISH = listOf("%1\$s B", "%1\$s KB", "%1\$s MB", "%1\$s GB", "%1\$s TB")

    @StringRes
    private val UNIT_RES = listOf(
        R.string.size_unit_b,
        R.string.size_unit_kb,
        R.string.size_unit_mb,
        R.string.size_unit_gb,
        R.string.size_unit_tb
    )

    @Volatile
    private var appContext: Context? = null
    private val cache = ConcurrentHashMap<Locale, List<String>>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun format(bytes: Long): String {
        val locale = currentLocale()
        return format(bytes, locale, templatesFor(locale))
    }

    /**
     * The language the user sees: the per-app language when one is set (Locale.getDefault() can still
     * be the system locale then), otherwise the default locale.
     */
    private fun currentLocale(): Locale {
        if (appContext == null) return Locale.getDefault()
        return try {
            val perApp = AppCompatDelegate.getApplicationLocales()
            if (!perApp.isEmpty) perApp[0] ?: Locale.getDefault() else Locale.getDefault()
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    /** Pure core, unit-tested: [templates] are five "%1$s UNIT" strings from B to TB. */
    internal fun format(bytes: Long, locale: Locale, templates: List<String>): String {
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < templates.lastIndex) {
            value /= 1024
            unit++
        }
        val digits = if (unit == 0) 0 else 1
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)
        return String.format(locale, templates[unit], number)
    }

    private fun templatesFor(locale: Locale): List<String> {
        val ctx = appContext ?: return ENGLISH
        return cache.getOrPut(locale) {
            try {
                val config = Configuration(ctx.resources.configuration).apply { setLocale(locale) }
                val res = ctx.createConfigurationContext(config).resources
                UNIT_RES.map { res.getString(it) }
            } catch (_: Exception) {
                ENGLISH
            }
        }
    }
}
