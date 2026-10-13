package com.howardaistudio.subscriptionhub.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.howardaistudio.subscriptionhub.core.ui.theme.SubscriptionHubTheme
import com.howardaistudio.subscriptionhub.domain.forecast.Summaries
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.query.SubscriptionFilter
import com.howardaistudio.subscriptionhub.domain.validation.DraftError
import com.howardaistudio.subscriptionhub.domain.validation.DraftField
import com.howardaistudio.subscriptionhub.feature.calendar.CalendarContent
import com.howardaistudio.subscriptionhub.feature.calendar.CalendarUiState
import com.howardaistudio.subscriptionhub.feature.dashboard.DashboardContent
import com.howardaistudio.subscriptionhub.feature.dashboard.DashboardUiState
import com.howardaistudio.subscriptionhub.feature.report.ReportContent
import com.howardaistudio.subscriptionhub.feature.report.ReportUiState
import com.howardaistudio.subscriptionhub.feature.subscription.detail.SubscriptionDetailContent
import com.howardaistudio.subscriptionhub.feature.subscription.detail.SubscriptionDetailUiState
import com.howardaistudio.subscriptionhub.feature.subscription.edit.SubscriptionEditContent
import com.howardaistudio.subscriptionhub.feature.subscription.edit.SubscriptionFormState
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionListContent
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionListUiState
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionRow
import com.howardaistudio.subscriptionhub.feature.subscription.testSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Screen-level checks of the stateless composables (strings resolve to the zh-TW defaults).
 * Runs on a typical phone window (411 x 891 dp) so lazy lists compose what a user would see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 10)
    private val usd = testSubscription("usd", name = "ChatGPT Plus", minor = 2000, currency = "USD", anchor = "2026-10-15")
    private val twd = testSubscription("twd", name = "Netflix", minor = 39000, currency = "TWD", anchor = "2026-10-15", category = Category.VIDEO)

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SubscriptionHubTheme(darkTheme = false, dynamicColor = false) { content() }
            }
        }
    }

    // SA §4.3 item 4: empty state with a clear next step.
    @Test
    fun `list empty state offers to add the first subscription`() {
        var added = false
        show {
            SubscriptionListContent(
                state = SubscriptionListUiState.Ready(SubscriptionFilter(), emptyList(), totalCount = 0),
                onAdd = { added = true }, onOpen = {}, onQueryChange = {}, onSortChange = {}, onToggleStatus = {}, onToggleCategory = {},
            )
        }
        compose.onNodeWithText("還沒有任何訂閱").assertIsDisplayed()
        compose.onNodeWithText("新增第一筆訂閱").performClick()
        assertTrue(added)
    }

    // AT-08: duplicates are shown, not blocked.
    @Test
    fun `list shows amounts, estimate wording and duplicate hints`() {
        val rows = listOf(
            SubscriptionRow(usd, LocalDate.of(2026, 10, 15), inTrial = false, possibleDuplicate = true),
            SubscriptionRow(usd.copy(id = "dup"), LocalDate.of(2026, 10, 15), inTrial = false, possibleDuplicate = true),
        )
        show {
            SubscriptionListContent(
                state = SubscriptionListUiState.Ready(SubscriptionFilter(), rows, totalCount = 2),
                onAdd = {}, onOpen = {}, onQueryChange = {}, onSortChange = {}, onToggleStatus = {}, onToggleCategory = {},
            )
        }
        compose.onAllNodesWithText("USD 20.00").assertCountEquals(2)
        compose.onAllNodesWithText("預計 2026/10/15 扣款").assertCountEquals(2)
        compose.onAllNodesWithText("可能重複").assertCountEquals(2)
    }

    // AT-02 and the month-end question (dev plan G2).
    @Test
    fun `form shows field errors and the month-end option`() {
        val base = SubscriptionFormState(isNew = true, loading = false)
        show {
            SubscriptionEditContent(
                state = base.copy(
                    draft = base.draft.copy(nextBillingDate = LocalDate.of(2027, 2, 28)),
                    errors = mapOf(DraftField.NAME to DraftError.REQUIRED, DraftField.AMOUNT to DraftError.NOT_POSITIVE),
                ),
                onBack = {}, onChange = {}, onSave = {},
            )
        }
        compose.onNodeWithText("必填").assertExists()
        compose.onNodeWithText("金額需大於 0").assertExists()
        compose.onNodeWithText("每月最後一天扣款").assertExists()
    }

    // AT-14 / AT-23 at 200% font scale (SA NFR-10): currencies stay on separate lines.
    @Test
    fun `dashboard keeps currencies apart at large font scale`() {
        val summary = Summaries.dashboard(listOf(usd, twd), today)
        show(fontScale = 2f) { DashboardContent(DashboardUiState.Ready(summary), onAdd = {}, onOpen = {}, onSettings = {}) }

        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("TWD 390"))
        compose.onNodeWithText("TWD 390").assertExists()
        list.performScrollToNode(hasText("USD 20.00"))
        compose.onNodeWithText("USD 20.00").assertExists()
        compose.onAllNodesWithText("410", substring = true).assertCountEquals(0)
    }

    // FR-021: a deleted subscription opened from a stale link does not crash.
    @Test
    fun `detail not found state`() {
        show {
            SubscriptionDetailContent(
                state = SubscriptionDetailUiState.NotFound,
                onBack = {}, onEdit = {}, onStop = {}, suggestedResumeDate = { today }, onResume = {}, onDelete = {},
            )
        }
        compose.onNodeWithText("找不到這筆訂閱，可能已被刪除。").assertIsDisplayed()
    }

    // AT-24 + touch target size (SA NFR-10).
    @Test
    fun `calendar day cells are large enough and selectable`() {
        var selected: LocalDate? = null
        val state = CalendarUiState(today, YearMonth.of(2026, 10), today, Summaries.calendar(listOf(usd, twd), YearMonth.of(2026, 10)))
        show { CalendarContent(state, onPrevious = {}, onNext = {}, onSelect = { selected = it }, onOpen = {}) }

        val cell = compose.onNodeWithContentDescription("2026/10/15，2 筆預估扣款")
        cell.assertHeightIsAtLeast(48.dp)
        cell.performClick()
        assertEquals(LocalDate.of(2026, 10, 15), selected)
    }

    // AT-25: category share is per currency and carries a text label.
    @Test
    fun `report labels category shares per currency`() {
        val report = Summaries.report(listOf(usd, twd), YearMonth.of(2026, 10))
        show { ReportContent(ReportUiState.Ready(report, subscriptionCount = 2), onPrevious = {}, onNext = {}) }

        val body = compose.onNode(hasScrollAction())
        body.performScrollToNode(hasText("TWD 390"))
        assertTrue(compose.onAllNodesWithText("TWD 390").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("影音串流 100%", substring = true).assertExists()
        compose.onNodeWithContentDescription("AI 工具 100%", substring = true).assertExists()
    }
}
