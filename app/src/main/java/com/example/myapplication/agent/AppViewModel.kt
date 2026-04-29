package com.example.myapplication.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.agent.data.LocalStore
import com.example.myapplication.agent.model.ActionReceipt
import com.example.myapplication.agent.model.ActionReceiptKind
import com.example.myapplication.agent.model.AgentAction
import com.example.myapplication.agent.model.AuthResponse
import com.example.myapplication.agent.model.AuthState
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessageKind
import com.example.myapplication.agent.model.ChatMessagePayload
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.ContextSnapshot
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
import retrofit2.HttpException
import java.time.LocalDate
import java.util.UUID

data class AgentUiState(
    val input: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

data class AccountUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String = "",
    val pendingSyncUserId: String = "",
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = LocalStore(application.applicationContext)
    private val repository = AgentRepository()
    private val legacyWelcomeMessage = "你好，我是 TimePaper Agent。你可以和我聊天，也可以让我记账和安排日程。"
    private val currentWelcomeMessage = "你好，我是 MyLife Agent。你可以和我聊天，也可以让我记账和安排日程。"

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _accountUiState = MutableStateFlow(AccountUiState())
    val accountUiState: StateFlow<AccountUiState> = _accountUiState.asStateFlow()

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

    private val contextSnapshot: StateFlow<ContextSnapshot> = localStore.observeContextSnapshot().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ContextSnapshot(),
    )

    val modelSettings: StateFlow<ModelSettings> = localStore.observeModelSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ModelSettings(),
    )

    val authState: StateFlow<AuthState> = localStore.observeAuthState().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AuthState(),
    )

    init {
        viewModelScope.launch {
            localStore.migrateLegacyModelSettings()
            localStore.migrateLegacyStructuredData()
            localStore.migrateLedgerCategoriesToPreset()
            localStore.migrateToBackendStorageCache()
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
            val currentAuth = localStore.getAuthState()
            if (currentAuth.isLoggedIn) {
                syncStoredSessionSafely()
            }
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
        if (!authState.value.isLoggedIn) {
            _uiState.value = _uiState.value.copy(error = "请先在“我的”里登录，再使用 Agent 记录或查询日程记账。")
            return
        }

        val requestContext = chatMessages.value.toContextRequest(contextSnapshot.value)
        val userMessage = ChatMessage(role = "user", content = content)

        viewModelScope.launch {
            localStore.appendChatMessage(userMessage)
            _uiState.value = _uiState.value.copy(
                input = "",
                loading = true,
                error = null,
            )

            runCatching {
                val sessionId = localStore.getOrCreateSessionId()
                authorizedCall { token ->
                    repository.processNaturalLanguage(
                        text = content,
                        sessionId = sessionId,
                        history = requestContext.history,
                        contextSummary = requestContext.contextSummary,
                        summaryHistory = requestContext.summaryHistory,
                        settings = modelSettings.value,
                        accessToken = token,
                    )
                }
            }.onSuccess { response ->
                response.contextSummary?.takeIf { requestContext.summaryHistory.isNotEmpty() }?.let { summary ->
                    localStore.saveContextSnapshot(
                        ContextSnapshot(
                            summary = summary,
                            summarizedMessageCount = requestContext.nextSummarizedMessageCount,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                if (response.changedDomains.any { it == "schedule" || it == "ledger" }) {
                    authorizedCall { token ->
                        syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                    }
                }

                val messagesToAppend = buildList {
                    if (response.reply.isNotBlank()) {
                        add(ChatMessage(role = "assistant", content = response.reply))
                    }
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
            runCatching {
                authorizedCall { token ->
                    repository.updateLedger(entry, modelSettings.value, token)
                    syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun deleteLedger(entryId: String) {
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    repository.deleteLedger(entryId, modelSettings.value, token)
                    syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun updateSchedule(item: ScheduleItem) {
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    repository.updateSchedule(item, modelSettings.value, token)
                    syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun deleteSchedule(itemId: String) {
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    repository.deleteSchedule(itemId, modelSettings.value, token)
                    syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
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
            _connectionTestResult.value = repository.testConnection(candidateSettings, authState.value.accessToken)
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
        _agentStatus.value = repository.testConnection(settings, authState.value.accessToken)
    }

    fun registerAccount(username: String, email: String, password: String) {
        viewModelScope.launch {
            _accountUiState.value = AccountUiState(loading = true)
            runCatching {
                val response = repository.register(username.trim(), email.trim(), password, modelSettings.value)
                handleAuthResponse(response)
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage())
            }
        }
    }

    fun loginAccount(identifier: String, password: String) {
        viewModelScope.launch {
            _accountUiState.value = AccountUiState(loading = true)
            runCatching {
                val response = repository.login(identifier.trim(), password, modelSettings.value)
                handleAuthResponse(response)
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage())
            }
        }
    }

    fun logoutAccount() {
        viewModelScope.launch {
            val current = localStore.getAuthState()
            runCatching {
                if (current.refreshToken.isNotBlank()) {
                    repository.logout(current.refreshToken, modelSettings.value)
                }
            }
            localStore.clearAuthSession()
            _accountUiState.value = AccountUiState(notice = "已退出登录，本机缓存会保留为只读。")
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _accountUiState.value = _accountUiState.value.copy(loading = true, error = null, notice = "")
            runCatching {
                repository.forgotPassword(email.trim(), modelSettings.value)
            }.onSuccess { response ->
                val devToken = response.devResetToken?.takeIf { it.isNotBlank() }
                _accountUiState.value = AccountUiState(
                    notice = if (devToken == null) response.message else "${response.message} 开发重置 Token：$devToken",
                )
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage())
            }
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        viewModelScope.launch {
            _accountUiState.value = _accountUiState.value.copy(loading = true, error = null, notice = "")
            runCatching {
                repository.resetPassword(token.trim(), newPassword, modelSettings.value)
            }.onSuccess {
                _accountUiState.value = AccountUiState(notice = "密码已重置，请使用新密码登录。")
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage())
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _accountUiState.value = _accountUiState.value.copy(loading = true, error = null, notice = "")
            runCatching {
                authorizedCall { token ->
                    repository.changePassword(oldPassword, newPassword, modelSettings.value, token)
                }
                localStore.clearAuthSession()
            }.onSuccess {
                _accountUiState.value = AccountUiState(notice = "密码已修改，请重新登录。")
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage())
            }
        }
    }

    fun resolvePendingSync(uploadLocal: Boolean) {
        viewModelScope.launch {
            val current = localStore.getAuthState()
            val userId = current.user?.id.orEmpty()
            if (!current.isLoggedIn || userId.isBlank()) {
                _accountUiState.value = AccountUiState(error = "请先登录。")
                return@launch
            }
            _accountUiState.value = _accountUiState.value.copy(loading = true, error = null)
            runCatching {
                authorizedCall { token ->
                    if (uploadLocal) {
                        val (schedules, ledgers) = localStore.getStructuredCacheSnapshot()
                        val response = repository.importSync(schedules, ledgers, modelSettings.value, token)
                        localStore.replaceStructuredCache(response.schedules, response.ledgers, ownerId = userId)
                    } else {
                        localStore.clearStructuredCache(ownerId = userId)
                        syncStructuredCache(modelSettings.value, token, userId)
                    }
                }
            }.onSuccess {
                _accountUiState.value = AccountUiState(notice = if (uploadLocal) "本机数据已上传并同步。" else "已切换为云端数据。")
            }.onFailure { throwable ->
                _accountUiState.value = AccountUiState(error = throwable.toReadableMessage(), pendingSyncUserId = userId)
            }
        }
    }

    private suspend fun handleAuthResponse(response: AuthResponse) {
        val before = localStore.getAuthState()
        val (schedules, ledgers) = localStore.getStructuredCacheSnapshot()
        localStore.saveAuthSession(response)
        val hasLocalStructuredCache = schedules.isNotEmpty() || ledgers.isNotEmpty()
        val shouldChoose = hasLocalStructuredCache && before.structuredCacheOwnerId != response.user.id
        if (shouldChoose) {
            _accountUiState.value = AccountUiState(
                notice = "登录成功。请选择如何处理本机已有的日程和记账缓存。",
                pendingSyncUserId = response.user.id,
            )
            return
        }
        syncStructuredCache(modelSettings.value, response.accessToken, response.user.id)
        _accountUiState.value = AccountUiState(notice = "登录成功，数据已同步。")
    }

    private suspend fun syncStoredSessionSafely() {
        runCatching {
            authorizedCall { token ->
                val ownerId = localStore.getAuthState().user?.id.orEmpty()
                syncStructuredCache(modelSettings.value, token, ownerId)
            }
        }.onFailure { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                localStore.clearAuthSession()
                _accountUiState.value = AccountUiState(error = "登录状态已过期，请重新登录。")
            } else {
                _accountUiState.value = AccountUiState(error = "暂时无法同步云端数据，本机缓存仍可查看。")
            }
        }
    }

    private suspend fun <T> authorizedCall(block: suspend (String) -> T): T {
        val current = localStore.getAuthState()
        if (!current.isLoggedIn) {
            throw IllegalStateException("请先在“我的”里登录。")
        }
        return try {
            block(current.accessToken)
        } catch (throwable: HttpException) {
            if (throwable.code() != 401 || current.refreshToken.isBlank()) {
                throw throwable
            }
            runCatching {
                repository.refresh(current.refreshToken, modelSettings.value)
            }.onFailure {
                localStore.clearAuthSession()
            }.getOrThrow().let { refreshed ->
                localStore.saveAuthSession(refreshed)
                try {
                    block(refreshed.accessToken)
                } catch (second: HttpException) {
                    if (second.code() == 401) {
                        localStore.clearAuthSession()
                    }
                    throw second
                }
            }
        }
    }

    private suspend fun syncStructuredCache(settings: ModelSettings, accessToken: String, ownerId: String) {
        val response = repository.syncData(settings, accessToken)
        localStore.replaceStructuredCache(
            schedules = response.schedules,
            ledgers = response.ledgers,
            ownerId = ownerId,
        )
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

internal data class ContextRequest(
    val history: List<ChatMessagePayload>,
    val contextSummary: String,
    val summaryHistory: List<ChatMessagePayload>,
    val nextSummarizedMessageCount: Int,
)

internal const val RECENT_CONTEXT_MESSAGE_LIMIT = 12
internal const val SUMMARY_REFRESH_THRESHOLD = 8
internal const val SUMMARY_SOURCE_MESSAGE_LIMIT = 40
internal const val SUMMARY_SOURCE_CHAR_LIMIT = 12_000

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

internal fun List<ChatMessage>.toContextRequest(snapshot: ContextSnapshot): ContextRequest {
    val payloads = toPayload()
    val recentStart = (payloads.size - RECENT_CONTEXT_MESSAGE_LIMIT).coerceAtLeast(0)
    val history = payloads.drop(recentStart)

    val summarizedCount = snapshot.summarizedMessageCount.coerceIn(0, payloads.size)
    val unsummarizedStart = summarizedCount.coerceAtMost(recentStart)
    val unsummarized = if (recentStart > unsummarizedStart) {
        payloads.subList(unsummarizedStart, recentStart)
    } else {
        emptyList()
    }

    val summaryHistory = if (unsummarized.size >= SUMMARY_REFRESH_THRESHOLD) {
        unsummarized.takeForSummary()
    } else {
        emptyList()
    }

    return ContextRequest(
        history = history,
        contextSummary = snapshot.summary,
        summaryHistory = summaryHistory,
        nextSummarizedMessageCount = unsummarizedStart + summaryHistory.size,
    )
}

private fun List<ChatMessage>.toPayload(): List<ChatMessagePayload> {
    return asSequence()
        .filter { it.kind == ChatMessageKind.MESSAGE }
        .filter { it.content.isNotBlank() }
        .map { ChatMessagePayload(role = it.role, content = it.content) }
        .toList()
}

private fun List<ChatMessagePayload>.takeForSummary(): List<ChatMessagePayload> {
    val selected = mutableListOf<ChatMessagePayload>()
    var totalChars = 0
    for (message in take(SUMMARY_SOURCE_MESSAGE_LIMIT)) {
        val messageChars = message.role.length + message.content.length
        if (selected.isEmpty() && messageChars > SUMMARY_SOURCE_CHAR_LIMIT) {
            val allowedContentLength = (SUMMARY_SOURCE_CHAR_LIMIT - message.role.length).coerceAtLeast(0)
            selected += message.copy(content = message.content.take(allowedContentLength))
            break
        }
        if (selected.isNotEmpty() && totalChars + messageChars > SUMMARY_SOURCE_CHAR_LIMIT) {
            break
        }
        selected += message
        totalChars += messageChars
    }
    return selected
}

private fun Throwable.toReadableMessage(): String {
    if (this is HttpException) {
        return when (code()) {
            400, 401, 403, 404, 409 -> response()?.errorBody()?.string()
                ?.substringAfter("\"detail\":\"", "")
                ?.substringBefore("\"", "")
                ?.ifBlank { message() }
                ?: message()
            else -> "请求失败（HTTP ${code()}），请稍后重试。"
        }
    }
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
