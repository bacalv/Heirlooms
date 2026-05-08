package digital.heirlooms.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import digital.heirlooms.app.EndpointStore

@Composable
fun MainApp() {
    val context = LocalContext.current
    val store = remember { EndpointStore.create(context) }

    var apiKey by remember { mutableStateOf(store.getApiKey()) }
    var welcomed by remember { mutableStateOf(store.getWelcomed()) }

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
