package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.EditorialTitle
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: AppViewModel) {
    val schedules by viewModel.scheduleItems.collectAsState()
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var tab by remember { mutableStateOf(ScheduleTab.CALENDAR) }
    var allFilter by remember { mutableStateOf(AllScheduleFilter.UPCOMING) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val selectedDate = datePickerState.selectedDateMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    } ?: LocalDate.now()
    val today = LocalDate.now()

    val selectedSchedules = schedules
        .filter { it.date == selectedDate.toString() }
        .sortedBy { parseTimeSafe(it.time) }

    val filteredAllSchedules = schedules.filter {
        val date = parseDateSafe(it.date)
        when (allFilter) {
            AllScheduleFilter.UPCOMING -> date >= today
            AllScheduleFilter.ALL -> true
            AllScheduleFilter.HISTORY -> date < today
        }
    }

    val groupedAllSchedules = when (allFilter) {
        AllScheduleFilter.HISTORY -> filteredAllSchedules
            .sortedWith(compareByDescending<ScheduleItem> { parseDateTimeSafe(it) })
            .groupBy { parseDateSafe(it.date) }

        else -> filteredAllSchedules
            .sortedWith(compareBy<ScheduleItem> { parseDateTimeSafe(it) })
            .groupBy { parseDateSafe(it.date) }
    }

    EditorialBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditorialReveal(delayMillis = 0) {
                EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                    EditorialTitle(
                        title = "日程",
                        subtitle = "查看、筛选并维护你的时间安排",
                        modifier = Modifier.padding(14.dp),
                        trailing = {
                            TonePill(text = "${schedules.size} 项", tone = AccentVermilion)
                        }
                    )
                }
            }

            EditorialReveal(delayMillis = 80) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == ScheduleTab.CALENDAR,
                        onClick = { tab = ScheduleTab.CALENDAR },
                        label = { Text("日历视图") }
                    )
                    FilterChip(
                        selected = tab == ScheduleTab.ALL,
                        onClick = { tab = ScheduleTab.ALL },
                        label = { Text("全部日程") }
                    )
                }
            }

            EditorialReveal(delayMillis = 140) {
                if (tab == ScheduleTab.CALENDAR) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                item {
                    QuickDateActions(
                        selectedDate = selectedDate,
                        onSelectDate = { date ->
                            datePickerState.selectedDateMillis = date
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                    )
                }

                item {
                    Card {
                        DatePicker(
                            state = datePickerState,
                            modifier = Modifier.fillMaxWidth(),
                            showModeToggle = true
                        )
                    }
                }

                item {
                    SelectedDateSummary(
                        selectedDate = selectedDate,
                        count = selectedSchedules.size
                    )
                }

                if (selectedSchedules.isEmpty()) {
                    item {
                        ScheduleEmptyState(date = selectedDate)
                    }
                } else {
                    items(selectedSchedules) { item ->
                        ScheduleItemCard(
                            item = item,
                            onEdit = { editingItem = it },
                            onDelete = { viewModel.deleteSchedule(it.id) }
                        )
                    }
                }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "全部安排 (${filteredAllSchedules.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = allFilter == AllScheduleFilter.UPCOMING,
                        onClick = { allFilter = AllScheduleFilter.UPCOMING },
                        label = { Text("未来优先") }
                    )
                    FilterChip(
                        selected = allFilter == AllScheduleFilter.ALL,
                        onClick = { allFilter = AllScheduleFilter.ALL },
                        label = { Text("全部") }
                    )
                    FilterChip(
                        selected = allFilter == AllScheduleFilter.HISTORY,
                        onClick = { allFilter = AllScheduleFilter.HISTORY },
                        label = { Text("历史") }
                    )
                }

                if (groupedAllSchedules.isEmpty()) {
                    ScheduleEmptyState(date = selectedDate, title = "当前筛选下暂无日程")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        groupedAllSchedules.forEach { (day, itemsOfDay) ->
                            item {
                                DateSectionHeader(day)
                            }
                            items(itemsOfDay) { item ->
                                ScheduleAgendaCard(
                                    item = item,
                                    onEdit = { editingItem = it },
                                    onDelete = { viewModel.deleteSchedule(it.id) }
                                )
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        ScheduleEditDialog(
            item = item,
            onDismiss = { editingItem = null },
            onConfirm = {
                viewModel.updateSchedule(it)
                editingItem = null
            }
        )
    }
}

private enum class ScheduleTab {
    CALENDAR,
    ALL
}

private enum class AllScheduleFilter {
    UPCOMING,
    ALL,
    HISTORY
}

@Composable
private fun QuickDateActions(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val nextWeek = today.plusDays(7)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedDate == today,
            onClick = { onSelectDate(today) },
            label = { Text("今天") }
        )
        FilterChip(
            selected = selectedDate == tomorrow,
            onClick = { onSelectDate(tomorrow) },
            label = { Text("明天") }
        )
        FilterChip(
            selected = selectedDate == nextWeek,
            onClick = { onSelectDate(nextWeek) },
            label = { Text("7天后") }
        )
    }
}

