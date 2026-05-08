package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun ApiKeyScreen(onKeyEntered: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val canSave = input.trim().isNotEmpty()

    fun submit() { if (canSave) onKeyEntered(input.trim()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Parchment)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Heirlooms",
            style = HeirloomsSerifItalic.copy(fontSize = 28.sp, color = Forest),
        )
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Enter your API key to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("API key", color = TextMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedBorderColor = Forest25,
                cursorColor = Forest,
            ),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::submit,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Forest,
                contentColor = Parchment,
                disabledContainerColor = Forest25,
                disabledContentColor = TextMuted,
            ),
        ) {
            Text(
                text = "Continue",
                style = HeirloomsSerifItalic.copy(fontSize = 16.sp),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
