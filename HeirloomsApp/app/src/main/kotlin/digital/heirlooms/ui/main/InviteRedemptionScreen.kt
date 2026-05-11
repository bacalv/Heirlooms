package digital.heirlooms.ui.main

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InviteRedemptionScreen(onRegistered: (sessionToken: String) -> Unit) {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }
    val deviceKeyManager = remember { DeviceKeyManager(context) }
    val scope = rememberCoroutineScope()

    var inviteToken by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val mismatch = confirm.isNotEmpty() && passphrase != confirm
    val canSubmit = inviteToken.isNotBlank() && username.isNotBlank() &&
        displayName.isNotBlank() && passphrase.isNotEmpty() && confirm.isNotEmpty() && !mismatch

    val (focusUsername, focusDisplayName, focusPassphrase, focusConfirm) =
        remember { List(4) { FocusRequester() } }

    fun submit() {
        if (!canSubmit || working) return
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

                val masterKey = VaultCrypto.generateMasterKey()
                val spkiBytes = withContext(Dispatchers.IO) {
                    deviceKeyManager.setupVault(masterKey)
                    deviceKeyManager.getDevicePublicKeySpki() ?: error("Keypair generation failed")
                }
                val wrappedMasterKey = withContext(Dispatchers.IO) {
                    deviceKeyManager.wrapMasterKeyForServer(masterKey)
                }
                val pubkeyB64 = Base64.encodeToString(spkiBytes, Base64.NO_WRAP)
                val wrappedB64 = Base64.encodeToString(wrappedMasterKey, Base64.NO_WRAP)

                val api = HeirloomsApi(apiKey = "")
                val r = api.authRegister(
                    inviteToken = inviteToken.trim(),
                    username = u,
                    displayName = displayName.trim(),
                    authSaltB64url = authSaltB64url,
                    authVerifierB64url = authVerifierB64url,
                    wrappedMasterKeyB64 = wrappedB64,
                    pubkeyB64 = pubkeyB64,
                    deviceId = deviceKeyManager.deviceId,
                    deviceLabel = deviceKeyManager.deviceLabel,
                )
                store.setSessionToken(r.sessionToken)
                store.setUsername(u)
                store.setAuthSalt(authSaltB64url)
                onRegistered(r.sessionToken)
            } catch (e: Exception) {
                error = when {
                    e.message?.contains("409") == true -> "Username already taken."
                    e.message?.contains("410") == true -> "Invite is invalid or expired."
                    else -> e.message ?: "Something went wrong."
                }
            } finally {
                working = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Create your account", style = HeirloomsSerifItalic.copy(fontSize = 26.sp, color = Forest))
        Spacer(Modifier.height(8.dp))
        Text("Enter your invite code and set a passphrase to get started.",
            style = MaterialTheme.typography.bodyMedium, color = Forest)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = inviteToken, onValueChange = { inviteToken = it },
            label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusUsername.requestFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusUsername),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusDisplayName.requestFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName, onValueChange = { displayName = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusDisplayName),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusPassphrase.requestFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = passphrase, onValueChange = { passphrase = it },
            label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().focusRequester(focusPassphrase),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusConfirm.requestFocus() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("Confirm passphrase") }, visualTransformation = PasswordVisualTransformation(),
            isError = mismatch,
            modifier = Modifier.fillMaxWidth().focusRequester(focusConfirm),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
        )
        if (mismatch) Text("Passphrases don't match.", style = MaterialTheme.typography.bodySmall, color = Earth)

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = Earth))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit() },
            enabled = !working && canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
        ) {
            Text(if (working) "Creating account…" else "Create account",
                style = HeirloomsSerifItalic.copy(fontSize = 16.sp))
        }
    }
}
