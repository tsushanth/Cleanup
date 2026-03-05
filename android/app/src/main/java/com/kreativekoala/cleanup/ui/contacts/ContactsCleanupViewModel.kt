package com.kreativekoala.cleanup.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.billing.PaywallCoordinator
import com.kreativekoala.cleanup.billing.PaywallContext
import com.kreativekoala.cleanup.data.model.ContactItem
import com.kreativekoala.cleanup.data.model.DuplicateContactGroup
import com.kreativekoala.cleanup.domain.service.ContactService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsCleanupViewModel @Inject constructor(
    private val contactService: ContactService,
    private val paywallCoordinator: PaywallCoordinator
) : ViewModel() {

    data class UiState(
        val duplicateGroups: List<DuplicateContactGroup> = emptyList(),
        val incompleteContacts: List<ContactItem> = emptyList(),
        val selectedIncomplete: Set<Long> = emptySet(),
        val isScanning: Boolean = false,
        val showMergeSheet: Boolean = false,
        val mergeGroup: DuplicateContactGroup? = null,
        val showPaywall: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val selectedIncompleteCount: Int get() = _uiState.value.selectedIncomplete.size

    // MARK: - Scanning

    fun scan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }

            val duplicatesDeferred = async { contactService.findDuplicates() }
            val incompleteDeferred = async { contactService.findIncompleteContacts() }

            val duplicateResult = duplicatesDeferred.await()
            val incompleteResult = incompleteDeferred.await()

            _uiState.update {
                it.copy(
                    duplicateGroups = duplicateResult.groups,
                    incompleteContacts = incompleteResult.contacts,
                    isScanning = false
                )
            }
        }
    }

    // MARK: - Duplicate Actions

    fun showMergeSheet(group: DuplicateContactGroup) {
        _uiState.update { it.copy(showMergeSheet = true, mergeGroup = group) }
    }

    fun dismissMergeSheet() {
        _uiState.update { it.copy(showMergeSheet = false, mergeGroup = null) }
    }

    fun mergeContacts(group: DuplicateContactGroup, primaryIndex: Int) {
        if (primaryIndex >= group.contacts.size) return

        val access = paywallCoordinator.checkAccess(
            context = PaywallContext.AttemptMergeContacts(group.contacts.size)
        )
        if (access == PaywallCoordinator.AccessResult.PAYWALL) {
            _uiState.update { it.copy(showPaywall = true, showMergeSheet = false) }
            return
        }

        viewModelScope.launch {
            val primary = group.contacts[primaryIndex]
            contactService.mergeContacts(group.contacts, primary)
            paywallCoordinator.recordUsage(1)
            _uiState.update { it.copy(showMergeSheet = false, mergeGroup = null) }
            scan() // Refresh
        }
    }

    fun deleteDuplicatesInGroup(group: DuplicateContactGroup) {
        val access = paywallCoordinator.checkAccess(
            context = PaywallContext.AttemptMergeContacts(group.contacts.size)
        )
        if (access == PaywallCoordinator.AccessResult.PAYWALL) {
            _uiState.update { it.copy(showPaywall = true) }
            return
        }

        val toDelete = group.contacts.drop(1) // Keep first, delete rest

        viewModelScope.launch {
            contactService.deleteContacts(toDelete)
            paywallCoordinator.recordUsage(toDelete.size)
            scan() // Refresh
        }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    // MARK: - Incomplete Contacts

    fun isIncompleteSelected(contact: ContactItem): Boolean =
        _uiState.value.selectedIncomplete.contains(contact.contactId)

    fun toggleIncompleteSelection(contact: ContactItem) {
        _uiState.update { state ->
            val newSet = state.selectedIncomplete.toMutableSet()
            if (contact.contactId in newSet) newSet.remove(contact.contactId)
            else newSet.add(contact.contactId)
            state.copy(selectedIncomplete = newSet)
        }
    }

    fun selectAllIncomplete(select: Boolean) {
        _uiState.update { state ->
            if (select) {
                state.copy(selectedIncomplete = state.incompleteContacts.map { it.contactId }.toSet())
            } else {
                state.copy(selectedIncomplete = emptySet())
            }
        }
    }

    fun deleteSelectedIncomplete() {
        val state = _uiState.value
        val toDelete = state.incompleteContacts.filter { it.contactId in state.selectedIncomplete }

        viewModelScope.launch {
            contactService.deleteContacts(toDelete)
            _uiState.update { it.copy(selectedIncomplete = emptySet()) }
            scan() // Refresh
        }
    }
}
