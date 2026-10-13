package com.howardaistudio.subscriptionhub.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.LoadingContent
import com.howardaistudio.subscriptionhub.core.ui.MessageContent
import com.howardaistudio.subscriptionhub.core.ui.MonthSwitcher
import com.howardaistudio.subscriptionhub.core.ui.SectionCard
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.core.ui.labelRes
import com.howardaistudio.subscriptionhub.domain.forecast.CurrencyCategoryBreakdown
import com.howardaistudio.subscriptionhub.domain.forecast.EstimatedAmount
import com.howardaistudio.subscriptionhub.domain.forecast.MonthReport
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportContent(state, viewModel::previousMonth, viewModel::nextMonth)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContent(state: ReportUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_report)) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            ReportUiState.Loading -> LoadingContent(modifier)
            is ReportUiState.Ready ->
                if (state.subscriptionCount == 0) {
                    MessageContent(stringResource(R.string.report_empty), modifier)
                } else {
                    ReportBody(state.report, onPrevious, onNext, modifier)
                }
        }
    }
}

@Composable
private fun ReportBody(report: MonthReport, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSwitcher(report.month, onPrevious, onNext)

        SectionCard(stringResource(R.string.report_month_total)) {
            if (report.monthTotals.isEmpty()) {
                Text(stringResource(R.string.report_none))
            } else {
                report.monthTotals.forEach { Text(UiFormat.money(it), style = MaterialTheme.typography.titleLarge) }
            }
        }
        EstimateSection(stringResource(R.string.report_monthly_equivalent), report.monthlyEquivalents)
        EstimateSection(stringResource(R.string.report_yearly_equivalent), report.yearlyEquivalents)

        if (report.categoryBreakdown.isNotEmpty()) {
            SectionCard(stringResource(R.string.report_category_share)) {
                report.categoryBreakdown.forEach { CurrencyBreakdown(it) }
            }
        }

        SectionCard(stringResource(R.string.report_status_counts)) {
            report.statusCounts.forEach { (status, count) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(status.labelRes()), modifier = Modifier.weight(1f))
                    Text(count.toString(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Text(
            stringResource(R.string.estimate_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EstimateSection(title: String, amounts: List<EstimatedAmount>) {
    SectionCard(title) {
        if (amounts.isEmpty()) {
            Text(stringResource(R.string.report_none))
        } else {
            amounts.forEach { Text(UiFormat.estimate(it), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/**
 * Category split of one currency as a sorted, labelled bar list: one series (magnitude), so a
 * single theme colour is enough and identity is carried by the text label, never by colour.
 * Shares are never combined across currencies (SA FR-025, AT-25).
 */
@Composable
private fun CurrencyBreakdown(breakdown: CurrencyCategoryBreakdown) {
    val percent = NumberFormat.getPercentInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            UiFormat.estimate(EstimatedAmount(breakdown.currencyCode, breakdown.total)),
            style = MaterialTheme.typography.titleSmall,
        )
        breakdown.categories.forEach { share ->
            val label = stringResource(share.category.labelRes())
            val shareText = percent.format(share.share)
            val amountText = UiFormat.estimate(EstimatedAmount(breakdown.currencyCode, share.monthlyEquivalent))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$label $shareText $amountText" },
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.report_share_value, shareText, amountText),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ShareBar(share.share)
            }
        }
    }
}

@Composable
private fun ShareBar(share: BigDecimal) {
    val fraction = share.toFloat().coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
