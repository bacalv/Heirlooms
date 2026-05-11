package digital.heirlooms.ui.main

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import digital.heirlooms.app.PairingQrParser
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var qrJson by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    fun submit(json: String) {
        scope.launch {
            working = true
            error = null
            try {
                val parsed = when (val r = PairingQrParser.parse(json.trim())) {
                    is PairingQrParser.ParseResult.Error -> {
                        error = r.message
                        return@launch
                    }
                    is PairingQrParser.ParseResult.Success -> r.payload
                }
                val masterKey = VaultSession.masterKey
                val recipientSpki = withContext(Dispatchers.IO) {
                    VaultCrypto.fromBase64Url(parsed.pubkey)
                }
                val wrapped = withContext(Dispatchers.IO) {
                    VaultCrypto.wrapMasterKeyForRecipient(masterKey, recipientSpki)
                }
                val wrappedB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
                api.pairingComplete(parsed.sessionId, wrappedB64, parsed.pubkey)
                success = true
                onPaired()
            } catch (e: Exception) {
                error = e.message ?: "Pairing failed."
            } finally {
                working = false
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { json -> submit(json) }
    }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Pair browser", style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            if (success) {
                Text("Browser linked successfully.",
                    style = HeirloomsSerifItalic.copy(fontSize = 20.sp, color = Forest))
                return@Column
            }

            Text("Point your camera at the QR code shown by your browser.",
                style = MaterialTheme.typography.bodyMedium, color = Forest)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scanLauncher.launch(ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan the browser pairing QR code")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    })
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) { Text("Scan QR code") }

            Spacer(Modifier.height(24.dp))
            Text("Or paste the QR code's JSON content:",
                style = MaterialTheme.typography.bodyMedium, color = Forest)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = qrJson, onValueChange = { qrJson = it },
                label = { Text("QR code JSON") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest, unfocusedBorderColor = Forest15),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = Earth))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { submit(qrJson) },
                enabled = !working && qrJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
            ) { Text(if (working) "Linking…" else "Link browser") }
        }
    }
}
