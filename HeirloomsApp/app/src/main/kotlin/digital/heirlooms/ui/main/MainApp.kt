package digital.heirlooms.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import digital.heirlooms.app.EndpointStore
import digital.heirlooms.crypto.DeviceKeyManager
import digital.heirlooms.crypto.VaultSession

@Composable
fun MainApp() {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }
    val deviceKeyManager = remember { DeviceKeyManager(context) }

    // rememberSaveable so the key survives Activity recreation (rotation, OS process kill + restore).
    // SharedPreferences is the authoritative store; this is a Bundle-backed safety net.
    var apiKey by rememberSaveable { mutableStateOf(store.getApiKey()) }
    var welcomed by rememberSaveable { mutableStateOf(store.getWelcomed()) }
    var vaultReady by rememberSaveable { mutableStateOf(deviceKeyManager.isVaultSetUp()) }

    when {
        apiKey.isEmpty() -> ApiKeyScreen(
            onKeyEntered = { key ->
                store.setApiKey(key)
                apiKey = key
            }
        )
        !welcomed -> WelcomeScreen(
            onGetStarted = {
                store.setWelcomed(true)
                welcomed = true
            }
        )
        !vaultReady -> VaultSetupScreen(
            apiKey = apiKey,
            onComplete = { vaultReady = true },
        )
        else -> {
            // Auto-unlock vault on process restart (Keystore AES decrypt is fast, safe on main thread).
            if (!VaultSession.isUnlocked) {
                deviceKeyManager.loadMasterKey()?.let { VaultSession.unlock(it) }
            }
            MainNavigation(
                apiKey = apiKey,
                onApiKeyReset = {
                    store.setApiKey("")
                    apiKey = ""
                    // Welcome flag intentionally NOT reset — welcome shows once per install.
                },
            )
        }
    }
}
