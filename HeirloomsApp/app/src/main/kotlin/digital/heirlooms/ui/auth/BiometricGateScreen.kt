package digital.heirlooms.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment

/**
 * SEC-015: Biometric gate screen shown before vault content when `require_biometric = true`.
 *
 * If the device has no enrolled biometric hardware, the prompt falls back to device credential
 * (PIN/pattern/password) via [DEVICE_CREDENTIAL]. If neither is available, the gate is bypassed
 * and [onAuthenticated] is called immediately so the user is never locked out.
 */
@Composable
fun BiometricGateScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current

    var error by remember { mutableStateOf<String?>(null) }
    var canAuthenticate by remember { mutableStateOf(true) }

    // Check whether any authenticator is enrolled.
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        val canUseBiometric = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        if (canUseBiometric == BiometricManager.BIOMETRIC_SUCCESS ||
            canUseBiometric == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            canAuthenticate = true
        } else {
            // No biometric hardware and no device credential — bypass gate.
            canAuthenticate = false
            onAuthenticated()
            return@LaunchedEffect
        }
        showBiometricPrompt(context, onAuthenticated) { msg -> error = msg }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Parchment)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Biometric required",
            style = HeirloomsSerifItalic.copy(fontSize = 26.sp, color = Forest),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Authenticate to open your vault.",
            style = MaterialTheme.typography.bodyMedium,
            color = Forest,
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Earth,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    error = null
                    showBiometricPrompt(context, onAuthenticated) { msg -> error = msg }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) {
                Text("Try again")
            }
        }
    }
}

private fun showBiometricPrompt(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val activity = context as? FragmentActivity ?: run {
        // Cannot show prompt without a FragmentActivity — bypass gate.
        onSuccess()
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // NEGATIVE_BUTTON or user cancellation — show error so user can retry.
            onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
            onError("Biometric not recognised. Please try again.")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock vault")
        .setSubtitle("Use your biometric or device credential")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}
