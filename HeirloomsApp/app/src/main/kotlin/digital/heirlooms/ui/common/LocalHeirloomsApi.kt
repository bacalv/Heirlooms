package digital.heirlooms.ui.common

import androidx.compose.runtime.compositionLocalOf
import digital.heirlooms.api.HeirloomsApi

val LocalHeirloomsApi = compositionLocalOf<HeirloomsApi> { error("No HeirloomsApi provided") }
