package digital.heirlooms.ui.brand

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.heirlooms.ui.theme.HeirloomsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class OliveBranchDidntTakeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_didnt_take_text() {
        composeRule.setContent {
            HeirloomsTheme {
                OliveBranchDidntTake(reduceMotion = true)
            }
        }
        composeRule.onNodeWithText("didn't take").assertExists()
    }

    @Test
    fun fires_on_complete_under_reduced_motion() {
        var callCount = 0
        composeRule.setContent {
            HeirloomsTheme {
                OliveBranchDidntTake(reduceMotion = true, onComplete = { callCount++ })
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, callCount)
    }
}
