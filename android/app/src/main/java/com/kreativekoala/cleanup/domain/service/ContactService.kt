package com.kreativekoala.cleanup.domain.service

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.kreativekoala.cleanup.data.model.ContactItem
import com.kreativekoala.cleanup.data.model.ContactScanResult
import com.kreativekoala.cleanup.data.model.DuplicateContactGroup
import com.kreativekoala.cleanup.data.model.IncompleteContactResult
import com.kreativekoala.cleanup.data.model.MatchReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS ContactService using Android ContactsContract.
 *
 * Provides duplicate contact detection (by name and phone number),
 * incomplete contact detection, contact merging, and deletion.
 */
@Singleton
class ContactService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver get() = context.contentResolver

    // MARK: - Find Duplicate Contacts

    suspend fun findDuplicates(): ContactScanResult = withContext(Dispatchers.IO) {
        val allContacts = queryAllContacts()
        val duplicateGroups = mutableListOf<DuplicateContactGroup>()

        // Find duplicates by normalized name
        val nameGroups = mutableMapOf<String, MutableList<ContactItem>>()
        for (contact in allContacts) {
            val normalizedName = contact.displayName.lowercase().trim()
            if (normalizedName.isNotEmpty() && normalizedName != "no name") {
                nameGroups.getOrPut(normalizedName) { mutableListOf() }.add(contact)
            }
        }

        for ((_, contacts) in nameGroups) {
            if (contacts.size > 1) {
                duplicateGroups.add(
                    DuplicateContactGroup(
                        contacts = contacts,
                        matchReason = MatchReason.SAME_NAME
                    )
                )
            }
        }

        // Find duplicates by phone number
        val phoneGroups = mutableMapOf<String, MutableList<ContactItem>>()
        for (contact in allContacts) {
            for (phone in contact.phoneNumbers) {
                val normalized = normalizePhoneNumber(phone)
                if (normalized.isNotEmpty()) {
                    val group = phoneGroups.getOrPut(normalized) { mutableListOf() }
                    if (group.none { it.contactId == contact.contactId }) {
                        group.add(contact)
                    }
                }
            }
        }

        for ((_, contacts) in phoneGroups) {
            if (contacts.size > 1) {
                // Check if already covered by name matching
                val ids = contacts.map { it.contactId }.toSet()
                val alreadyGrouped = duplicateGroups.any { group ->
                    val groupIds = group.contacts.map { it.contactId }.toSet()
                    ids.intersect(groupIds).isNotEmpty()
                }

                if (!alreadyGrouped) {
                    duplicateGroups.add(
                        DuplicateContactGroup(
                            contacts = contacts,
                            matchReason = MatchReason.SAME_PHONE
                        )
                    )
                }
            }
        }

        val totalDuplicates = duplicateGroups.sumOf { it.contacts.size - 1 }
        ContactScanResult(count = totalDuplicates, groups = duplicateGroups)
    }

    // MARK: - Find Incomplete Contacts

    suspend fun findIncompleteContacts(): IncompleteContactResult = withContext(Dispatchers.IO) {
        val allContacts = queryAllContacts()
        val incomplete = allContacts.filter { it.isIncomplete }
        IncompleteContactResult(count = incomplete.size, contacts = incomplete)
    }

    // MARK: - Merge Contacts

    suspend fun mergeContacts(
        contacts: List<ContactItem>,
        primaryContact: ContactItem
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            // Collect phone numbers and emails from other contacts to add to primary
            val existingPhones = primaryContact.phoneNumbers.toMutableSet()
            val existingEmails = primaryContact.emailAddresses.toMutableSet()

            for (contact in contacts) {
                if (contact.contactId == primaryContact.contactId) continue

                // Add unique phone numbers to primary
                for (phone in contact.phoneNumbers) {
                    val normalized = normalizePhoneNumber(phone)
                    if (normalized !in existingPhones.map { normalizePhoneNumber(it) }) {
                        existingPhones.add(phone)
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryContact.rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }
                }

                // Add unique emails to primary
                for (email in contact.emailAddresses) {
                    if (email.lowercase() !in existingEmails.map { it.lowercase() }) {
                        existingEmails.add(email)
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryContact.rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                                .build()
                        )
                    }
                }

                // Delete the duplicate contact's raw contact
                ops.add(
                    ContentProviderOperation.newDelete(
                        ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendPath(contact.rawContactId.toString())
                            .build()
                    ).build()
                )
            }

            if (ops.isNotEmpty()) {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // MARK: - Delete Contacts

    suspend fun deleteContacts(contacts: List<ContactItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            for (contact in contacts) {
                ops.add(
                    ContentProviderOperation.newDelete(
                        ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendPath(contact.rawContactId.toString())
                            .build()
                    ).build()
                )
            }
            if (ops.isNotEmpty()) {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // MARK: - Private Helpers

    private fun queryAllContacts(): List<ContactItem> {
        val contacts = mutableMapOf<Long, ContactItem>()

        // Query contact names
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            ),
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                contacts[id] = ContactItem(
                    contactId = id,
                    rawContactId = 0,
                    displayName = name,
                    phoneNumbers = emptyList(),
                    emailAddresses = emptyList()
                )
            }
        }

        // Get raw contact IDs
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID
            ),
            null, null, null
        )?.use { cursor ->
            val rawIdCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID)

            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(rawIdCol)
                val contactId = cursor.getLong(contactIdCol)
                contacts[contactId]?.let { contact ->
                    if (contact.rawContactId == 0L) {
                        contacts[contactId] = contact.copy(rawContactId = rawId)
                    }
                }
            }
        }

        // Query phone numbers
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { cursor ->
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdCol)
                val number = cursor.getString(numberCol) ?: continue
                contacts[contactId]?.let { contact ->
                    contacts[contactId] = contact.copy(
                        phoneNumbers = contact.phoneNumbers + number
                    )
                }
            }
        }

        // Query email addresses
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null, null, null
        )?.use { cursor ->
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdCol)
                val email = cursor.getString(emailCol) ?: continue
                contacts[contactId]?.let { contact ->
                    contacts[contactId] = contact.copy(
                        emailAddresses = contact.emailAddresses + email
                    )
                }
            }
        }

        return contacts.values.toList()
    }

    /**
     * Normalize phone number by extracting last 10 digits for comparison.
     * Matches iOS behavior.
     */
    private fun normalizePhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}
