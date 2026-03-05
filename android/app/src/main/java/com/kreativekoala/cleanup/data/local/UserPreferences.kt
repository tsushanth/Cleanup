package com.kreativekoala.cleanup.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cleanup_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Onboarding
    private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

    val hasCompletedOnboarding: Flow<Boolean> = dataStore.data.map {
        it[HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun setOnboardingComplete() {
        dataStore.edit { it[HAS_COMPLETED_ONBOARDING] = true }
    }

    // Free usage tracking
    private fun freeUsageKey(category: String) = intPreferencesKey("free_usage_$category")

    fun getFreeUsageCount(category: String): Flow<Int> = dataStore.data.map {
        it[freeUsageKey(category)] ?: 0
    }

    suspend fun incrementFreeUsage(category: String) {
        dataStore.edit { prefs ->
            val current = prefs[freeUsageKey(category)] ?: 0
            prefs[freeUsageKey(category)] = current + 1
        }
    }

    // Vault settings
    private val VAULT_PIN = stringPreferencesKey("vault_pin")
    private val VAULT_FAKE_PIN = stringPreferencesKey("vault_fake_pin")
    private val VAULT_BIOMETRIC_ENABLED = booleanPreferencesKey("vault_biometric_enabled")
    private val VAULT_AUTO_LOCK_SECONDS = intPreferencesKey("vault_auto_lock_seconds")

    val vaultPin: Flow<String?> = dataStore.data.map { it[VAULT_PIN] }
    val vaultFakePin: Flow<String?> = dataStore.data.map { it[VAULT_FAKE_PIN] }
    val vaultBiometricEnabled: Flow<Boolean> = dataStore.data.map { it[VAULT_BIOMETRIC_ENABLED] ?: false }
    val vaultAutoLockSeconds: Flow<Int> = dataStore.data.map { it[VAULT_AUTO_LOCK_SECONDS] ?: 60 }

    suspend fun setVaultPin(pin: String) { dataStore.edit { it[VAULT_PIN] = pin } }
    suspend fun setVaultFakePin(pin: String) { dataStore.edit { it[VAULT_FAKE_PIN] = pin } }
    suspend fun setVaultBiometricEnabled(enabled: Boolean) { dataStore.edit { it[VAULT_BIOMETRIC_ENABLED] = enabled } }

    // Archive auth
    private val ARCHIVE_IDENTITY_ID = stringPreferencesKey("archive_identity_id")
    private val ARCHIVE_AUTH_TOKEN = stringPreferencesKey("archive_auth_token")

    val archiveIdentityId: Flow<String?> = dataStore.data.map { it[ARCHIVE_IDENTITY_ID] }

    suspend fun setArchiveIdentityId(id: String) { dataStore.edit { it[ARCHIVE_IDENTITY_ID] = id } }
    suspend fun setArchiveAuthToken(token: String) { dataStore.edit { it[ARCHIVE_AUTH_TOKEN] = token } }
}
