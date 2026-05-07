package digital.heirlooms.ui.brand

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.heirlooms.ui.theme.HeirloomsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class OliveBranchArrivalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_wordmark_by_default() {
        composeRule.setContent {
            HeirloomsTheme {
                OliveBranchArrival(reduceMotion = true)
            }
        }
        composeRule.onNodeWithText("Heirlooms").assertExists()
    }

    @Test
    fun omits_wordmark_when_with_wordmark_false() {
        composeRule.setContent {
            HeirloomsTheme {
                OliveBranchArrival(withWordmark = false, reduceMotion = true)
            }
        }
        composeRule.onAllNodesWithText("Heirlooms").fetchSemanticsNodes().let { nodes ->
            assertEquals(0, nodes.size)
        }
    }

    @Test
    fun fires_on_complete_under_reduced_motion() {
        var callCount = 0
        composeRule.setContent {
            HeirloomsTheme {
                OliveBranchArrival(reduceMotion = true, onComplete = { callCount++ })
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, callCount)
    }
}
