package digital.heirlooms.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.app.BuildConfig
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Web base URL derived from the flavor's API base URL (BuildConfig.BASE_URL_OVERRIDE).
 *
 * - Staging API:  https://test.api.heirlooms.digital  → https://test.heirlooms.digital
 * - Prod API:     "" (empty — no override)             → https://heirlooms.digital
 *
 * This mirrors the approach used to fix BUG-003 in UploadWorker.
 */
private val INVITE_BASE_URL: String
    get() = if (BuildConfig.BASE_URL_OVERRIDE.isNotEmpty())
        BuildConfig.BASE_URL_OVERRIDE
            .replace("test.api.", "test.")
            .replace("api.", "")
            .trimEnd('/')
    else "https://heirlooms.digital"
private const val QR_SIZE = 300

private fun generateQrBitmap(content: String): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
    Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.RGB_565).apply {
        for (x in 0 until QR_SIZE) {
            for (y in 0 until QR_SIZE) {
                setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
} catch (_: Exception) { null }

private fun formatExpiry(iso: String): String = try {
    val instant = Instant.parse(iso)
    val dt = instant.atZone(ZoneId.systemDefault())
    "Expires ${DateTimeFormatter.ofPattern("d MMM, HH:mm").format(dt)}"
} catch (_: Exception) { "" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesAccessScreen(onBack: () -> Unit, onNavigateToPairing: () -> Unit) {
    val context = LocalContext.current
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var inviteQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inviteUrl by remember { mutableStateOf<String?>(null) }
    var inviteExpiry by remember { mutableStateOf("") }
    var inviteWorking by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf<String?>(null) }

    var pairingCode by remember { mutableStateOf<String?>(null) }
    var pairingExpiry by remember { mutableStateOf("") }
    var pairingWorking by remember { mutableStateOf(false) }
    var pairingError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Devices & Access", style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                    }
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Invite someone ──────────────────────────────────────────────
            Text("Invite someone", style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest))
            Spacer(Modifier.height(8.dp))
            Text("Generate a link for a new user to create their account.",
                style = MaterialTheme.typography.bodySmall, color = Forest)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        inviteWorking = true
                        inviteError = null
                        try {
                            val resp = api.getInvite()
                            val url = "$INVITE_BASE_URL/join?token=${resp.token}"
                            inviteUrl = url
                            inviteExpiry = formatExpiry(resp.expiresAt)
                            inviteQrBitmap = generateQrBitmap(url)
                        } catch (e: Exception) {
                            inviteError = e.message ?: "Could not generate invite."
                        } finally {
                            inviteWorking = false
                        }
                    }
                },
                enabled = !inviteWorking,
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) { Text(if (inviteWorking) "Generating…" else "Generate invite link") }

            inviteError?.let { Text(it, color = Earth, style = MaterialTheme.typography.bodySmall) }

            inviteQrBitmap?.let { bmp ->
                Spacer(Modifier.height(12.dp))
                Image(bmp.asImageBitmap(), contentDescription = "Invite QR code",
                    modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(4.dp))
                Text(inviteExpiry, style = MaterialTheme.typography.bodySmall, color = Forest)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, inviteUrl)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share invite link"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text("Share invite link") }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(24.dp))

            // ── Link a web browser ──────────────────────────────────────────
            Text("Link a web browser", style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest))
            Spacer(Modifier.height(8.dp))
            Text("Generate a code for your browser to scan.",
                style = MaterialTheme.typography.bodySmall, color = Forest)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        pairingWorking = true
                        pairingError = null
                        try {
                            val resp = api.pairingInitiate()
                            pairingCode = resp.code
                            pairingExpiry = formatExpiry(resp.expiresAt)
                        } catch (e: Exception) {
                            pairingError = e.message ?: "Could not generate code."
                        } finally {
                            pairingWorking = false
                        }
                    }
                },
                enabled = !pairingWorking,
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) { Text(if (pairingWorking) "Generating…" else "Generate pairing code") }

            pairingError?.let { Text(it, color = Earth, style = MaterialTheme.typography.bodySmall) }

            pairingCode?.let { code ->
                Spacer(Modifier.height(12.dp))
                Text(
                    code,
                    style = HeirloomsSerifItalic.copy(fontSize = 40.sp, color = Forest),
                )
                Text(pairingExpiry, style = MaterialTheme.typography.bodySmall, color = Forest)
                Spacer(Modifier.height(8.dp))
                Text("Type this code into ${INVITE_BASE_URL.removePrefix("https://")} on your browser. Then tap Scan QR below.",
                    style = MaterialTheme.typography.bodySmall, color = Forest)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToPairing,
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text("Scan QR code") }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
