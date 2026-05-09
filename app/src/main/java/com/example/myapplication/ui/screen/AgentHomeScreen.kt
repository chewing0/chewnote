package com.example.myapplication.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ActionReceipt
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessageKind
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.Conversation
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LedgerPeriod
import com.example.myapplication.agent.model.ReceiptActionTarget
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentMoss
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.CanvasIvory
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import com.example.myapplication.ui.theme.LineSoft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun AgentHomeScreen(
    viewModel: AppViewModel,
    onOpenSchedule: (targetDate: String?, batchId: String?) -> Unit,
    onOpenLedger: (period: LedgerPeriod, batchId: String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val agentStatus by viewModel.agentStatus.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val schedules by viewModel.scheduleItems.collectAsState()
    val ledgers by viewModel.ledgerEntries.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var selectedMessage by remember { mutableStateOf<Pair<Int, ChatMessage>?>(null) }
    var showConversationDialog by remember { mutableStateOf(false) }
    var renameConversation by remember { mutableStateOf<Conversation?>(null) }
    var deleteConversation by remember { mutableStateOf<Conversation?>(null) }
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val currentConversation = remember(conversations, currentConversationId) {
        conversations.firstOrNull { it.id == currentConversationId }
    }

    val today = LocalDate.now()
    val todaySchedules = remember(schedules, today) {
        schedules
            .filter { it.date == today.toString() }
            .sortedWith(compareBy<ScheduleItem> { it.time }.thenBy { it.createdAt })
    }
    val nextSchedule = remember(todaySchedules) {
        val now = LocalTime.now()
        todaySchedules.firstOrNull { parseTimeSafe(it.time) >= now } ?: todaySchedules.firstOrNull()
    }
    val monthSummary = remember(ledgers) { buildMonthSummary(ledgers) }

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val dockLiftTarget = with(density) {
        if (imeBottomPx > navBottomPx) {
            (imeBottomPx - navBottomPx).coerceAtLeast(0).toDp() + 4.dp
        } else {
            0.dp
        }
    }
    val dockLift by animateDpAsState(
        targetValue = dockLiftTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "chat-dock-lift",
    )
    val safeDockLift = if (dockLift < 0.dp) 0.dp else dockLift
    val listBottomPadding = with(density) { dockHeightPx.toDp() } + safeDockLift + 8.dp

    LaunchedEffect(Unit) {
        viewModel.refreshAgentStatus()
    }

    LaunchedEffect(messages.size, uiState.loading) {
        val totalItems = messages.size + if (uiState.loading) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    EditorialBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorialReveal(delayMillis = 0) {
                    EditorialPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = currentConversation?.title ?: "MyLife",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkDeep,
                                )
                                TonePill(
                                    text = agentStatus.bannerLabel(),
                                    tone = agentStatus.bannerTone(),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = viewModel::createConversation,
                                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("新对话")
                                }
                                FilledTonalButton(
                                    onClick = { showConversationDialog = true },
                                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("对话")
                                }
                            }
                        }
                    }
                }

                EditorialReveal(delayMillis = 60) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryBandCard(
                            modifier = Modifier.weight(1f),
                            title = "今日日程",
                            value = "${todaySchedules.size} 项",
                            detail = nextSchedule?.let { "下一项 ${it.time} ${it.title}" } ?: "今天还没有安排",
                        )
                        SummaryBandCard(
                            modifier = Modifier.weight(1f),
                            title = "本月账单",
                            value = "¥${"%.0f".format(monthSummary.net)}",
                            detail = "收入 ¥${"%.0f".format(monthSummary.income)} / 支出 ¥${"%.0f".format(monthSummary.expense)}",
                        )
                    }
                }

                EditorialReveal(
                    modifier = Modifier.weight(1f),
                    delayMillis = 100,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = listBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (messages.isEmpty() && !uiState.loading) {
                            item {
                                EmptyConversationState()
                            }
                        }
                        itemsIndexed(messages, key = { index, message -> message.id.ifBlank { "${message.createdAt}-${message.kind}-$index" } }) { index, message ->
                            if (shouldShowDateHeader(messages, index)) {
                                DateHeader(dateLabel(message.createdAt))
                            }
                            if (message.kind == ChatMessageKind.ACTION_RECEIPT) {
                                message.actionReceipt?.let { receipt ->
                                    ActionReceiptCard(
                                        receipt = receipt,
                                        onOpenPrimary = {
                                            handleReceiptAction(
                                                receipt = receipt,
                                                target = receipt.primaryAction,
                                                onOpenSchedule = onOpenSchedule,
                                                onOpenLedger = onOpenLedger,
                                            )
                                        },
                                        onOpenSecondary = {
                                            receipt.secondaryAction?.let { secondary ->
                                                handleReceiptAction(
                                                    receipt = receipt,
                                                    target = secondary,
                                                    onOpenSchedule = onOpenSchedule,
                                                    onOpenLedger = onOpenLedger,
                                                )
                                            }
                                        },
                                        onUndo = { viewModel.undoActionBatch(receipt.batchId) },
                                    )
                                }
                            } else {
                                ChatBubble(
                                    message = message,
                                    onLongPress = { selectedMessage = index to message },
                                )
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = uiState.loading,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ThinkingIndicator()
                                }
                            }
                        }
                    }
                }
            }

            EditorialReveal(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = safeDockLift)
                    .onSizeChanged { dockHeightPx = it.height },
                delayMillis = 160,
            ) {
                AgentInputDock(
                    input = uiState.input,
                    error = uiState.error,
                    loading = uiState.loading,
                    onValueChange = viewModel::updateInput,
                    onSend = viewModel::submitInput,
                )
            }
        }
    }

    selectedMessage?.let { (index, message) ->
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("消息操作") },
            text = { Text("选择你要执行的操作") },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("message", message.content))
                    selectedMessage = null
                }) {
                    Text("复制")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.deleteChatMessageAt(index)
                        selectedMessage = null
                    }) {
                        Text("删除")
                    }
                    TextButton(onClick = { selectedMessage = null }) {
                        Text("取消")
                    }
                }
            },
        )
    }

    if (showConversationDialog) {
        ConversationManagerDialog(
            conversations = conversations,
            currentConversationId = currentConversationId,
            onDismiss = { showConversationDialog = false },
            onNewConversation = {
                showConversationDialog = false
                viewModel.createConversation()
            },
            onSelect = {
                showConversationDialog = false
                viewModel.selectConversation(it.id)
            },
            onRename = { renameConversation = it },
            onDelete = { deleteConversation = it },
        )
    }

    renameConversation?.let { conversation ->
        RenameConversationDialog(
            conversation = conversation,
            onDismiss = { renameConversation = null },
            onConfirm = { title ->
                viewModel.renameConversation(conversation.id, title)
                renameConversation = null
            },
        )
    }

    deleteConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("删除对话") },
            text = { Text("确定删除“${conversation.title}”吗？这会同时删除后端保存的这条对话消息。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conversation.id)
                    deleteConversation = null
                    showConversationDialog = false
                }) {
                    Text("删除", color = AccentVermilion)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConversation = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ConversationManagerDialog(
    conversations: List<Conversation>,
    currentConversationId: String,
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onSelect: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对话管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onNewConversation, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Text("新建对话")
                }

                if (conversations.isEmpty()) {
                    Text(
                        text = "还没有对话。发送第一条消息后，后端会自动创建并保存对话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(conversations, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                selected = conversation.id == currentConversationId,
                                onSelect = { onSelect(conversation) },
                                onRename = { onRename(conversation) },
                                onDelete = { onDelete(conversation) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect),
        color = if (selected) AccentVermilion.copy(alpha = 0.1f) else Color(0xFFFFFBF4),
        border = BorderStroke(1.dp, if (selected) AccentVermilion.copy(alpha = 0.42f) else LineSoft.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkDeep,
                )
                Text(
                    text = "${conversation.messageCount} 条消息 · ${conversationTimeLabel(conversation)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "重命名",
                    tint = InkSoft,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = AccentVermilion,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun RenameConversationDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(conversation.id) { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("对话名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.trim().isNotBlank(),
            ) {
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
private fun EmptyConversationState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFF7EA),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AccentVermilion.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "开始一段新对话",
                style = MaterialTheme.typography.titleMedium,
                color = InkDeep,
            )
            Text(
                text = "发送消息后，对话和消息会保存到后端，并跟随当前登录账号同步到其他设备。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun AgentInputDock(
    input: String,
    error: String?,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 700f,
                )
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        EditorialPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactChatInput(
                        value = input,
                        onValueChange = onValueChange,
                        onSend = onSend,
                        modifier = Modifier.weight(1f),
                    )

                    FilledIconButton(
                        modifier = Modifier.size(38.dp),
                        onClick = onSend,
                        enabled = !loading && input.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentVermilion,
                            contentColor = CanvasIvory,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !error.isNullOrBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 46.dp),
        color = Color(0xFFFFFBF4),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AccentVermilion.copy(alpha = 0.42f)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = InkDeep),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            cursorBrush = SolidColor(InkDeep),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SummaryBandCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    detail: String,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFFF7EA),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, color = InkSoft, style = MaterialTheme.typography.bodySmall)
            Text(
                text = value,
                color = InkDeep,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(text = detail, color = InkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActionReceiptCard(
    receipt: ActionReceipt,
    onOpenPrimary: () -> Unit,
    onOpenSecondary: () -> Unit,
    onUndo: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F0E4)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(Color(0xFFE0C39A)),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonePill(
                    text = if (receipt.undone) "已撤销" else "本次新增",
                    tone = if (receipt.undone) InkSoft else AccentVermilion,
                )
                Text(
                    text = when (receipt.kind.name) {
                        "SCHEDULE" -> "日程回执"
                        "LEDGER" -> "账单回执"
                        else -> "同步回执"
                    },
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = receipt.summary,
                color = InkDeep,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onOpenPrimary,
                    enabled = !receipt.undone,
                ) {
                    Text(receipt.primaryAction.toActionLabel())
                }
                receipt.secondaryAction?.let {
                    FilledTonalButton(
                        onClick = onOpenSecondary,
                        enabled = !receipt.undone,
                    ) {
                        Text(it.toActionLabel())
                    }
                }
                TextButton(
                    onClick = onUndo,
                    enabled = !receipt.undone,
                ) {
                    Text("撤销本次")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onLongPress: () -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(8.dp))
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = 760f,
                    )
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFFC73A30) else Color(0xFFFFF9EE),
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isUser) AccentVermilion.copy(alpha = 0.24f) else LineSoft.copy(alpha = 0.52f)),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 3.dp else 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .pointerInput(message.content) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) CanvasIvory else InkDeep,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = timeLabel(message.createdAt),
                    color = if (isUser) Color(0xFFE7DCC9) else InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DateHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF2E2C8),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun shouldShowDateHeader(messages: List<ChatMessage>, index: Int): Boolean {
    if (index == 0) return true
    val currentDate = millisToDate(messages[index].createdAt)
    val previousDate = millisToDate(messages[index - 1].createdAt)
    return currentDate != previousDate
}

