package com.example.myapplication.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ChatMessage
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AgentHomeScreen(
    viewModel: AppViewModel,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var selectedMessage by remember { mutableStateOf<Pair<Int, ChatMessage>?>(null) }

    LaunchedEffect(messages.size, uiState.loading) {
        val totalItems = messages.size + if (uiState.loading) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    EditorialBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EditorialReveal(delayMillis = 0) {
                EditorialPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "MyLife",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkDeep
                            )
                            TonePill(text = "在线", tone = AccentMoss)
                        }

                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(onClick = onOpenSettings),
                            color = Color(0xFFE7D8C1),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "设置",
                                    tint = InkDeep
                                )
                            }
                        }
                    }
                }
            }

            EditorialReveal(
                modifier = Modifier.weight(1f),
                delayMillis = 80
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(messages) { index, message ->
                        if (shouldShowDateHeader(messages, index)) {
                            DateHeader(dateLabel(message.createdAt))
                        }
                        ChatBubble(
                            message = message,
                            onLongPress = { selectedMessage = index to message }
                        )
                    }

                    item {
                        AnimatedVisibility(
                            visible = uiState.loading,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ThinkingIndicator()
                                Text("正在思考中...", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            EditorialReveal(delayMillis = 160) {
                EditorialPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .imePadding()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = uiState.input,
                                onValueChange = viewModel::updateInput,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp, max = 160.dp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = 700f
                                        )
                                    ),
                                minLines = 1,
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { viewModel.submitInput() })
                            )

                            FilledIconButton(
                                modifier = Modifier.padding(bottom = 4.dp),
                                onClick = viewModel::submitInput,
                                enabled = !uiState.loading && uiState.input.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AccentVermilion,
                                    contentColor = CanvasIvory
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                            }
                        }

                        AnimatedVisibility(
                            visible = !uiState.error.isNullOrBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
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
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onLongPress: () -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = 760f
                    )
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) InkDeep else Color(0xFFFFF9EE)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isUser) InkDeep else LineSoft
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 3.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .pointerInput(message.content) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) CanvasIvory else InkDeep,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = timeLabel(message.createdAt),
                    color = if (isUser) Color(0xFFE7DCC9) else InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DateHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF2E2C8)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
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
            RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val scale2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 520, delayMillis = 120, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val scale3 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 520, delayMillis = 240, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(scale1)
        Dot(scale2)
        Dot(scale3)
        Text(
            text = "处理中",
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall
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
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
    )
}
