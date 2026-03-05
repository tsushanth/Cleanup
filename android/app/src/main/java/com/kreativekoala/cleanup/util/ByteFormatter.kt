package com.kreativekoala.cleanup.util

object ByteFormatter {
    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        if (gb < 1024) return "%.1f GB".format(gb)
        val tb = gb / 1024.0
        return "%.1f TB".format(tb)
    }
}
