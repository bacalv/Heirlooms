package digital.heirlooms.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import digital.heirlooms.app.EndpointStore

@Composable
fun MainApp() {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }

    // rememberSaveable so the key survives Activity recreation (rotation, OS process kill + restore).
    // SharedPreferences is the authoritative store; this is a Bundle-backed safety net.
    var apiKey by rememberSaveable { mutableStateOf(store.getApiKey()) }
    var welcomed by rememberSaveable { mutableStateOf(store.getWelcomed()) }

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
        else -> MainNavigation(
            apiKey = apiKey,
            onApiKeyReset = {
                store.setApiKey("")
                apiKey = ""
                // Welcome flag intentionally NOT reset — welcome shows once per install.
            },
        )
    }
}
