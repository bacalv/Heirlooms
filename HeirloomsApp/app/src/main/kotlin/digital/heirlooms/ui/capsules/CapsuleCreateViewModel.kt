package digital.heirlooms.ui.capsules

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Instant

sealed class CapsuleCreateResult {
    object Idle : CapsuleCreateResult()
    object Submitting : CapsuleCreateResult()
    data class Success(val capsuleId: String) : CapsuleCreateResult()
    data class ValidationError(val message: String) : CapsuleCreateResult()
    data class SubmitError(val message: String) : CapsuleCreateResult()
}

class CapsuleCreateViewModel(
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    val recipient = MutableStateFlow(savedStateHandle.get<String>("recipient") ?: "")
    val unlockDate = MutableStateFlow<LocalDate?>(savedStateHandle.get<LocalDate>("unlockDate"))
    val message = MutableStateFlow(savedStateHandle.get<String>("message") ?: "")
    val selectedPhotos = MutableStateFlow<List<Uri>>(emptyList())
    val isSubmitting = MutableStateFlow(false)

    private val _result = MutableStateFlow<CapsuleCreateResult>(CapsuleCreateResult.Idle)
    val result: StateFlow<CapsuleCreateResult> = _result

    fun setPreSelectedUpload(uploadId: String?) {
        // Pre-selected upload IDs from the share flow are tracked separately;
        // this just records the IDs so submit can include them.
        if (uploadId != null) _preSelectedUploadId = uploadId
    }

    private var _preSelectedUploadId: String? = null

    fun submit(api: HeirloomsApi) {
        val rec = recipient.value.trim()
        val date = unlockDate.value

        if (rec.isEmpty()) {
            _result.value = CapsuleCreateResult.ValidationError("Add at least one recipient.")
            return
        }
        if (date == null) {
            _result.value = CapsuleCreateResult.ValidationError("Choose a date to open on.")
            return
        }
        if (date <= LocalDate.now()) {
            _result.value = CapsuleCreateResult.ValidationError("Unlock date must be in the future.")
            return
        }

        isSubmitting.value = true
        _result.value = CapsuleCreateResult.Submitting

        viewModelScope.launch {
            try {
                val uploadIds = listOfNotNull(_preSelectedUploadId)
                val capsule = api.createCapsule(
                    shape = "open",
                    unlockAt = buildUnlockAt(date),
                    recipients = listOf(rec),
                    uploadIds = uploadIds,
                    message = message.value,
                )
                _result.value = CapsuleCreateResult.Success(capsule.id)
            } catch (e: Exception) {
                _result.value = CapsuleCreateResult.SubmitError(e.message ?: "Something went wrong.")
            } finally {
                isSubmitting.value = false
            }
        }
    }

    fun resetResult() {
        _result.value = CapsuleCreateResult.Idle
    }

    private fun buildUnlockAt(date: LocalDate): String {
        val zoneId = ZoneId.systemDefault()
        val odt = OffsetDateTime.of(date, LocalTime.of(8, 0), zoneId.rules.getOffset(Instant.now()))
        return odt.toString()
    }
}
