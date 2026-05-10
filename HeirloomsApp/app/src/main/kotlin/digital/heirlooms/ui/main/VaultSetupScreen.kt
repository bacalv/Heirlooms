package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.ui.brand.OliveBranchIcon
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment

@Composable
fun VaultSetupScreen(
    apiKey: String,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val vm: VaultSetupViewModel = viewModel(
        factory = VaultSetupViewModel.Factory(
            context.applicationContext as android.app.Application,
            apiKey,
        )
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startSetup() }
    LaunchedEffect(state) {
        if (state is VaultSetupViewModel.SetupState.Done) onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Parchment)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is VaultSetupViewModel.SetupState.Checking -> {
                SpinnerContent("Checking your vault…")
            }

            is VaultSetupViewModel.SetupState.AwaitingUnlock -> {
                PassphraseUnlockContent(
                    errorMessage = s.errorMessage,
                    onSubmit = { vm.submitUnlock(it) },
                )
            }

            is VaultSetupViewModel.SetupState.GeneratingKeys,
            is VaultSetupViewModel.SetupState.Saving -> {
                SpinnerContent(
                    if (s is VaultSetupViewModel.SetupState.GeneratingKeys) "Setting up your vault…" else "Saving…"
                )
            }

            is VaultSetupViewModel.SetupState.AwaitingPassphrase -> {
                PassphraseEntryContent(onSubmit = { vm.submitPassphrase(it) })
            }

            is VaultSetupViewModel.SetupState.Done -> {
                Box(Modifier.fillMaxSize())
            }

            is VaultSetupViewModel.SetupState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OliveBranchIcon(Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Couldn't connect. Try again.",
                        color = Earth,
                        fontStyle = FontStyle.Italic,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = s.message, color = Forest.copy(alpha = 0.5f))
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.startSetup() },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                    ) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun SpinnerContent(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Forest, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(text = label, color = Forest.copy(alpha = 0.7f))
    }
}

@Composable
private fun PassphraseUnlockContent(
    errorMessage: String?,
    onSubmit: (CharArray) -> Unit,
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your vault",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontStyle = FontStyle.Italic,
            color = Forest,
        )

        Text(
            text = "Enter your passphrase to add this device to your vault.",
            fontStyle = FontStyle.Italic,
            color = Forest.copy(alpha = 0.7f),
        )

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(errorMessage, color = Earth) }
            } else null,
            trailingIcon = {
                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                    Icon(
                        imageVector = if (showPassphrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase",
                    )
                }
            },
        )

        Button(
            onClick = { onSubmit(passphrase.toCharArray()) },
            enabled = passphrase.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
        ) {
            Text("Unlock vault")
        }
    }
}

@Composable
private fun PassphraseEntryContent(onSubmit: (CharArray) -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    val passphrasesMismatch = passphrase.isNotEmpty() && confirm.isNotEmpty() && passphrase != confirm
    val canSubmit = passphrase.isNotEmpty() && passphrase == confirm

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your vault",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontStyle = FontStyle.Italic,
            color = Forest,
        )

        Text(
            text = "Your passphrase unlocks your vault on any device. Keep it somewhere safe.",
            fontStyle = FontStyle.Italic,
            color = Forest.copy(alpha = 0.7f),
        )

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                    Icon(
                        imageVector = if (showPassphrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase",
                    )
                }
            },
        )

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm passphrase") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passphrasesMismatch,
            supportingText = if (passphrasesMismatch) {
                { Text("Passphrases don't match") }
            } else null,
        )

        Button(
            onClick = { onSubmit(passphrase.toCharArray()) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
        ) {
            Text("Protect vault")
        }
    }
}