private fun millisToDate(millis: Long): LocalDate {
    val safe = if (millis > 0) millis else System.currentTimeMillis()
    return Instant.ofEpochMilli(safe)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun dateLabel(millis: Long): String {
    val date = millisToDate(millis)
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}

private fun conversationTimeLabel(conversation: Conversation): String {
    val timestamp = listOf(conversation.lastMessageAt, conversation.updatedAt, conversation.createdAt)
        .firstOrNull { it > 0L }
        ?: return "刚刚"
    return dateLabel(timestamp)
}

private fun timeLabel(millis: Long): String {
    val safe = if (millis > 0) millis else System.currentTimeMillis()
    return Instant.ofEpochMilli(safe)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val scale1 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 520, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val scale2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 520, delayMillis = 120, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val scale3 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 520, delayMillis = 240, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(scale1)
        Dot(scale2)
        Dot(scale3)
        Text(
            text = "处理中...",
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Dot(scale: Float) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
    )
}

private data class MonthLedgerSummary(
    val income: Double,
    val expense: Double,
    val net: Double,
)

private fun buildMonthSummary(entries: List<LedgerEntry>): MonthLedgerSummary {
    val currentMonth = YearMonth.now()
    val monthEntries = entries.filter {
        runCatching { YearMonth.from(LocalDate.parse(it.date)) }.getOrNull() == currentMonth
    }
    val income = monthEntries.filter { it.entryType == "income" }.sumOf { it.amount.absoluteValue }
    val expense = monthEntries.filter { it.entryType == "expense" }.sumOf { it.amount.absoluteValue }
    return MonthLedgerSummary(
        income = income,
        expense = expense,
        net = income - expense,
    )
}

private fun handleReceiptAction(
    receipt: ActionReceipt,
    target: ReceiptActionTarget,
    onOpenSchedule: (String?, String?) -> Unit,
    onOpenLedger: (LedgerPeriod, String?) -> Unit,
) {
    when (target) {
        ReceiptActionTarget.SCHEDULE -> {
            val date = if (receipt.primaryAction == ReceiptActionTarget.SCHEDULE) {
                receipt.targetDate
            } else {
                receipt.secondaryTargetDate
            }
            onOpenSchedule(date, receipt.batchId)
        }

        ReceiptActionTarget.LEDGER -> {
            val period = if (receipt.primaryAction == ReceiptActionTarget.LEDGER) {
                receipt.period ?: LedgerPeriod.MONTH
            } else {
                receipt.secondaryPeriod ?: LedgerPeriod.MONTH
            }
            onOpenLedger(period, receipt.batchId)
        }
    }
}

private fun ReceiptActionTarget.toActionLabel(): String {
    return when (this) {
        ReceiptActionTarget.SCHEDULE -> "查看日程"
        ReceiptActionTarget.LEDGER -> "查看账单"
    }
}

private fun ConnectionTestResult.bannerLabel(): String {
    return when (status) {
        ConnectionTestStatus.TESTING -> "检测中"
        ConnectionTestStatus.SUCCESS -> "在线"
        ConnectionTestStatus.FAILURE -> "配置异常"
        ConnectionTestStatus.IDLE -> "待检测"
    }
}

private fun ConnectionTestResult.bannerTone(): Color {
    return when (status) {
        ConnectionTestStatus.SUCCESS -> AccentMoss
        ConnectionTestStatus.FAILURE -> AccentVermilion
        ConnectionTestStatus.TESTING,
        ConnectionTestStatus.IDLE -> InkSoft
    }
}

private fun parseTimeSafe(time: String): LocalTime {
    return runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.MAX }
}
