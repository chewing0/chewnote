package com.example.myapplication.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.LedgerCategoryCatalog
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LedgerPeriod
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.EditorialTitle
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentMoss
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import com.example.myapplication.ui.theme.LineSoft
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue

@Composable
fun LedgerScreen(
    viewModel: AppViewModel,
    initialPeriod: LedgerPeriod = LedgerPeriod.MONTH,
    highlightBatchId: String? = null,
) {
    val entries by viewModel.ledgerEntries.collectAsState()
    var period by remember { mutableStateOf(initialPeriod) }
    var anchorDate by remember { mutableStateOf(currentAnchor(initialPeriod)) }
    var typeFilter by remember { mutableStateOf(LedgerTypeFilter.ALL) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialPeriod) {
        period = initialPeriod
        anchorDate = currentAnchor(initialPeriod)
    }

    val currentPeriodEntries = remember(entries, period, anchorDate) {
        filterEntriesByPeriod(entries, period, anchorDate)
    }
    val previousPeriodEntries = remember(entries, period, anchorDate) {
        filterEntriesByPeriod(entries, period, previousAnchor(period, anchorDate))
    }
    val filteredEntries = remember(currentPeriodEntries, typeFilter) {
        filterEntriesByType(currentPeriodEntries, typeFilter)
    }
    val previousFilteredEntries = remember(previousPeriodEntries, typeFilter) {
        filterEntriesByType(previousPeriodEntries, typeFilter)
    }
    val analysisFilter = remember(typeFilter) {
        if (typeFilter == LedgerTypeFilter.ALL) LedgerTypeFilter.EXPENSE else typeFilter
    }
    val insightEntries = remember(currentPeriodEntries, analysisFilter) {
        filterEntriesByType(currentPeriodEntries, analysisFilter)
    }
    val highlightedEntries = remember(entries, highlightBatchId) {
        if (highlightBatchId.isNullOrBlank()) {
            emptyList()
        } else {
            entries
                .filter { it.actionBatchId == highlightBatchId }
                .sortedWith(compareByDescending<LedgerEntry> { parseDateSafe(it.date) }.thenByDescending { it.createdAt })
        }
    }
    val summaryData = remember(
        currentPeriodEntries,
        previousPeriodEntries,
        filteredEntries,
        previousFilteredEntries,
        period,
        anchorDate,
        typeFilter,
    ) {
        buildSummaryData(
            period = period,
            periodLabel = periodSummaryLabel(period, anchorDate),
            typeFilter = typeFilter,
            currentEntries = currentPeriodEntries,
            previousEntries = previousPeriodEntries,
            filteredEntries = filteredEntries,
            previousFilteredEntries = previousFilteredEntries,
        )
    }
    val insightCards = remember(insightEntries, typeFilter) {
        buildInsightCards(insightEntries, typeFilter)
    }
    val categoryBreakdown = remember(insightEntries) {
        buildCategoryBreakdown(insightEntries)
    }
    val trendData = remember(entries, period, typeFilter, anchorDate) {
        buildTrendData(entries, period, typeFilter, anchorDate)
    }
    val watchListEntries = remember(insightEntries) {
        insightEntries
            .sortedWith(
                compareByDescending<LedgerEntry> { it.amount.absoluteValue }
                    .thenByDescending { parseDateSafe(it.date) }
                    .thenByDescending { it.createdAt }
            )
            .take(3)
    }

    EditorialBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                EditorialReveal(delayMillis = 0) {
                    EditorialPanel(modifier = Modifier.padding(top = 12.dp)) {
                        EditorialTitle(
                            title = "记账统计",
                            subtitle = "把本期变化、结构和重点支出放到一个页面里看清楚",
                            showSubtitle = false,
                            modifier = Modifier.padding(12.dp),
                            trailing = {
                                TonePill(text = period.displayName(), tone = AccentVermilion)
                            },
                        )
                    }
                }
            }

            item {
                EditorialReveal(delayMillis = 70) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerPeriod.values().forEach { current ->
                            FilterChip(
                                selected = period == current,
                                onClick = {
                                    period = current
                                    anchorDate = normalizeAnchor(current, anchorDate)
                                },
                                label = { Text(current.displayName()) },
                            )
                        }
                    }
                }
            }

            item {
                PeriodAnchorSelector(
                    period = period,
                    anchorDate = anchorDate,
                    onPrevious = { anchorDate = shiftAnchor(anchorDate, period, -1) },
                    onNext = { anchorDate = shiftAnchor(anchorDate, period, 1) },
                    onCurrent = { anchorDate = currentAnchor(period) },
                    onPick = { showPeriodPicker = true },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerTypeFilter.values().forEach { current ->
                        FilterChip(
                            selected = typeFilter == current,
                            onClick = { typeFilter = current },
                            label = { Text(current.label) },
                        )
                    }
                }
            }

            if (highlightedEntries.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8EEE1)),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = "刚刚新增",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkDeep,
                            )
                            Text(
                                text = "你是从聊天回执打开的，这一批账单会在下方继续高亮显示。",
                                color = InkSoft,
                            )
                            highlightedEntries.take(3).forEach { entry ->
                                LedgerCompactHighlight(entry = entry)
                            }
                        }
                    }
                }
            }

            item {
                EditorialReveal(delayMillis = 130) {
                    AnimatedContent(
                        targetState = period to typeFilter,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ledger-summary",
                    ) {
                        SummaryCard(summaryData = summaryData)
                    }
                }
            }

            item {
                InsightSection(
                    cards = insightCards,
                    analysisFilter = analysisFilter,
                    showExpenseHint = typeFilter == LedgerTypeFilter.ALL,
                )
            }

            item {
                CategorySection(
                    breakdown = categoryBreakdown,
                    analysisFilter = analysisFilter,
                    showExpenseHint = typeFilter == LedgerTypeFilter.ALL,
                )
            }

            item {
                TrendSection(
                    trendData = trendData,
                    period = period,
                    typeFilter = typeFilter,
                    anchorDate = anchorDate,
                )
            }

            item {
                WatchListSection(
                    entries = watchListEntries,
                    typeFilter = typeFilter,
                )
            }

            item {
                Text(
                    text = "账单明细",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkDeep,
                )
            }

            if (filteredEntries.isEmpty()) {
                item {
                    EmptyPanel(message = "当前筛选下还没有账单记录。")
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    LedgerItem(
                        entry = entry,
                        highlighted = !highlightBatchId.isNullOrBlank() && entry.actionBatchId == highlightBatchId,
                        onEdit = { editingEntry = it },
                        onDelete = { viewModel.deleteLedger(it.id) },
                    )
                }
            }
        }
    }

    editingEntry?.let { entry ->
        LedgerEditDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onConfirm = {
                viewModel.updateLedger(it)
                editingEntry = null
            },
        )
    }

    if (showPeriodPicker) {
        PeriodPickerDialog(
            period = period,
            anchorDate = anchorDate,
            onDismiss = { showPeriodPicker = false },
            onConfirm = {
                anchorDate = normalizeAnchor(period, it)
                showPeriodPicker = false
            },
        )
    }
}

