package com.example.myapplication.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.agent.data.LocalStore
import com.example.myapplication.agent.model.ActionReceipt
import com.example.myapplication.agent.model.ActionReceiptKind
import com.example.myapplication.agent.model.AgentAction
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessageKind
import com.example.myapplication.agent.model.ChatMessagePayload
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LedgerPeriod
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.ReceiptActionTarget
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.net.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AgentUiState(
    val input: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = LocalStore(application.applicationContext)
    private val repository = AgentRepository()
    private val legacyWelcomeMessage = "你好，我是 TimePaper Agent。你可以和我聊天，也可以让我记账和安排日程。"
    private val currentWelcomeMessage = "你好，我是 MyLife Agent。你可以和我聊天，也可以让我记账和安排日程。"

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _connectionTestResult = MutableStateFlow(ConnectionTestResult())
    val connectionTestResult: StateFlow<ConnectionTestResult> = _connectionTestResult.asStateFlow()

    private val _agentStatus = MutableStateFlow(
        ConnectionTestResult(
            status = ConnectionTestStatus.TESTING,
            title = "正在检查 Agent 状态",
            detail = "会先检查后端连通性，再确认当前模型是否可用。",
        )
    )
    val agentStatus: StateFlow<ConnectionTestResult> = _agentStatus.asStateFlow()

    val ledgerEntries: StateFlow<List<LedgerEntry>> = localStore.observeLedgerEntries().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val scheduleItems: StateFlow<List<ScheduleItem>> = localStore.observeScheduleItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val chatMessages: StateFlow<List<ChatMessage>> = localStore.observeChatMessages().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val modelSettings: StateFlow<ModelSettings> = localStore.observeModelSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ModelSettings(),
    )

    init {
        viewModelScope.launch {
            localStore.migrateLegacyModelSettings()
            localStore.migrateLegacyStructuredData()
            localStore.migrateLedgerCategoriesToPreset()
            localStore.migrateLegacyWelcomeMessage(
                oldContent = legacyWelcomeMessage,
                newContent = currentWelcomeMessage,
            )
            localStore.ensureWelcomeMessage(
                ChatMessage(
                    role = "assistant",
                    content = currentWelcomeMessage,
                )
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun seedInput(text: String) {
        updateInput(text)
    }

    fun submitInput() {
        val content = _uiState.value.input.trim()
        if (content.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请输入你想让我处理的内容")
            return
        }

        val requestHistory = chatMessages.value.toPayload()
        val userMessage = ChatMessage(role = "user", content = content)

        viewModelScope.launch {
            localStore.appendChatMessage(userMessage)
            _uiState.value = _uiState.value.copy(
                input = "",
                loading = true,
                error = null,
            )

            runCatching {
                repository.processNaturalLanguage(content, requestHistory, modelSettings.value)
            }.onSuccess { response ->
                val receiptMessage = if (response.actions.isNotEmpty()) {
                    val batchId = UUID.randomUUID().toString()
                    val createdAt = System.currentTimeMillis()
                    val batchResult = applyActions(response.actions, batchId, createdAt)
                    buildReceiptMessage(batchId, createdAt, batchResult)
                } else {
                    null
                }

                val messagesToAppend = buildList {
                    if (response.reply.isNotBlank()) {
                        add(ChatMessage(role = "assistant", content = response.reply))
                    }
                    receiptMessage?.let(::add)
                }
                localStore.appendChatMessages(messagesToAppend)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = null,
                )
            }.onFailure { throwable ->
                val friendlyError = throwable.toReadableMessage()
                localStore.appendChatMessage(
                    ChatMessage(
                        role = "assistant",
                        content = "当前连接暂时不可用，请检查后端地址或设置页里的模型配置后再试。",
                    )
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = friendlyError,
                )
            }
        }
    }

    fun updateLedger(entry: LedgerEntry) {
        viewModelScope.launch {
            localStore.updateLedger(entry)
        }
    }

    fun deleteLedger(entryId: String) {
        viewModelScope.launch {
            localStore.deleteLedger(entryId)
        }
    }

    fun updateSchedule(item: ScheduleItem) {
        viewModelScope.launch {
            localStore.updateSchedule(item)
        }
    }

    fun deleteSchedule(itemId: String) {
        viewModelScope.launch {
            localStore.deleteSchedule(itemId)
        }
    }

    fun undoActionBatch(batchId: String) {
        viewModelScope.launch {
            localStore.deleteEntriesForActionBatch(batchId)
            localStore.markReceiptUndone(batchId)
        }
    }

    fun deleteChatMessageAt(index: Int) {
        viewModelScope.launch {
            localStore.deleteChatMessageAt(index)
        }
    }

    fun saveModelSettings(settings: ModelSettings) {
        viewModelScope.launch {
            localStore.saveModelSettings(settings)
            refreshAgentStatus(settings)
        }
    }

    fun testModelConnection(candidateSettings: ModelSettings) {
        viewModelScope.launch {
            _connectionTestResult.value = ConnectionTestResult(
                status = ConnectionTestStatus.TESTING,
                title = "正在测试连接",
                detail = "先检查后端连通性，再验证当前模型是否可用。",
            )
            _connectionTestResult.value = repository.testConnection(candidateSettings)
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = ConnectionTestResult()
    }

    fun refreshAgentStatus() {
        viewModelScope.launch {
            refreshAgentStatus(modelSettings.value)
        }
    }

    private suspend fun refreshAgentStatus(settings: ModelSettings) {
        _agentStatus.value = ConnectionTestResult(
            status = ConnectionTestStatus.TESTING,
            title = "正在检查 Agent 状态",
            detail = "会先检查后端连通性，再确认当前模型是否可用。",
        )
        _agentStatus.value = repository.testConnection(settings)
    }

    private suspend fun applyActions(
        actions: List<AgentAction>,
        batchId: String,
        createdAt: Long,
    ): BatchResult {
        val schedules = mutableListOf<ScheduleItem>()
        val ledgers = mutableListOf<LedgerEntry>()

        actions.forEach { action ->
            when (action.type) {
                "add_schedule" -> {
                    val item = ScheduleItem(
                        title = action.payload.string("title", "未命名日程"),
                        date = action.payload.string("date", LocalDate.now().toString()),
                        time = action.payload.string("time", "09:00"),
                        note = action.payload.string("note", ""),
                        createdAt = createdAt,
                        actionBatchId = batchId,
                    )
                    localStore.appendSchedule(item)
                    schedules += item
                }

                "add_ledger" -> {
                    val entry = LedgerEntry(
                        amount = action.payload.double("amount", 0.0),
                        category = action.payload.string("category", "其他"),
                        note = action.payload.string("note", ""),
                        date = action.payload.string("date", LocalDate.now().toString()),
                        entryType = action.payload.string("entryType", "expense"),
                        createdAt = createdAt,
                        actionBatchId = batchId,
                    )
                    localStore.appendLedger(entry)
                    ledgers += entry
                }
            }
        }

        return BatchResult(
            schedules = schedules,
            ledgers = ledgers,
        )
    }

    private fun buildReceiptMessage(
        batchId: String,
        createdAt: Long,
        batchResult: BatchResult,
    ): ChatMessage? {
        if (batchResult.schedules.isEmpty() && batchResult.ledgers.isEmpty()) {
            return null
        }

        val receipt = when {
            batchResult.schedules.isNotEmpty() && batchResult.ledgers.isNotEmpty() -> {
                ActionReceipt(
                    batchId = batchId,
                    kind = ActionReceiptKind.MIXED,
                    summary = "已新增 ${batchResult.schedules.size} 条日程，并记录 ${batchResult.ledgers.size} 笔账单。",
                    primaryAction = ReceiptActionTarget.SCHEDULE,
                    targetDate = batchResult.schedules.sortedWith(scheduleComparator()).firstOrNull()?.date,
                    secondaryAction = ReceiptActionTarget.LEDGER,
                    secondaryPeriod = LedgerPeriod.MONTH,
                    scheduleCount = batchResult.schedules.size,
                    ledgerCount = batchResult.ledgers.size,
                )
            }

            batchResult.schedules.isNotEmpty() -> {
                val firstItem = batchResult.schedules.sortedWith(scheduleComparator()).first()
                val summary = if (batchResult.schedules.size == 1) {
                    "已添加日程：${firstItem.date} ${firstItem.time} ${firstItem.title}"
                } else {
                    "已添加 ${batchResult.schedules.size} 条日程，首条安排在 ${firstItem.date} ${firstItem.time}。"
                }
                ActionReceipt(
                    batchId = batchId,
                    kind = ActionReceiptKind.SCHEDULE,
                    summary = summary,
                    primaryAction = ReceiptActionTarget.SCHEDULE,
                    targetDate = firstItem.date,
                    scheduleCount = batchResult.schedules.size,
                )
            }

            else -> {
                val firstEntry = batchResult.ledgers.first()
                val summary = if (batchResult.ledgers.size == 1) {
                    "已记录账单：${firstEntry.category} ¥${"%.2f".format(firstEntry.amount)}"
                } else {
                    "已记录 ${batchResult.ledgers.size} 笔账单，可到本月统计页查看。"
                }
                ActionReceipt(
                    batchId = batchId,
                    kind = ActionReceiptKind.LEDGER,
                    summary = summary,
                    primaryAction = ReceiptActionTarget.LEDGER,
                    period = LedgerPeriod.MONTH,
                    ledgerCount = batchResult.ledgers.size,
                )
            }
        }

        return ChatMessage(
            role = "assistant",
            content = "",
            createdAt = createdAt,
            kind = ChatMessageKind.ACTION_RECEIPT,
            actionReceipt = receipt,
        )
    }
}

private data class BatchResult(
    val schedules: List<ScheduleItem>,
    val ledgers: List<LedgerEntry>,
)

private fun Map<String, Any?>.string(key: String, fallback: String): String {
    val value = this[key] ?: return fallback
    return value.toString()
}

private fun Map<String, Any?>.double(key: String, fallback: Double): Double {
    val value = this[key] ?: return fallback
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: fallback
        else -> fallback
    }
}

private fun List<ChatMessage>.toPayload(): List<ChatMessagePayload> {
    return asSequence()
        .filter { it.kind == ChatMessageKind.MESSAGE }
        .filter { it.content.isNotBlank() }
        .map { ChatMessagePayload(role = it.role, content = it.content) }
        .toList()
}

private fun Throwable.toReadableMessage(): String {
    val raw = message.orEmpty()
    val lower = raw.lowercase()
    return when {
        lower.contains("failed to connect")
            || lower.contains("unable to resolve host")
            || lower.contains("timeout")
            || lower.contains("connection") -> "后端当前不可达，请检查地址或网络后重试。"

        else -> raw.ifBlank { "请求失败，请稍后重试。" }
    }
}

private fun scheduleComparator(): Comparator<ScheduleItem> {
    return compareBy<ScheduleItem>({ it.date }, { it.time }, { it.createdAt })
}
