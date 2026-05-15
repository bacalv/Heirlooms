package digital.heirlooms.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import digital.heirlooms.ui.main.MainApp
import digital.heirlooms.ui.theme.HeirloomsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SEC-009 Part 1 (A-05): prevent screenshots and recent-apps thumbnails from
        // capturing decrypted vault photos/videos. FLAG_SECURE makes the system compositor
        // render a blank frame for both screen-capture APIs and the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
        setContent {
            HeirloomsTheme {
                MainApp()
            }
        }
    }
}
