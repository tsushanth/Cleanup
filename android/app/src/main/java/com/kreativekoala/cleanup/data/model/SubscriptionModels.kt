package com.kreativekoala.cleanup.data.model

object ProductIds {
    const val PRO_MONTHLY = "cleanup_pro_monthly_999"
    const val PRO_YEARLY = "cleanup_pro_yearly_2999"
    const val ARCHIVE_5GB = "cleanup_archive_5gb__199"
    const val ARCHIVE_25GB = "cleanup_archive_25gb_499"
    const val ARCHIVE_100GB = "cleanup_archive_100gb_1499"

    val proProductIds = listOf(PRO_MONTHLY, PRO_YEARLY)
    val archiveProductIds = listOf(ARCHIVE_5GB, ARCHIVE_25GB, ARCHIVE_100GB)
    val allProductIds = proProductIds + archiveProductIds
}

enum class FreeUsageCategory(val label: String, val prefsKey: String) {
    DUPLICATE_PHOTOS("Duplicate Photos", "free_usage_duplicate_photos"),
    SIMILAR_PHOTOS("Similar Photos", "free_usage_similar_photos"),
    SCREENSHOTS("Screenshots", "free_usage_screenshots"),
    VIDEOS("Videos", "free_usage_videos"),
    CONTACTS("Contacts", "free_usage_contacts")
}
