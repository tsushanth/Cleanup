package com.kreativekoala.cleanup.data.model

import androidx.annotation.StringRes
import com.kreativekoala.cleanup.R
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

enum class MatchReason(@StringRes val labelRes: Int) {
    SAME_NAME(R.string.contacts_match_name),
    SAME_PHONE(R.string.contacts_match_phone),
    SAME_EMAIL(R.string.contacts_match_email)
}

data class ContactScanResult(
    val count: Int,
    val groups: List<DuplicateContactGroup>
)

data class IncompleteContactResult(
    val count: Int,
    val contacts: List<ContactItem>
)
