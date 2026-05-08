@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package digital.heirlooms.ui.capsules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.formatOffsetDate
import digital.heirlooms.ui.main.Routes
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun CapsuleCreateScreen(
    preSelectedUploadId: String?,
    navController: NavHostController,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var recipients by remember { mutableStateOf(listOf<String>()) }
    var recipientInput by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedUploadIds by remember {
        mutableStateOf(listOfNotNull(preSelectedUploadId))
    }
    var message by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Recipient-aware placeholder
    val messagePlaceholder = remember(recipients) {
        when {
            recipients.isEmpty() -> "Write a message…"
            recipients.size == 1 -> "Write something for ${recipients[0]}…"
            else -> "Write something for them…"
        }
    }

    // Observe photo picker result returned via previousBackStackEntry?.savedStateHandle.
    // We watch the current back-stack entry so the effect re-fires when we return from picker.
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        val ids = currentEntry?.savedStateHandle?.get<List<String>>("pickerResult")
        if (!ids.isNullOrEmpty()) {
            selectedUploadIds = ids
            currentEntry?.savedStateHandle?.remove<List<String>>("pickerResult")
        }
    }

    val formHasData = recipients.isNotEmpty() || recipientInput.isNotBlank() ||
        selectedDate != null || selectedUploadIds.isNotEmpty() || message.isNotBlank()

    fun addRecipient() {
        val name = recipientInput.trim()
        if (name.isNotEmpty()) {
            recipients = recipients + name
            recipientInput = ""
        }
    }

    fun buildUnlockAt(date: LocalDate): String {
        // 8am sender's local timezone on chosen date — matches web's v0.18.0 convention.
        val zoneId = ZoneId.systemDefault()
        val odt = OffsetDateTime.of(date, LocalTime.of(8, 0), zoneId.rules.getOffset(Instant.now()))
        return odt.toString()
    }

    fun submit(shape: String) {
        addRecipient() // flush any typed-but-uncommitted recipient
        val finalRecipients = recipients.ifEmpty {
            if (recipientInput.isNotBlank()) listOf(recipientInput.trim()) else emptyList()
        }
        val date = selectedDate

        if (finalRecipients.isEmpty()) { submitError = "Add at least one recipient."; return }
        if (date == null) { submitError = "Choose a date to open on."; return }
        if (shape == "sealed" && selectedUploadIds.isEmpty()) {
            submitError = "A sealed capsule needs at least one thing inside."
            return
        }

        scope.launch {
            isSubmitting = true
            submitError = null
            try {
                val result = api.createCapsule(
                    shape = shape,
                    unlockAt = buildUnlockAt(date),
                    recipients = finalRecipients,
                    uploadIds = selectedUploadIds,
                    message = message,
                )
                onCreated(result.id)
            } catch (e: Exception) {
                submitError = e.message ?: "Something went wrong."
            } finally {
                isSubmitting = false
            }
        }
    }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Start a capsule", style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
                navigationIcon = {
                    IconButton(onClick = { if (formHasData) showDiscardDialog = true else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Forest)
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text("Seal capsule") },
                            onClick = { showOverflow = false; submit("sealed") },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
        bottomBar = {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = { submit("open") },
                    enabled = !isSubmitting,
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
                        if (isSubmitting) "Starting…" else "Start a capsule",
                        style = HeirloomsSerifItalic.copy(fontSize = 16.sp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Plant something for someone.",
                style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            // For field
            Text("For", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                recipients.forEach { name ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(name) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { recipients = recipients - name },
                            )
                        },
                    )
                }
                BasicTextField(
                    value = recipientInput,
                    onValueChange = { input ->
                        if (input.endsWith(",")) {
                            val name = input.dropLast(1).trim()
                            if (name.isNotEmpty()) { recipients = recipients + name; recipientInput = "" }
                        } else {
                            recipientInput = input
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Forest),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addRecipient() }),
                    modifier = Modifier.padding(vertical = 8.dp),
                    decorationBox = { inner ->
                        if (recipientInput.isEmpty() && recipients.isEmpty()) {
                            Text("Sophie", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            // To open on field
            Text("To open on", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .background(Forest08, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    selectedDate?.let {
                        it.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                    } ?: "Pick a date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedDate != null) Forest else TextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            // Include field
            Text("Include", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            if (selectedUploadIds.isEmpty()) {
                Button(
                    onClick = {
                        navController.currentBackStackEntry?.savedStateHandle
                            ?.set("pickerPreselected", emptyList<String>())
                        navController.navigate(Routes.PHOTO_PICKER)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) {
                    Text("Choose what to include")
                }
            } else {
                Text(
                    "${selectedUploadIds.size} item${if (selectedUploadIds.size == 1) "" else "s"} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Forest,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    navController.currentBackStackEntry?.savedStateHandle
                        ?.set("pickerPreselected", selectedUploadIds)
                    navController.navigate(Routes.PHOTO_PICKER)
                }) {
                    Text("Change selection", color = TextMuted)
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            // Message field
            Text("Message", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(messagePlaceholder, color = TextMuted) },
                minLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Forest,
                    unfocusedBorderColor = Forest15,
                    cursorColor = Forest,
                ),
            )

            if (submitError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    submitError ?: "",
                    style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = Earth),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this capsule?", style = HeirloomsSerifItalic.copy(fontSize = 18.sp)) },
            confirmButton = {
                Button(
                    onClick = { showDiscardDialog = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Earth, contentColor = Parchment),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing", color = Forest) }
            },
            containerColor = Parchment,
        )
    }
}

