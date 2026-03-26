package com.example.myapplication.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue

@Composable
fun LedgerScreen(viewModel: AppViewModel) {
    val entries by viewModel.ledgerEntries.collectAsState()
    var period by remember { mutableStateOf(StatsPeriod.DAY) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    val filteredEntries = remember(entries, period) { filterEntriesByPeriod(entries, period) }
    val expense = filteredEntries.filter { it.entryType == "expense" }.sumOf { it.amount.absoluteValue }
    val income = filteredEntries.filter { it.entryType == "income" }.sumOf { it.amount.absoluteValue }
    val net = income - expense
    val categoryData = remember(filteredEntries) { buildCategoryExpenseData(filteredEntries) }
    val trendData = remember(entries, period) { buildTrendData(entries, period) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "记账",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == StatsPeriod.DAY, onClick = { period = StatsPeriod.DAY }, label = { Text("按日") })
            FilterChip(selected = period == StatsPeriod.MONTH, onClick = { period = StatsPeriod.MONTH }, label = { Text("按月") })
            FilterChip(selected = period == StatsPeriod.YEAR, onClick = { period = StatsPeriod.YEAR }, label = { Text("按年") })
        }

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("支出: ¥${"%.2f".format(expense)}")
                Text("收入: ¥${"%.2f".format(income)}")
                Text("结余: ¥${"%.2f".format(net)}")
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("分类占比", style = MaterialTheme.typography.titleMedium)
                PieChart(categoryData = categoryData)
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("趋势统计", style = MaterialTheme.typography.titleMedium)
                BarChart(data = trendData)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredEntries) { entry ->
                LedgerItem(
                    entry = entry,
                    onEdit = { editingEntry = it },
                    onDelete = { viewModel.deleteLedger(it.id) }
                )
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
            }
        )
    }
}

@Composable
private fun LedgerItem(
    entry: LedgerEntry,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "${entry.category}  ¥${"%.2f".format(entry.amount)}")
            Text(text = entry.note, style = MaterialTheme.typography.bodyMedium)
            Text(text = "${entry.date}  ${if (entry.entryType == "expense") "支出" else "收入"}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onEdit(entry) }) {
                    Text("编辑")
                }
                TextButton(onClick = { onDelete(entry) }) {
                    Text("删除")
                }
            }
        }
    }
}

private enum class StatsPeriod {
    DAY,
    MONTH,
    YEAR
}

private data class ChartItem(
    val label: String,
    val value: Double,
    val color: Color = Color(0xFF6B7280)
)

private fun filterEntriesByPeriod(entries: List<LedgerEntry>, period: StatsPeriod): List<LedgerEntry> {
    val today = LocalDate.now()
    val thisMonth = YearMonth.now()
    val thisYear = today.year
    return entries.filter { entry ->
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@filter false
        when (period) {
            StatsPeriod.DAY -> date == today
            StatsPeriod.MONTH -> YearMonth.from(date) == thisMonth
            StatsPeriod.YEAR -> date.year == thisYear
        }
    }
}

private fun buildCategoryExpenseData(entries: List<LedgerEntry>): List<ChartItem> {
    val colors = listOf(
        Color(0xFFC16E46),
        Color(0xFF4E79A7),
        Color(0xFF59A14F),
        Color(0xFFE15759),
        Color(0xFFEDC949),
        Color(0xFF76B7B2),
    )
    val grouped = entries
        .filter { it.entryType == "expense" }
        .groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.amount.absoluteValue } }
        .toList()
        .sortedByDescending { it.second }

    if (grouped.isEmpty()) {
        return listOf(ChartItem("暂无支出", 1.0, Color(0xFFD0D5DD)))
    }

    return grouped.mapIndexed { index, pair ->
        ChartItem(pair.first, pair.second, colors[index % colors.size])
    }
}

private fun buildTrendData(entries: List<LedgerEntry>, period: StatsPeriod): List<ChartItem> {
    val today = LocalDate.now()
    return when (period) {
        StatsPeriod.DAY -> {
            (6 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong())
                val value = entries.filter { it.entryType == "expense" && it.date == day.toString() }
                    .sumOf { it.amount.absoluteValue }
                ChartItem(day.dayOfMonth.toString(), value, Color(0xFF4E79A7))
            }
        }

        StatsPeriod.MONTH -> {
            (5 downTo 0).map { offset ->
                val month = YearMonth.now().minusMonths(offset.toLong())
                val value = entries
                    .filter { it.entryType == "expense" }
                    .filter {
                        val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
                        d != null && YearMonth.from(d) == month
                    }
                    .sumOf { it.amount.absoluteValue }
                ChartItem("${month.monthValue}月", value, Color(0xFF59A14F))
            }
        }

        StatsPeriod.YEAR -> {
            val thisYear = today.year
            (4 downTo 0).map { offset ->
                val y = thisYear - offset
                val value = entries
                    .filter { it.entryType == "expense" }
                    .filter { runCatching { LocalDate.parse(it.date).year == y }.getOrDefault(false) }
                    .sumOf { it.amount.absoluteValue }
                ChartItem("$y", value, Color(0xFFC16E46))
            }
        }
    }
}

@Composable
private fun PieChart(categoryData: List<ChartItem>) {
    val total = categoryData.sumOf { it.value }.coerceAtLeast(1.0)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(modifier = Modifier.size(160.dp)) {
            var startAngle = -90f
            categoryData.forEach { item ->
                val sweep = ((item.value / total) * 360f).toFloat()
                drawArc(
                    color = item.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
                startAngle += sweep
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            categoryData.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(item.color, shape = MaterialTheme.shapes.small)
                    )
                    Text("${item.label}  ¥${"%.2f".format(item.value)}")
                }
            }
        }
    }
}

@Composable
private fun BarChart(data: List<ChartItem>) {
    val max = data.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)) {
            val barWidth = size.width / (data.size * 1.8f)
            data.forEachIndexed { index, item ->
                val x = index * (barWidth * 1.8f) + barWidth * 0.6f
                val barHeight = ((item.value / max) * (size.height - 16)).toFloat()
                drawRect(
                    color = item.color,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { item ->
                Text(item.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LedgerEditDialog(
    entry: LedgerEntry,
    onDismiss: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit
) {
    var amountText by remember(entry.id) { mutableStateOf(entry.amount.toString()) }
    var category by remember(entry.id) { mutableStateOf(entry.category) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var date by remember(entry.id) { mutableStateOf(entry.date) }
    var type by remember(entry.id) { mutableStateOf(entry.entryType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("金额") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("日期 YYYY-MM-DD") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("类型 expense/income") })
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
                        entryType = type
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
        }
    )
}
