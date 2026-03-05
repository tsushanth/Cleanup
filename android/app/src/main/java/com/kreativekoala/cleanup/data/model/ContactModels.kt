package com.kreativekoala.cleanup.data.model

import java.util.UUID

data class ContactItem(
    val contactId: Long,
    val rawContactId: Long = 0,
    val displayName: String,
    val phoneNumbers: List<String> = emptyList(),
    val emailAddresses: List<String> = emptyList(),
    val photoUri: String? = null
) {
    val isIncomplete: Boolean
        get() = displayName.isBlank() || (phoneNumbers.isEmpty() && emailAddresses.isEmpty())

    val initials: String
        get() = displayName.take(1).uppercase().ifEmpty { "?" }
}

data class DuplicateContactGroup(
    val id: UUID = UUID.randomUUID(),
    val contacts: List<ContactItem>,
    val matchReason: MatchReason
) {
    val duplicateCount: Int get() = contacts.size - 1
    val primaryContact: ContactItem get() = contacts.first()
}

enum class MatchReason(val label: String) {
    SAME_NAME("Same Name"),
    SAME_PHONE("Same Phone"),
    SAME_EMAIL("Same Email")
}

data class ContactScanResult(
    val count: Int,
    val groups: List<DuplicateContactGroup>
)

data class IncompleteContactResult(
    val count: Int,
    val contacts: List<ContactItem>
)
