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
import digital.heirlooms.ui.auth.BiometricGateScreen

/**
 * Entry-point for the main UI. Detects which first-run path to show:
 *
 * 1. Has session_token AND vault ready → normal app
 * 2. Has session_token but vault not ready → VaultSetupScreen (passphrase)
 * 3. Has api_key (legacy) but no session_token → MigrationScreen (setup-existing)
 * 4. Neither → InviteRedemptionScreen (first-run invite)
 * 5. (Handled in AppNavigation) 401 during use → LoginScreen
 */
@Composable
fun MainApp() {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }
    val deviceKeyManager = remember { DeviceKeyManager(context) }

    var sessionToken by rememberSaveable { mutableStateOf(store.getSessionToken()) }
    var welcomed by rememberSaveable { mutableStateOf(store.getWelcomed()) }
    var vaultReady by rememberSaveable { mutableStateOf(deviceKeyManager.isVaultSetUp()) }
    // SEC-015: biometric gate — false until user authenticates when require_biometric = true.
    var biometricPassed by rememberSaveable { mutableStateOf(!store.getRequireBiometric()) }

    val hasLegacyApiKey = store.getApiKey().isNotEmpty()

    when {
        // ── First-run: invite redemption ────────────────────────────────────
        sessionToken.isEmpty() && !hasLegacyApiKey -> InviteRedemptionScreen(
            onRegistered = { token ->
                store.setWelcomed(true)
                welcomed = true
                sessionToken = token
                vaultReady = true
            }
        )

        // ── Migration: founding user sets passphrase ─────────────────────────
        sessionToken.isEmpty() && hasLegacyApiKey -> MigrationScreen(
            onMigrated = { token ->
                sessionToken = token
                vaultReady = deviceKeyManager.isVaultSetUp()
            }
        )

        // ── Welcome screen (first time after registration) ──────────────────
        !welcomed -> WelcomeScreen(
            onGetStarted = {
                store.setWelcomed(true)
                welcomed = true
            }
        )

        // ── Vault setup (session valid, but vault not yet configured) ────────
        !vaultReady -> VaultSetupScreen(
            apiKey = sessionToken,
            onComplete = { vaultReady = true },
        )

        // ── Normal app ───────────────────────────────────────────────────────
        else -> {
            if (!VaultSession.isUnlocked) {
                deviceKeyManager.loadMasterKey()?.let { VaultSession.unlock(it) }
            }
            // SEC-015: show biometric gate before vault content when setting is enabled.
            if (!biometricPassed) {
                BiometricGateScreen(onAuthenticated = { biometricPassed = true })
            } else {
                MainNavigation(
                    apiKey = sessionToken,
                    onApiKeyReset = {
                        store.clearSessionToken()
                        sessionToken = ""
                        VaultSession.lock()
                        // Reset gate so next login re-prompts biometric.
                        biometricPassed = !store.getRequireBiometric()
                    },
                    store = store,
                )
            }
        }
    }
}
