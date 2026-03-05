package com.kreativekoala.cleanup.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val mediumFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun formatMedium(timestamp: Long): String = mediumFormat.format(Date(timestamp))
    fun formatShort(timestamp: Long): String = shortFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
    fun formatMediumWithTime(timestamp: Long): String {
        val date = Date(timestamp)
        return "${mediumFormat.format(date)} ${timeFormat.format(date)}"
    }
}
