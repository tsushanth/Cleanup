package com.kreativekoala.cleanup.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.data.model.VaultFileType
import com.kreativekoala.cleanup.data.model.VaultItem
import com.kreativekoala.cleanup.data.model.VaultSettings
import com.kreativekoala.cleanup.domain.service.VaultService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultService: VaultService
) : ViewModel() {

    data class UiState(
        val isUnlocked: Boolean = false,
        val showFakeContent: Boolean = false,
        val showSetupPin: Boolean = false,
        val showAddSheet: Boolean = false,
        val selectedItem: VaultItem? = null,
        val showDeleteConfirmation: Boolean = false,
        val showRestoreConfirmation: Boolean = false,
        val enteredPin: String = "",
        val pinError: String? = null,
        val isLoading: Boolean = false,
        val settings: VaultSettings = VaultSettings()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val items: StateFlow<List<VaultItem>> = vaultService.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSettings()
    }

    // MARK: - Settings

    private fun loadSettings() {
        val settings = vaultService.getSettings()
        _uiState.update { it.copy(settings = settings) }

        // Show setup if no PIN configured
        if (!vaultService.hasPin()) {
            _uiState.update { it.copy(showSetupPin = true) }
        }
    }

    fun hasPin(): Boolean = vaultService.hasPin()

    fun canUseBiometrics(): Boolean =
        vaultService.canUseBiometrics() && _uiState.value.settings.isBiometricEnabled

    fun canUseBiometricsHardware(): Boolean = vaultService.canUseBiometrics()

    // MARK: - PIN Entry

    fun onPinDigitEntered(digit: String) {
        val current = _uiState.value.enteredPin
        if (current.length < 4) {
            val newPin = current + digit
            _uiState.update { it.copy(enteredPin = newPin, pinError = null) }
            if (newPin.length == 4) {
                verifyPin(newPin)
            }
        }
    }

    fun onPinBackspace() {
        val current = _uiState.value.enteredPin
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(enteredPin = current.dropLast(1), pinError = null) }
        }
    }

    private fun verifyPin(pin: String) {
        when {
            vaultService.verifyPin(pin) -> {
                _uiState.update {
                    it.copy(isUnlocked = true, showFakeContent = false, enteredPin = "")
                }
            }
            vaultService.isFakePin(pin) -> {
                // Fake PIN - show decoy empty vault
                _uiState.update {
                    it.copy(isUnlocked = true, showFakeContent = true, enteredPin = "")
                }
            }
            else -> {
                _uiState.update {
                    it.copy(enteredPin = "", pinError = "Incorrect PIN. Please try again.")
                }
            }
        }
    }

    fun onBiometricSuccess() {
        _uiState.update { it.copy(isUnlocked = true, showFakeContent = false) }
    }

    // MARK: - PIN Setup

    fun setPin(pin: String) {
        vaultService.setPin(pin)
        _uiState.update {
            it.copy(
                showSetupPin = false,
                settings = vaultService.getSettings()
            )
        }
    }

    fun setFakePin(pin: String?) {
        vaultService.setFakePin(pin)
        _uiState.update { it.copy(settings = vaultService.getSettings()) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        vaultService.setBiometricEnabled(enabled)
        _uiState.update { it.copy(settings = vaultService.getSettings()) }
    }

    // MARK: - Lock

    fun lock() {
        vaultService.cleanupTempFiles()
        _uiState.update {
            it.copy(isUnlocked = false, showFakeContent = false, enteredPin = "")
        }
    }

    // MARK: - Item Management

    fun showAddSheet() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun dismissAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun selectItem(item: VaultItem) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun clearSelectedItem() {
        _uiState.update { it.copy(selectedItem = null) }
    }

    fun addToVault(
        uri: Uri,
        fileName: String,
        fileType: VaultFileType,
        fileSize: Long,
        deleteOriginal: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                vaultService.addToVault(uri, fileName, fileType, fileSize, deleteOriginal)
            } catch (_: Exception) {
                // Item will be added via Flow from Room
            }
            _uiState.update { it.copy(isLoading = false, showAddSheet = false) }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun showRestoreConfirmation() {
        _uiState.update { it.copy(showRestoreConfirmation = true) }
    }

    fun dismissConfirmations() {
        _uiState.update {
            it.copy(showDeleteConfirmation = false, showRestoreConfirmation = false)
        }
    }

    fun deleteSelectedItem() {
        val item = _uiState.value.selectedItem ?: return
        viewModelScope.launch {
            try {
                vaultService.removeFromVault(item, restoreToPhotos = false)
            } catch (_: Exception) {
                // Handled by Flow
            }
            _uiState.update {
                it.copy(selectedItem = null, showDeleteConfirmation = false)
            }
        }
    }

    fun restoreSelectedItem() {
        val item = _uiState.value.selectedItem ?: return
        viewModelScope.launch {
            try {
                vaultService.removeFromVault(item, restoreToPhotos = true)
            } catch (_: Exception) {
                // Handled by Flow
            }
            _uiState.update {
                it.copy(selectedItem = null, showRestoreConfirmation = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vaultService.cleanupTempFiles()
    }
}
