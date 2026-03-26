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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ScheduleItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: AppViewModel) {
    val schedules by viewModel.scheduleItems.collectAsState()
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var tab by remember { mutableStateOf(ScheduleTab.CALENDAR) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val selectedDate = datePickerState.selectedDateMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    } ?: LocalDate.now()

    val selectedSchedules = schedules
        .filter { it.date == selectedDate.toString() }
        .sortedBy { parseTimeSafe(it.time) }

    val groupedAllSchedules = schedules
        .sortedWith(compareBy<ScheduleItem>({ parseDateSafe(it.date) }, { parseTimeSafe(it.time) }))
        .groupBy { parseDateSafe(it.date) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "日程",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif
        )

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

        if (tab == ScheduleTab.CALENDAR) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    Text(
                        text = "${selectedDate} 的安排 (${selectedSchedules.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(selectedSchedules) { item ->
                    ScheduleItemCard(
                        item = item,
                        onEdit = { editingItem = it },
                        onDelete = { viewModel.deleteSchedule(it.id) }
                    )
                }
            }
        } else {
            Text(
                text = "全部安排 (${schedules.size})",
                style = MaterialTheme.typography.titleMedium
            )

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

@Composable
private fun DateSectionHeader(day: LocalDate) {
    val text = day.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    Surface(color = Color(0xFFF7EFE1), shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color(0xFF5A5F66)
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
                color = Color(0xFF2E3944),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = item.time,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(text = item.date, color = Color(0xFF6B7280))
                if (item.note.isNotBlank()) {
                    Text(text = item.note)
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
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(text = "时间: ${item.date} ${item.time}")
            if (item.note.isNotBlank()) {
                Text(text = item.note)
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
