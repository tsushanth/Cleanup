package com.kreativekoala.cleanup.ui.notifications

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.domain.service.NotificationManagementService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationManagerViewModel @Inject constructor(
    private val notificationService: NotificationManagementService
) : ViewModel() {

    data class UiState(
        val apps: List<NotificationManagementService.AppNotificationInfo> = emptyList(),
        val isScanning: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val apps = notificationService.getAppNotificationInfos()
            _uiState.update { it.copy(apps = apps, isScanning = false) }
        }
    }

    fun getAppNotificationSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    }
}
