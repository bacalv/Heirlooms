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
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    prefillUsername: String,
    prefillAuthSalt: String,
    onLoginSuccess: (sessionToken: String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(prefillUsername) }
    var passphrase by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Sign in", style = HeirloomsSerifItalic.copy(fontSize = 28.sp, color = Forest))
        Spacer(Modifier.height(8.dp))
        Text("Your session expired. Enter your passphrase to continue.",
            style = MaterialTheme.typography.bodyMedium, color = Forest)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
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

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = Earth))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    working = true
                    error = null
                    try {
                        val u = username.trim()
                        val saltB64url = prefillAuthSalt.takeIf { it.isNotEmpty() }
                            ?: run {
                                val api = HeirloomsApi(apiKey = "")
                                api.authChallenge(u).authSaltB64url
                            }
                        val salt = VaultCrypto.fromBase64Url(saltB64url)
                        val (authKey, _) = VaultCrypto.deriveAuthAndMasterKeys(passphrase, salt)
                        val authKeyB64url = VaultCrypto.toBase64Url(authKey)
                        val api = HeirloomsApi(apiKey = "")
                        val resp = api.authLogin(u, authKeyB64url)
                        store.setSessionToken(resp.sessionToken)
                        store.setUsername(u)
                        store.setAuthSalt(saltB64url)
                        try {
                            val authedApi = HeirloomsApi(apiKey = resp.sessionToken)
                            val me = authedApi.authMe()
                            store.setDisplayName(me.displayName)
                            // SEC-015: sync require_biometric from server on login.
                            try {
                                val account = authedApi.getAccount()
                                store.setRequireBiometric(account.requireBiometric)
                            } catch (_: Exception) {}
                        } catch (_: Exception) {}
                        onLoginSuccess(resp.sessionToken)
                    } catch (e: Exception) {
                        error = if (e.message?.contains("UNAUTHORIZED") == true)
                            "Invalid username or passphrase." else "Could not reach the server."
                    } finally {
                        working = false
                    }
                }
            },
            enabled = !working && username.isNotBlank() && passphrase.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
        ) {
            Text(if (working) "Signing in…" else "Sign in",
                style = HeirloomsSerifItalic.copy(fontSize = 16.sp))
        }
    }
}
