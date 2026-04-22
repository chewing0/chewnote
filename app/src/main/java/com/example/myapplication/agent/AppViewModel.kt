package com.example.myapplication.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.agent.data.LocalStore
import com.example.myapplication.agent.model.AgentAction
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessagePayload
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.net.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AgentUiState(
    val input: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = LocalStore(application.applicationContext)
    private val repository = AgentRepository()
    private val legacyWelcomeMessage = "你好，我是 TimePaper Agent。你可以和我聊天，也可以让我记账和安排日程。"
    private val currentWelcomeMessage = "你好，我是 MyLife Agent。你可以和我聊天，也可以让我记账和安排日程。"

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    val ledgerEntries: StateFlow<List<LedgerEntry>> = localStore.observeLedgerEntries().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val scheduleItems: StateFlow<List<ScheduleItem>> = localStore.observeScheduleItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val chatMessages: StateFlow<List<ChatMessage>> = localStore.observeChatMessages().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val modelSettings: StateFlow<ModelSettings> = localStore.observeModelSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ModelSettings()
    )

    init {
        viewModelScope.launch {
            localStore.migrateLegacyModelSettings()
            localStore.migrateLegacyWelcomeMessage(
                oldContent = legacyWelcomeMessage,
                newContent = currentWelcomeMessage
            )
            localStore.ensureWelcomeMessage(
                ChatMessage(
                    role = "assistant",
                    content = currentWelcomeMessage
                )
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun submitInput() {
        val content = _uiState.value.input.trim()
        if (content.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请输入自然语言内容")
            return
        }

        val previousMessages = chatMessages.value
        val userMessage = ChatMessage(role = "user", content = content)
        val requestHistory = previousMessages.toPayload()

        viewModelScope.launch {
            localStore.appendChatMessage(userMessage)
            _uiState.value = _uiState.value.copy(
                input = "",
                loading = true,
                error = null
            )

            runCatching {
                repository.processNaturalLanguage(content, requestHistory, modelSettings.value)
            }.onSuccess { response ->
                response.actions.forEach { action ->
                    applyAction(action)
                }
                localStore.appendChatMessage(ChatMessage("assistant", response.reply))
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = null
                )
            }.onFailure { throwable ->
                val errorText = throwable.message ?: "调用 Agent 服务失败"
                localStore.appendChatMessage(
                    ChatMessage(
                        "assistant",
                        "服务暂时不可用，请稍后重试。"
                    )
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = errorText
                )
            }
        }
    }

    private suspend fun applyAction(action: AgentAction) {
        when (action.type) {
            "add_schedule" -> {
                localStore.appendSchedule(
                    ScheduleItem(
                        title = action.payload.string("title", "未命名日程"),
                        date = action.payload.string("date", LocalDate.now().toString()),
                        time = action.payload.string("time", "09:00"),
                        note = action.payload.string("note", "")
                    )
                )
            }

            "add_ledger" -> {
                localStore.appendLedger(
                    LedgerEntry(
                        amount = action.payload.double("amount", 0.0),
                        category = action.payload.string("category", "其他"),
                        note = action.payload.string("note", ""),
                        date = action.payload.string("date", LocalDate.now().toString()),
                        entryType = action.payload.string("entryType", "expense")
                    )
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

    fun deleteChatMessageAt(index: Int) {
        viewModelScope.launch {
            localStore.deleteChatMessageAt(index)
        }
    }

    fun saveModelSettings(settings: ModelSettings) {
        viewModelScope.launch {
            localStore.saveModelSettings(settings)
        }
    }
}

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
    return map { ChatMessagePayload(role = it.role, content = it.content) }
}
