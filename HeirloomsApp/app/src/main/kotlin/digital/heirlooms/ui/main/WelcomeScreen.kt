package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Parchment)
            .padding(horizontal = 32.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "A place to keep what matters.",
            style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 80.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Forest,
                contentColor = Parchment,
            ),
        ) {
            Text(
                text = "Get started",
                style = HeirloomsSerifItalic.copy(fontSize = 16.sp),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
