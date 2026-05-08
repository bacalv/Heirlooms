package digital.heirlooms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import digital.heirlooms.ui.main.MainApp
import digital.heirlooms.ui.theme.HeirloomsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeirloomsTheme {
                MainApp()
            }
        }
    }
}
