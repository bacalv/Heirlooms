package digital.heirlooms.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.app.EndpointStore
import digital.heirlooms.crypto.DeviceKeyManager
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.launch

@Composable
fun MigrationScreen(onMigrated: (sessionToken: String) -> Unit) {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }
    val deviceKeyManager = remember { DeviceKeyManager(context) }
    val scope = rememberCoroutineScope()

    // Username is read from the store if already known. Shown as read-only so the
    // founding user can't accidentally change it — the account username is fixed in
    // the database and setup-existing will reject a mismatch.
    val storedUsername = remember { store.getUsername() }
    var username by remember { mutableStateOf(storedUsername) }
    val usernameIsLocked = storedUsername.isNotEmpty()
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val mismatch = confirm.isNotEmpty() && passphrase != confirm

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Set up your passphrase", style = HeirloomsSerifItalic.copy(fontSize = 26.sp, color = Forest))
        Spacer(Modifier.height(8.dp))
        Text("Heirlooms now uses a personal passphrase instead of an API key. Set yours to continue.",
            style = MaterialTheme.typography.bodyMedium, color = Forest)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { if (!usernameIsLocked) username = it },
            label = { Text("Username") },
            readOnly = usernameIsLocked,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (usernameIsLocked) Forest15 else Forest,
                unfocusedBorderColor = Forest15,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            isError = mismatch,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        if (mismatch) {
            Text("Passphrases don't match.", style = MaterialTheme.typography.bodySmall, color = Earth)
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = Earth))
        }

        Spacer(Modifier.height(24.dp))
        val canSubmit = username.isNotBlank() && passphrase.isNotEmpty() && confirm.isNotEmpty() && !mismatch
        Button(
            onClick = {
                scope.launch {
                    working = true
                    error = null
                    try {
                        val u = username.trim()
                        val authSalt = VaultCrypto.generateSalt(16)
                        val (authKey, _) = VaultCrypto.deriveAuthAndMasterKeys(passphrase, authSalt)
                        val authVerifier = VaultCrypto.computeAuthVerifier(authKey)
                        val authSaltB64url = VaultCrypto.toBase64Url(authSalt)
                        val authVerifierB64url = VaultCrypto.toBase64Url(authVerifier)
                        val api = HeirloomsApi(apiKey = store.getApiKey())
                        val resp = api.setupExisting(u, deviceKeyManager.deviceId, authSaltB64url, authVerifierB64url)
                        store.setSessionToken(resp.sessionToken)
                        store.setUsername(u)
                        store.setAuthSalt(authSaltB64url)
                        store.setApiKey("")  // clear old api_key after migration
                        onMigrated(resp.sessionToken)
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong."
                    } finally {
                        working = false
                    }
                }
            },
            enabled = !working && canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
        ) {
            Text(if (working) "Setting up…" else "Continue",
                style = HeirloomsSerifItalic.copy(fontSize = 16.sp))
        }
    }
}