@Composable
private fun PeriodAnchorSelector(
    period: LedgerPeriod,
    anchorDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onPick: () -> Unit,
) {
    val isCurrentPeriod = normalizeAnchor(period, anchorDate) == currentAnchor(period)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF2)),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(Color(0xFFEADCC8), Color(0xFFFFF5E5)),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xFFF3E8D7),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    IconButton(
                        modifier = Modifier.size(38.dp),
                        onClick = onPrevious,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = period.previousButtonLabel(),
                            tint = InkDeep,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = periodRangeLabel(period, anchorDate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPick),
                        style = if (period == LedgerPeriod.WEEK) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = InkDeep,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${period.displayName()} · 点按选择",
                            modifier = Modifier.clickable(onClick = onPick),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        if (!isCurrentPeriod) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                            )
                            Text(
                                text = "回到${period.currentButtonLabel()}",
                                modifier = Modifier.clickable(onClick = onCurrent),
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentMoss,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFF3E8D7),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    IconButton(
                        modifier = Modifier.size(38.dp),
                        onClick = onNext,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = period.nextButtonLabel(),
                            tint = InkDeep,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun PeriodPickerDialog(
    period: LedgerPeriod,
    anchorDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val normalizedAnchor = normalizeAnchor(period, anchorDate)
    var dateText by remember(period, anchorDate) { mutableStateOf(normalizedAnchor.toString()) }
    var yearText by remember(period, anchorDate) { mutableStateOf(normalizedAnchor.year.toString()) }
    var monthText by remember(period, anchorDate) { mutableStateOf(normalizedAnchor.monthValue.toString()) }
    var errorText by remember(period, anchorDate) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择${period.pickerTitle()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (period) {
                    LedgerPeriod.DAY -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = dateText,
                            onValueChange = {
                                dateText = it
                                errorText = null
                            },
                            label = { Text("日期 YYYY-MM-DD") },
                            singleLine = true,
                        )
                    }

                    LedgerPeriod.WEEK -> {
                        Text(
                            text = "输入这一周中的任意一天，按周一到周日统计。",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = dateText,
                            onValueChange = {
                                dateText = it
                                errorText = null
                            },
                            label = { Text("周内日期 YYYY-MM-DD") },
                            singleLine = true,
                        )
                    }

                    LedgerPeriod.MONTH -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = yearText,
                                onValueChange = {
                                    yearText = it
                                    errorText = null
                                },
                                label = { Text("年") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = monthText,
                                onValueChange = {
                                    monthText = it
                                    errorText = null
                                },
                                label = { Text("月") },
                                singleLine = true,
                            )
                        }
                    }

                    LedgerPeriod.YEAR -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = yearText,
                            onValueChange = {
                                yearText = it
                                errorText = null
                            },
                            label = { Text("年份") },
                            singleLine = true,
                        )
                    }
                }

                val previewAnchor = parsePeriodPickerInput(
                    period = period,
                    dateText = dateText,
                    yearText = yearText,
                    monthText = monthText,
                ) ?: normalizedAnchor
                Text(
                    text = "选中范围：${periodRangeLabel(period, previewAnchor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentVermilion,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = parsePeriodPickerInput(
                        period = period,
                        dateText = dateText,
                        yearText = yearText,
                        monthText = monthText,
                    )
                    if (parsed == null) {
                        errorText = "请输入有效的${period.pickerTitle()}。"
                    } else {
                        onConfirm(parsed)
                    }
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun SummaryCard(summaryData: SummaryCardData) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFF6E6), Color(0xFFF4E2C3)),
                    ),
                    shape = RoundedCornerShape(14.dp),
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = 760f,
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = summaryData.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                    )
                    Text(
                        text = summaryData.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = summaryData.headlineTone,
                    )
                }
                TonePill(
                    text = summaryData.compareBadge,
                    tone = summaryData.compareTone,
                )
            }

            Text(
                text = summaryData.compareText,
                color = summaryData.compareTextTone,
                style = MaterialTheme.typography.bodyMedium,
            )

            summaryData.metricRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { metric ->
                        SummaryMetricCard(
                            modifier = Modifier.weight(1f),
                            label = metric.label,
                            value = metric.value,
                            tone = metric.tone,
                        )
                    }
                    repeat((2 - row.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSection(
    cards: List<InsightCardData>,
    analysisFilter: LedgerTypeFilter,
    showExpenseHint: Boolean,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 740f,
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("关键洞察", style = MaterialTheme.typography.titleMedium, color = InkDeep)
                    Text(
                        text = if (showExpenseHint) {
                            "总览按收支显示，这里聚焦最值得关注的支出变化。"
                        } else {
                            "把这一周期里最有代表性的 ${analysisFilter.subjectLabel()} 结论直接拎出来。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                }
                if (showExpenseHint) {
                    TonePill(text = "按支出分析", tone = AccentMoss)
                }
            }

            if (cards.isEmpty()) {
                EmptyPanel(message = "当前周期还没有可分析的${analysisFilter.subjectLabel()}记录。")
            } else {
                InsightHeroCard(
                    card = cards.first(),
                    sectionLabel = if (analysisFilter == LedgerTypeFilter.INCOME) "本期收入焦点" else "本期支出焦点",
                )

                val secondaryCards = cards.drop(1)
                if (secondaryCards.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        secondaryCards.forEach { card ->
                            InsightMiniCard(
                                card = card,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat((2 - secondaryCards.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightHeroCard(
    card: InsightCardData,
    sectionLabel: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(card.tone.copy(alpha = 0.45f), Color(0xFFE9D5BA)),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFFFBF4),
                            card.tone.copy(alpha = 0.12f),
                        ),
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TonePill(text = sectionLabel, tone = card.tone)
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            Text(
                text = card.value,
                style = MaterialTheme.typography.headlineSmall,
                color = card.tone,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun InsightMiniCard(
    card: InsightCardData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 132.dp),
        color = Color(0xFFFFFAF2),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(card.tone, RoundedCornerShape(99.dp)),
                )
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            Text(
                text = card.value,
                style = MaterialTheme.typography.titleMedium,
                color = card.tone,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun CategorySection(
    breakdown: CategoryBreakdown,
    analysisFilter: LedgerTypeFilter,
    showExpenseHint: Boolean,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 750f,
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${analysisFilter.subjectLabel()}分类结构",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkDeep,
                )
                if (showExpenseHint) {
                    TonePill(text = "默认看支出", tone = AccentMoss)
                }
            }

            if (breakdown.items.isEmpty()) {
                EmptyPanel(message = "当前周期还没有可绘制分类结构的记录。")
            } else {
                PieChart(items = breakdown.items)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    breakdown.items.forEach { item ->
                        CategoryRankRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendSection(
    trendData: List<TrendPoint>,
    period: LedgerPeriod,
    typeFilter: LedgerTypeFilter,
    anchorDate: LocalDate,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 750f,
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = period.trendTitle(typeFilter, anchorDate),
                style = MaterialTheme.typography.titleMedium,
                color = InkDeep,
            )

            if (trendData.isEmpty()) {
                EmptyPanel(message = "当前周期还没有趋势数据。")
            } else {
                if (typeFilter == LedgerTypeFilter.ALL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrendLegendChip(label = "支出", color = TrendExpenseTone)
                        TrendLegendChip(label = "收入", color = TrendIncomeTone)
                    }
                }
                TrendBarChart(
                    data = trendData,
                    dualSeries = typeFilter == LedgerTypeFilter.ALL,
                )
            }
        }
    }
}

@Composable
private fun WatchListSection(
    entries: List<LedgerEntry>,
    typeFilter: LedgerTypeFilter,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 760f,
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "值得关注",
                style = MaterialTheme.typography.titleMedium,
                color = InkDeep,
            )

            if (entries.isEmpty()) {
                EmptyPanel(message = "当前周期还没有需要重点关注的${typeFilter.focusLabel()}记录。")
            } else {
                Text(
                    text = typeFilter.watchListHint(),
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
                entries.forEachIndexed { index, entry ->
                    WatchListItem(rank = index + 1, entry = entry)
                }
            }
        }
    }
}

@Composable
private fun EmptyPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFFBF5),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            color = InkSoft,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun CategoryRankRow(item: CategoryBreakdownItem) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.label,
                color = InkDeep,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "¥${"%.2f".format(item.total)}",
                color = InkDeep,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "占比 ${"%.0f".format(item.ratio * 100)}% · ${item.count} 笔",
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = item.labelHint,
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFF0E5D6), RoundedCornerShape(99.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.ratio.toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(item.color, RoundedCornerShape(99.dp)),
            )
        }
    }
}

