package com.kreativekoala.cleanup.util

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Locale-aware plural lookup for Compose. The [quantity] picks the CLDR category
 * (one, few, many, ...) for the current language; [formatArgs] fill the format
 * placeholders and normally start with the same number.
 */
@Composable
fun pluralStringRes(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    // Read the configuration so the text is recomputed when the language changes.
    LocalConfiguration.current
    return LocalContext.current.resources.getQuantityString(id, quantity, *formatArgs)
}
