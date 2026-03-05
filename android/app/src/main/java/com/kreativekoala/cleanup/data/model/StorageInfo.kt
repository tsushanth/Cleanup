package com.kreativekoala.cleanup.data.model

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val photosSize: Long = 0,
    val videosSize: Long = 0,
    val otherSize: Long = 0
) {
    val usedPercentage: Double get() = if (totalBytes > 0) usedBytes.toDouble() / totalBytes else 0.0
    val totalFormatted: String get() = formatBytes(totalBytes)
    val usedFormatted: String get() = formatBytes(usedBytes)
    val freeFormatted: String get() = formatBytes(freeBytes)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
