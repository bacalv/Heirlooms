package digital.heirlooms.ui.share

import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.heirlooms.app.ReceiveState
import digital.heirlooms.ui.theme.HeirloomsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdleScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun uri(n: Int): Uri = Uri.parse("content://test/$n")

    private val uri1 = uri(1)

    @Test
    fun renders_grid_for_three_photos() {
        composeRule.setContent {
            HeirloomsTheme {
                IdleScreen(
                    state = ReceiveState.Idle(photos = listOf(uri(1), uri(2), uri(3))),
                    onTagInputChanged = {},
                    onTagCommit = {},
                    onTagRemoved = {},
                    onRecentTagTapped = {},
                    onPlant = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("3 photos").assertDoesNotExist()
    }

    @Test
    fun renders_strip_for_eight_photos() {
        val photos = (1..8).map { uri(it) }
        composeRule.setContent {
            HeirloomsTheme {
                IdleScreen(
                    state = ReceiveState.Idle(photos = photos),
                    onTagInputChanged = {},
                    onTagCommit = {},
                    onTagRemoved = {},
                    onRecentTagTapped = {},
                    onPlant = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("8 photos").assertExists()
    }

    @Test
    fun plant_disabled_when_current_input_invalid() {
        var planted = false
        composeRule.setContent {
            HeirloomsTheme {
                IdleScreen(
                    state = ReceiveState.Idle(
                        photos = listOf(uri1),
                        currentTagInput = "Bad Tag!",
                    ),
                    onTagInputChanged = {},
                    onTagCommit = {},
                    onTagRemoved = {},
                    onRecentTagTapped = {},
                    onPlant = { planted = true },
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("plant").performClick()
        assertFalse(planted)
    }

    @Test
    fun recent_tag_tap_invokes_callback() {
        var tapped: String? = null
        composeRule.setContent {
            HeirloomsTheme {
                IdleScreen(
                    state = ReceiveState.Idle(
                        photos = listOf(uri1),
                        recentTags = listOf("family", "summer-2024"),
                    ),
                    onTagInputChanged = {},
                    onTagCommit = {},
                    onTagRemoved = {},
                    onRecentTagTapped = { tapped = it },
                    onPlant = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText("summer-2024").performClick()
        assertEquals("summer-2024", tapped)
    }
}
