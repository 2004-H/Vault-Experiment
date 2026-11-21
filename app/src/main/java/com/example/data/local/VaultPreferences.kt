package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore by preferencesDataStore(name = "vaultblock_prefs")

data class VaultSettings(
    val linkedTreeUri: String? = null,
    val vaultName: String = "Local Vault",
    val lastSyncEpochMs: Long = 0L,
    val darkMode: Boolean = true,
    val dynamicColor: Boolean = false,
    val starterVaultSeeded: Boolean = false
)

class VaultPreferences(private val context: Context) {

    private object Keys {
        val LINKED_TREE_URI = stringPreferencesKey("linked_tree_uri")
        val VAULT_NAME = stringPreferencesKey("vault_name")
        val LAST_SYNC_EPOCH_MS = longPreferencesKey("last_sync_epoch_ms")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val STARTER_SEEDED = booleanPreferencesKey("starter_vault_seeded")
    }

    val settingsFlow: Flow<VaultSettings> = context.vaultDataStore.data.map { prefs ->
        VaultSettings(
            linkedTreeUri = prefs[Keys.LINKED_TREE_URI],
            vaultName = prefs[Keys.VAULT_NAME] ?: "Local Vault",
            lastSyncEpochMs = prefs[Keys.LAST_SYNC_EPOCH_MS] ?: 0L,
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            starterVaultSeeded = prefs[Keys.STARTER_SEEDED] ?: false
        )
    }

    suspend fun setLinkedVault(treeUri: String?, vaultName: String) {
        context.vaultDataStore.edit { prefs ->
            if (treeUri != null) {
                prefs[Keys.LINKED_TREE_URI] = treeUri
            } else {
                prefs.remove(Keys.LINKED_TREE_URI)
            }
            prefs[Keys.VAULT_NAME] = vaultName
        }
    }

    suspend fun setLastSync(epochMs: Long) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_EPOCH_MS] = epochMs
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun markStarterSeeded() {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.STARTER_SEEDED] = true
        }
    }
}