@Composable
private fun PieChart(items: List<CategoryBreakdownItem>) {
    val animatedValues = items.map { item ->
        animateFloatAsState(
            targetValue = item.total.toFloat(),
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 720f),
            label = "pie-${item.label}",
        ).value.toDouble()
    }
    val total = animatedValues.sum().coerceAtLeast(1.0)

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(modifier = Modifier.size(168.dp)) {
            var startAngle = -90f
            items.forEachIndexed { index, item ->
                val sweep = ((animatedValues[index] / total) * 360f).toFloat()
                drawArc(
                    color = item.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
                startAngle += sweep
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.take(5).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(item.color, shape = RoundedCornerShape(99.dp)),
                    )
                    Text(
                        text = "${item.label} ${"%.0f".format(item.ratio * 100)}%",
                        color = InkDeep,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendLegendChip(label: String, color: Color) {
    Surface(
        color = Color(0xFFFFFBF5),
        shape = RoundedCornerShape(99.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(99.dp)),
            )
            Text(
                text = label,
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrendBarChart(
    data: List<TrendPoint>,
    dualSeries: Boolean,
) {
    val animatedPrimary = data.map { item ->
        animateFloatAsState(
            targetValue = item.primaryValue.toFloat(),
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 700f),
            label = "trend-primary-${item.label}",
        ).value.toDouble()
    }
    val animatedSecondary = data.map { item ->
        animateFloatAsState(
            targetValue = item.secondaryValue.toFloat(),
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 700f),
            label = "trend-secondary-${item.label}",
        ).value.toDouble()
    }
    val maxValue = remember(animatedPrimary, animatedSecondary) {
        (animatedPrimary + animatedSecondary).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            val groupWidth = size.width / data.size.coerceAtLeast(1)
            val chartBottom = size.height - 18.dp.toPx()
            val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

            data.forEachIndexed { index, item ->
                val left = index * groupWidth
                if (item.highlight) {
                    drawRoundRect(
                        color = Color(0x14D28D38),
                        topLeft = Offset(left + groupWidth * 0.08f, 0f),
                        size = Size(groupWidth * 0.84f, chartBottom),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    )
                }

                if (dualSeries) {
                    val expenseHeight = ((animatedPrimary[index] / maxValue) * (chartBottom - 6.dp.toPx())).toFloat()
                    val incomeHeight = ((animatedSecondary[index] / maxValue) * (chartBottom - 6.dp.toPx())).toFloat()
                    val barWidth = groupWidth * 0.22f
                    val gap = groupWidth * 0.08f
                    val expenseLeft = left + groupWidth * 0.24f
                    val incomeLeft = expenseLeft + barWidth + gap

                    drawRoundRect(
                        color = TrendExpenseTone,
                        topLeft = Offset(expenseLeft, chartBottom - expenseHeight),
                        size = Size(barWidth, expenseHeight.coerceAtLeast(2f)),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = TrendIncomeTone,
                        topLeft = Offset(incomeLeft, chartBottom - incomeHeight),
                        size = Size(barWidth, incomeHeight.coerceAtLeast(2f)),
                        cornerRadius = cornerRadius,
                    )
                } else {
                    val barHeight = ((animatedPrimary[index] / maxValue) * (chartBottom - 6.dp.toPx())).toFloat()
                    val barWidth = groupWidth * 0.42f
                    drawRoundRect(
                        color = item.color,
                        topLeft = Offset(left + (groupWidth - barWidth) / 2f, chartBottom - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                        cornerRadius = cornerRadius,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEach { item ->
                Text(
                    text = item.axisLabel,
                    modifier = Modifier.widthIn(min = 18.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.highlight) InkDeep else InkSoft,
                    fontWeight = if (item.highlight) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun WatchListItem(rank: Int, entry: LedgerEntry) {
    Surface(
        color = Color(0xFFFFFAF3),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(99.dp),
                color = Color(0xFFF2E2C8),
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        color = InkDeep,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${entry.category}  ¥${"%.2f".format(entry.amount.absoluteValue)}",
                    color = InkDeep,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${entry.date}${entry.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LedgerCompactHighlight(entry: LedgerEntry) {
    Surface(
        color = Color(0xFFFFF8EF),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "${entry.date}  ${entry.category}  ¥${"%.2f".format(entry.amount)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = InkDeep,
        )
    }
}

@Composable
private fun LedgerItem(
    entry: LedgerEntry,
    highlighted: Boolean,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFFFFF5E7) else Color.White,
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (highlighted) Color(0xFFE0B77A) else LineSoft,
                    if (highlighted) Color(0xFFF1D8AF) else LineSoft.copy(alpha = 0.8f),
                ),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = 760f,
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.category,
                    color = InkDeep,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "¥${"%.2f".format(entry.amount)}",
                    color = if (entry.entryType == "expense") AccentVermilion else Color(0xFF2F6B3C),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (highlighted) Color(0xFFF8E6C8) else Color(0xFFF4E7D2),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = if (entry.entryType == "expense") "支出" else "收入",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = InkSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Surface(
                        color = Color(0xFFFFF8EF),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = entry.date,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = InkSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row {
                    TextButton(
                        onClick = { onEdit(entry) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text("编辑")
                    }
                    TextButton(
                        onClick = { onDelete(entry) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tone: Color,
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = tone,
        )
    }
}

@Composable
private fun LedgerEditDialog(
    entry: LedgerEntry,
    onDismiss: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
) {
    var amountText by remember(entry.id) { mutableStateOf(entry.amount.toString()) }
    var type by remember(entry.id) { mutableStateOf(entry.entryType) }
    var category by remember(entry.id) {
        mutableStateOf(LedgerCategoryCatalog.normalizeCategory(entry.category, entry.entryType))
    }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var date by remember(entry.id) { mutableStateOf(entry.date) }
    val categories = remember(type) { LedgerCategoryCatalog.categoriesFor(type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("金额") })
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("expense" to "支出", "income" to "收入").forEach { (value, label) ->
                        FilterChip(
                            selected = type == value,
                            onClick = {
                                type = value
                                category = LedgerCategoryCatalog.normalizeCategory(category, value)
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
                CategoryChipRows(
                    categories = categories,
                    selected = category,
                    onSelect = { category = it },
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("日期 YYYY-MM-DD") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull() ?: return@Button
                onConfirm(
                    entry.copy(
                        amount = amount,
                        category = category,
                        note = note,
                        date = date,
                        entryType = type,
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun CategoryChipRows(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { category ->
                    FilterChip(
                        selected = selected == category,
                        onClick = { onSelect(category) },
                        label = { Text(category) },
                    )
                }
            }
        }
    }
}

private enum class LedgerTypeFilter(val label: String) {
    ALL("全部"),
    EXPENSE("支出"),
    INCOME("收入"),
}

private data class SummaryMetricData(
    val label: String,
    val value: String,
    val tone: Color,
)

private data class SummaryCardData(
    val title: String,
    val headline: String,
    val headlineTone: Color,
    val compareBadge: String,
    val compareText: String,
    val compareTone: Color,
    val compareTextTone: Color,
    val metricRows: List<List<SummaryMetricData>>,
)

private data class LedgerStats(
    val income: Double,
    val expense: Double,
    val net: Double,
    val recordCount: Int,
    val activeDays: Int,
)

private data class InsightCardData(
    val title: String,
    val value: String,
    val detail: String,
    val tone: Color,
)

private data class CategoryBreakdownItem(
    val label: String,
    val total: Double,
    val count: Int,
    val ratio: Double,
    val color: Color,
    val labelHint: String,
)

private data class CategoryBreakdown(
    val items: List<CategoryBreakdownItem>,
)

private data class TrendPoint(
    val label: String,
    val axisLabel: String,
    val primaryValue: Double,
    val secondaryValue: Double = 0.0,
    val color: Color,
    val highlight: Boolean = false,
)

private val MorandiChartPalette = listOf(
    Color(0xFFB58A86),
    Color(0xFF8AA0AF),
    Color(0xFF95A68E),
    Color(0xFFC4A596),
    Color(0xFFC5B18A),
    Color(0xFF8DA8A2),
)

private val TrendExpenseTone = Color(0xFFB58A86)
private val TrendIncomeTone = Color(0xFF95A68E)

private fun buildSummaryData(
    period: LedgerPeriod,
    periodLabel: String,
    typeFilter: LedgerTypeFilter,
    currentEntries: List<LedgerEntry>,
    previousEntries: List<LedgerEntry>,
    filteredEntries: List<LedgerEntry>,
    previousFilteredEntries: List<LedgerEntry>,
): SummaryCardData {
    val currentStats = buildStats(filteredEntries)
    val previousStats = buildStats(previousFilteredEntries)
    val comparisonLabel = period.comparisonLabel()

    return if (typeFilter == LedgerTypeFilter.ALL) {
        val delta = currentStats.expense - previousStats.expense
        val compareTone = compareTone(delta, prefersLower = true)
        SummaryCardData(
            title = "${periodLabel}结余",
            headline = "¥${"%.2f".format(currentStats.net)}",
            headlineTone = if (currentStats.net >= 0) Color(0xFF2F6B3C) else AccentVermilion,
            compareBadge = comparisonBadge(delta, prefersLower = true),
            compareText = buildComparisonText(
                label = comparisonLabel,
                delta = delta,
                previousValue = previousStats.expense,
                increaseVerb = "多支出",
                decreaseVerb = "少支出",
            ),
            compareTone = compareTone,
            compareTextTone = compareTone,
            metricRows = listOf(
                listOf(
                    SummaryMetricData("支出", "¥${"%.2f".format(currentStats.expense)}", AccentVermilion),
                    SummaryMetricData("收入", "¥${"%.2f".format(currentStats.income)}", Color(0xFF2F6B3C)),
                ),
                listOf(
                    SummaryMetricData("结余", "¥${"%.2f".format(currentStats.net)}", InkDeep),
                    SummaryMetricData("记录数", currentStats.recordCount.toString(), InkDeep),
                ),
            ),
        )
    } else {
        val currentTotal = filteredEntries.sumOf { it.amount.absoluteValue }
        val previousTotal = previousFilteredEntries.sumOf { it.amount.absoluteValue }
        val delta = currentTotal - previousTotal
        val isExpense = typeFilter == LedgerTypeFilter.EXPENSE
        val compareTone = compareTone(delta, prefersLower = isExpense)
        val metrics = listOf(
            SummaryMetricData("记录数", currentStats.recordCount.toString(), InkDeep),
            SummaryMetricData("活跃天数", currentStats.activeDays.toString(), InkDeep),
            SummaryMetricData(
                "活跃日均",
                "¥${"%.2f".format(currentTotal / currentStats.activeDays.coerceAtLeast(1))}",
                compareTone,
            ),
        )
        SummaryCardData(
            title = "${periodLabel}${typeFilter.subjectLabel()}总额",
            headline = "¥${"%.2f".format(currentTotal)}",
            headlineTone = if (isExpense) AccentVermilion else Color(0xFF2F6B3C),
            compareBadge = comparisonBadge(delta, prefersLower = isExpense),
            compareText = buildComparisonText(
                label = comparisonLabel,
                delta = delta,
                previousValue = previousTotal,
                increaseVerb = if (isExpense) "多支出" else "多收入",
                decreaseVerb = if (isExpense) "少支出" else "少收入",
            ),
            compareTone = compareTone,
            compareTextTone = compareTone,
            metricRows = listOf(metrics),
        )
    }
}

private fun buildStats(entries: List<LedgerEntry>): LedgerStats {
    val income = entries.filter { it.entryType == "income" }.sumOf { it.amount.absoluteValue }
    val expense = entries.filter { it.entryType == "expense" }.sumOf { it.amount.absoluteValue }
    return LedgerStats(
        income = income,
        expense = expense,
        net = income - expense,
        recordCount = entries.size,
        activeDays = entries.map { it.date }.distinct().size,
    )
}

private fun buildComparisonText(
    label: String,
    delta: Double,
    previousValue: Double,
    increaseVerb: String,
    decreaseVerb: String,
): String {
    if (delta == 0.0) {
        return "$label 基本持平。"
    }
    val percent = if (previousValue > 0.0) {
        "（${"%.0f".format((delta.absoluteValue / previousValue) * 100)}%）"
    } else {
        ""
    }
    val verb = if (delta > 0) increaseVerb else decreaseVerb
    return "$label $verb ¥${"%.2f".format(delta.absoluteValue)}$percent。"
}

private fun comparisonBadge(delta: Double, prefersLower: Boolean): String {
    if (delta == 0.0) return "持平"
    return if ((delta < 0 && prefersLower) || (delta > 0 && !prefersLower)) {
        "表现更好"
    } else {
        "需要留意"
    }
}

private fun compareTone(delta: Double, prefersLower: Boolean): Color {
    if (delta == 0.0) return InkSoft
    return if ((delta < 0 && prefersLower) || (delta > 0 && !prefersLower)) {
        Color(0xFF2F6B3C)
    } else {
        AccentVermilion
    }
}

private fun buildInsightCards(
    entries: List<LedgerEntry>,
    typeFilter: LedgerTypeFilter,
): List<InsightCardData> {
    if (entries.isEmpty()) return emptyList()

    val total = entries.sumOf { it.amount.absoluteValue }
    val largest = entries.maxByOrNull { it.amount.absoluteValue }
    val champion = entries
        .groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.amount.absoluteValue } }
        .maxByOrNull { it.value }
    val activeDays = entries.map { it.date }.distinct().size

    val largestTitle = if (typeFilter == LedgerTypeFilter.INCOME) "最大收入" else "最大支出"
    val championTitle = if (typeFilter == LedgerTypeFilter.INCOME) "收入冠军分类" else "支出冠军分类"

    return buildList {
        largest?.let {
            add(
                InsightCardData(
                    title = largestTitle,
                    value = "${it.category} · ¥${"%.2f".format(it.amount.absoluteValue)}",
                    detail = "${it.date}${it.note.takeIf(String::isNotBlank)?.let { note -> " · $note" }.orEmpty()}",
                    tone = if (typeFilter == LedgerTypeFilter.INCOME) Color(0xFF2F6B3C) else AccentVermilion,
                )
            )
        }
        champion?.let {
            add(
                InsightCardData(
                    title = championTitle,
                    value = "${it.key} · ${"%.0f".format((it.value / total.coerceAtLeast(1.0)) * 100)}%",
                    detail = "本期累计 ¥${"%.2f".format(it.value)}",
                    tone = InkDeep,
                )
            )
        }
        add(
            InsightCardData(
                title = "消费活跃度",
                value = "$activeDays 天",
                detail = "平均每活跃日 ${typeFilter.subjectLabel()} ¥${"%.2f".format(total / activeDays.coerceAtLeast(1))}",
                tone = Color(0xFF8A6D4B),
            )
        )
    }
}

private fun buildCategoryBreakdown(entries: List<LedgerEntry>): CategoryBreakdown {
    if (entries.isEmpty()) {
        return CategoryBreakdown(emptyList())
    }

    val total = entries.sumOf { it.amount.absoluteValue }.coerceAtLeast(1.0)
    val grouped = entries
        .groupBy { it.category }
        .map { (category, values) ->
            RawCategoryItem(
                label = category,
                total = values.sumOf { it.amount.absoluteValue },
                count = values.size,
            )
        }
        .sortedByDescending { it.total }

    val trimmed = if (grouped.size <= 5) {
        grouped
    } else {
        val top = grouped.take(5)
        val rest = grouped.drop(5)
        top + RawCategoryItem(
            label = "其他",
            total = rest.sumOf { it.total },
            count = rest.sumOf { it.count },
        )
    }

    return CategoryBreakdown(
        items = trimmed.mapIndexed { index, item ->
            CategoryBreakdownItem(
                label = item.label,
                total = item.total,
                count = item.count,
                ratio = item.total / total,
                color = MorandiChartPalette[index % MorandiChartPalette.size],
                labelHint = if (item.label == "其他") "其余分类合并" else "Top ${index + 1}",
            )
        }
    )
}

private fun buildTrendData(
    entries: List<LedgerEntry>,
    period: LedgerPeriod,
    typeFilter: LedgerTypeFilter,
    anchorDate: LocalDate,
): List<TrendPoint> {
    val today = LocalDate.now()
    val normalizedAnchor = normalizeAnchor(period, anchorDate)
    return when (period) {
        LedgerPeriod.DAY -> {
            (6 downTo 0).map { offset ->
                val day = normalizedAnchor.minusDays(offset.toLong())
                val dayEntries = entries.filter { it.date == day.toString() }
                buildTrendPoint(
                    label = day.dayOfMonth.toString(),
                    axisLabel = if (day == today) "今天" else "${day.monthValue}/${day.dayOfMonth}",
                    entries = dayEntries,
                    typeFilter = typeFilter,
                    highlight = day == normalizedAnchor,
                )
            }
        }

        LedgerPeriod.WEEK -> {
            val selectedWeekStart = normalizeAnchor(LedgerPeriod.WEEK, normalizedAnchor)
            val currentWeekStart = normalizeAnchor(LedgerPeriod.WEEK, today)
            (7 downTo 0).map { offset ->
                val weekStart = selectedWeekStart.minusWeeks(offset.toLong())
                val weekEnd = weekStart.plusDays(6)
                buildTrendPoint(
                    label = "${weekStart.monthValue}/${weekStart.dayOfMonth}",
                    axisLabel = if (weekStart == currentWeekStart) {
                        "本周"
                    } else {
                        "${weekStart.monthValue}/${weekStart.dayOfMonth}"
                    },
                    entries = entries.filter {
                        val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
                        date != null && date.isWithin(weekStart, weekEnd)
                    },
                    typeFilter = typeFilter,
                    highlight = weekStart == selectedWeekStart,
                )
            }
        }

        LedgerPeriod.MONTH -> {
            val month = YearMonth.from(normalizedAnchor)
            (1..month.lengthOfMonth()).map { dayOfMonth ->
                val date = month.atDay(dayOfMonth)
                buildTrendPoint(
                    label = dayOfMonth.toString(),
                    axisLabel = monthAxisLabel(dayOfMonth, month.lengthOfMonth()),
                    entries = entries.filter { it.date == date.toString() },
                    typeFilter = typeFilter,
                    highlight = date == today,
                )
            }
        }

        LedgerPeriod.YEAR -> {
            val selectedYear = normalizedAnchor.year
            (1..12).map { monthValue ->
                val month = YearMonth.of(selectedYear, monthValue)
                buildTrendPoint(
                    label = "$monthValue 月",
                    axisLabel = "${monthValue}月",
                    entries = entries.filter {
                        val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
                        date != null && YearMonth.from(date) == month
                    },
                    typeFilter = typeFilter,
                    highlight = selectedYear == today.year && monthValue == today.monthValue,
                )
            }
        }
    }
}

private fun buildTrendPoint(
    label: String,
    axisLabel: String,
    entries: List<LedgerEntry>,
    typeFilter: LedgerTypeFilter,
    highlight: Boolean,
): TrendPoint {
    return when (typeFilter) {
        LedgerTypeFilter.ALL -> TrendPoint(
            label = label,
            axisLabel = axisLabel,
            primaryValue = entries.filter { it.entryType == "expense" }.sumOf { it.amount.absoluteValue },
            secondaryValue = entries.filter { it.entryType == "income" }.sumOf { it.amount.absoluteValue },
            color = TrendExpenseTone,
            highlight = highlight,
        )

        LedgerTypeFilter.EXPENSE -> TrendPoint(
            label = label,
            axisLabel = axisLabel,
            primaryValue = entries.filter { it.entryType == "expense" }.sumOf { it.amount.absoluteValue },
            color = TrendExpenseTone,
            highlight = highlight,
        )

        LedgerTypeFilter.INCOME -> TrendPoint(
            label = label,
            axisLabel = axisLabel,
            primaryValue = entries.filter { it.entryType == "income" }.sumOf { it.amount.absoluteValue },
            color = TrendIncomeTone,
            highlight = highlight,
        )
    }
}

private fun monthAxisLabel(day: Int, monthLength: Int): String {
    return when {
        day == 1 -> "1"
        day == monthLength -> day.toString()
        day % 5 == 0 -> day.toString()
        else -> ""
    }
}

private fun currentAnchor(period: LedgerPeriod): LocalDate {
    return normalizeAnchor(period, LocalDate.now())
}

private fun previousAnchor(period: LedgerPeriod, anchorDate: LocalDate): LocalDate {
    return shiftAnchor(anchorDate, period, -1)
}

private fun normalizeAnchor(period: LedgerPeriod, rawDate: LocalDate): LocalDate {
    return when (period) {
        LedgerPeriod.DAY -> rawDate
        LedgerPeriod.WEEK -> rawDate.minusDays((rawDate.dayOfWeek.value - 1).toLong())
        LedgerPeriod.MONTH -> rawDate.withDayOfMonth(1)
        LedgerPeriod.YEAR -> rawDate.withDayOfYear(1)
    }
}

private fun shiftAnchor(anchorDate: LocalDate, period: LedgerPeriod, amount: Long): LocalDate {
    val normalizedAnchor = normalizeAnchor(period, anchorDate)
    return when (period) {
        LedgerPeriod.DAY -> normalizedAnchor.plusDays(amount)
        LedgerPeriod.WEEK -> normalizedAnchor.plusWeeks(amount)
        LedgerPeriod.MONTH -> normalizedAnchor.plusMonths(amount)
        LedgerPeriod.YEAR -> normalizedAnchor.plusYears(amount)
    }
}

private fun filterEntriesByPeriod(
    entries: List<LedgerEntry>,
    period: LedgerPeriod,
    anchor: LocalDate,
): List<LedgerEntry> {
    val normalizedAnchor = normalizeAnchor(period, anchor)
    val anchorMonth = YearMonth.from(normalizedAnchor)
    val (rangeStart, rangeEnd) = periodRange(period, normalizedAnchor)
    return entries
        .filter { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@filter false
            when (period) {
                LedgerPeriod.DAY -> date == normalizedAnchor
                LedgerPeriod.WEEK -> date.isWithin(rangeStart, rangeEnd)
                LedgerPeriod.MONTH -> YearMonth.from(date) == anchorMonth
                LedgerPeriod.YEAR -> date.year == normalizedAnchor.year
            }
        }
        .sortedWith(compareByDescending<LedgerEntry> { parseDateSafe(it.date) }.thenByDescending { it.createdAt })
}

private fun filterEntriesByType(
    entries: List<LedgerEntry>,
    typeFilter: LedgerTypeFilter,
): List<LedgerEntry> {
    return when (typeFilter) {
        LedgerTypeFilter.ALL -> entries
        LedgerTypeFilter.EXPENSE -> entries.filter { it.entryType == "expense" }
        LedgerTypeFilter.INCOME -> entries.filter { it.entryType == "income" }
    }
}

private fun periodRange(period: LedgerPeriod, anchorDate: LocalDate): Pair<LocalDate, LocalDate> {
    val normalizedAnchor = normalizeAnchor(period, anchorDate)
    return when (period) {
        LedgerPeriod.DAY -> normalizedAnchor to normalizedAnchor
        LedgerPeriod.WEEK -> normalizedAnchor to normalizedAnchor.plusDays(6)
        LedgerPeriod.MONTH -> {
            val month = YearMonth.from(normalizedAnchor)
            month.atDay(1) to month.atEndOfMonth()
        }
        LedgerPeriod.YEAR -> normalizedAnchor.withDayOfYear(1) to normalizedAnchor.withDayOfYear(normalizedAnchor.lengthOfYear())
    }
}

private fun periodRangeLabel(period: LedgerPeriod, anchorDate: LocalDate): String {
    val normalizedAnchor = normalizeAnchor(period, anchorDate)
    return when (period) {
        LedgerPeriod.DAY -> fullDateLabel(normalizedAnchor)
        LedgerPeriod.WEEK -> {
            val (start, end) = periodRange(period, normalizedAnchor)
            if (start.year == end.year) {
                "${start.year}年${shortDateLabel(start)}-${shortDateLabel(end)}"
            } else {
                "${fullDateLabel(start)}-${fullDateLabel(end)}"
            }
        }
        LedgerPeriod.MONTH -> {
            val month = YearMonth.from(normalizedAnchor)
            "${month.year}年${month.monthValue}月"
        }
        LedgerPeriod.YEAR -> "${normalizedAnchor.year}年"
    }
}

private fun periodSummaryLabel(period: LedgerPeriod, anchorDate: LocalDate): String {
    return when (period) {
        LedgerPeriod.DAY -> periodRangeLabel(period, anchorDate)
        LedgerPeriod.WEEK -> periodRangeLabel(period, anchorDate)
        LedgerPeriod.MONTH -> periodRangeLabel(period, anchorDate)
        LedgerPeriod.YEAR -> periodRangeLabel(period, anchorDate)
    }
}

private fun parsePeriodPickerInput(
    period: LedgerPeriod,
    dateText: String,
    yearText: String,
    monthText: String,
): LocalDate? {
    return runCatching {
        when (period) {
            LedgerPeriod.DAY -> LocalDate.parse(dateText.trim())
            LedgerPeriod.WEEK -> normalizeAnchor(period, LocalDate.parse(dateText.trim()))
            LedgerPeriod.MONTH -> LocalDate.of(yearText.trim().toInt(), monthText.trim().toInt(), 1)
            LedgerPeriod.YEAR -> LocalDate.of(yearText.trim().toInt(), 1, 1)
        }
    }.getOrNull()
}

private fun fullDateLabel(date: LocalDate): String {
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}

private fun shortDateLabel(date: LocalDate): String {
    return "${date.monthValue}月${date.dayOfMonth}日"
}

private fun LocalDate.isWithin(start: LocalDate, end: LocalDate): Boolean {
    return !isBefore(start) && !isAfter(end)
}

private fun LedgerPeriod.displayName(): String {
    return when (this) {
        LedgerPeriod.DAY -> "按日"
        LedgerPeriod.WEEK -> "按周"
        LedgerPeriod.MONTH -> "按月"
        LedgerPeriod.YEAR -> "按年"
    }
}

private fun LedgerPeriod.pickerTitle(): String {
    return when (this) {
        LedgerPeriod.DAY -> "日期"
        LedgerPeriod.WEEK -> "周"
        LedgerPeriod.MONTH -> "月份"
        LedgerPeriod.YEAR -> "年份"
    }
}

private fun LedgerPeriod.currentButtonLabel(): String {
    return when (this) {
        LedgerPeriod.DAY -> "今天"
        LedgerPeriod.WEEK -> "本周"
        LedgerPeriod.MONTH -> "本月"
        LedgerPeriod.YEAR -> "今年"
    }
}

private fun LedgerPeriod.previousButtonLabel(): String {
    return when (this) {
        LedgerPeriod.DAY -> "上一日"
        LedgerPeriod.WEEK -> "上一周"
        LedgerPeriod.MONTH -> "上一月"
        LedgerPeriod.YEAR -> "上一年"
    }
}

private fun LedgerPeriod.nextButtonLabel(): String {
    return when (this) {
        LedgerPeriod.DAY -> "下一日"
        LedgerPeriod.WEEK -> "下一周"
        LedgerPeriod.MONTH -> "下一月"
        LedgerPeriod.YEAR -> "下一年"
    }
}

private fun LedgerPeriod.comparisonLabel(): String {
    return when (this) {
        LedgerPeriod.DAY -> "较昨天"
        LedgerPeriod.WEEK -> "较上周"
        LedgerPeriod.MONTH -> "较上月"
        LedgerPeriod.YEAR -> "较去年"
    }
}

private fun LedgerPeriod.trendTitle(typeFilter: LedgerTypeFilter, anchorDate: LocalDate): String {
    val subject = when (typeFilter) {
        LedgerTypeFilter.ALL -> "收支"
        LedgerTypeFilter.EXPENSE -> "支出"
        LedgerTypeFilter.INCOME -> "收入"
    }
    val normalizedAnchor = normalizeAnchor(this, anchorDate)
    return when (this) {
        LedgerPeriod.DAY -> "截至 ${shortDateLabel(normalizedAnchor)} 最近 7 天${subject}趋势"
        LedgerPeriod.WEEK -> "截至 ${shortDateLabel(normalizedAnchor)} 最近 8 周${subject}趋势"
        LedgerPeriod.MONTH -> {
            val month = YearMonth.from(normalizedAnchor)
            "${month.year}年${month.monthValue}月每日${subject}趋势"
        }
        LedgerPeriod.YEAR -> "${normalizedAnchor.year}年每月${subject}趋势"
    }
}

private fun LedgerTypeFilter.subjectLabel(): String {
    return when (this) {
        LedgerTypeFilter.ALL -> "支出"
        LedgerTypeFilter.EXPENSE -> "支出"
        LedgerTypeFilter.INCOME -> "收入"
    }
}

private fun LedgerTypeFilter.focusLabel(): String {
    return when (this) {
        LedgerTypeFilter.ALL -> "支出"
        LedgerTypeFilter.EXPENSE -> "支出"
        LedgerTypeFilter.INCOME -> "收入"
    }
}

private fun LedgerTypeFilter.watchListHint(): String {
    return when (this) {
        LedgerTypeFilter.ALL -> "默认按支出金额排序，帮你优先看到本期最需要留意的花销。"
        LedgerTypeFilter.EXPENSE -> "按金额从高到低排列，方便快速定位本期大额支出。"
        LedgerTypeFilter.INCOME -> "按金额从高到低排列，方便快速定位本期大额收入。"
    }
}

private data class RawCategoryItem(
    val label: String,
    val total: Double,
    val count: Int,
)

private fun parseDateSafe(dateString: String): LocalDate {
    return runCatching { LocalDate.parse(dateString) }.getOrElse { LocalDate.MIN }
}