@Composable
private fun SelectedDateSummary(
    selectedDate: LocalDate,
    count: Int
) {
    val dayOfWeek = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
    val dateText = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    Surface(
        color = Color(0xFFF4E8D6),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "$dateText  $dayOfWeek", style = MaterialTheme.typography.titleMedium, color = InkDeep)
            Text(text = "当日安排 $count 项", color = InkSoft)
        }
    }
}

@Composable
private fun ScheduleEmptyState(
    date: LocalDate,
    title: String = "当天暂无安排"
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${date.format(DateTimeFormatter.ofPattern("MM月dd日"))} 目前没有日程。可在交互页输入“明天 10 点开会”快速添加。",
                color = InkSoft
            )
        }
    }
}

@Composable
private fun DateSectionHeader(day: LocalDate) {
    val text = day.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    Surface(color = Color(0xFFF7EFE1), shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = InkSoft
        )
    }
}

@Composable
private fun ScheduleAgendaCard(
    item: ScheduleItem,
    onEdit: (ScheduleItem) -> Unit,
    onDelete: (ScheduleItem) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = AccentVermilion,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = item.time,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = InkDeep)
                Text(text = item.date, color = InkSoft)
                if (item.note.isNotBlank()) {
                    Text(text = item.note, color = InkSoft)
                }
                Spacer(modifier = Modifier)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onEdit(item) }) {
                        Text("编辑")
                    }
                    TextButton(onClick = { onDelete(item) }) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    onEdit: (ScheduleItem) -> Unit,
    onDelete: (ScheduleItem) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = InkDeep)
            Text(text = "时间: ${item.date} ${item.time}", color = InkSoft)
            if (item.note.isNotBlank()) {
                Text(text = item.note, color = InkSoft)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onEdit(item) }) {
                    Text("编辑")
                }
                TextButton(onClick = { onDelete(item) }) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditDialog(
    item: ScheduleItem,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleItem) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var date by remember(item.id) { mutableStateOf(item.date) }
    var time by remember(item.id) { mutableStateOf(item.time) }
    var note by remember(item.id) { mutableStateOf(item.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") })
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("日期 YYYY-MM-DD") })
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("时间 HH:mm") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(item.copy(title = title, date = date, time = time, note = note))
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

private fun parseDateSafe(dateString: String): LocalDate {
    return runCatching { LocalDate.parse(dateString) }.getOrElse { LocalDate.MAX }
}

private fun parseTimeSafe(timeString: String): LocalTime {
    return runCatching { LocalTime.parse(timeString) }.getOrElse { LocalTime.of(23, 59) }
}

private fun parseDateTimeSafe(item: ScheduleItem): LocalDateTime {
    return LocalDateTime.of(parseDateSafe(item.date), parseTimeSafe(item.time))
}
