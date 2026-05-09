package com.example.myapplication.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.agent.data.LocalStore
import com.example.myapplication.agent.model.AuthResponse
import com.example.myapplication.agent.model.AuthState
import com.example.myapplication.agent.model.BackendModelStatus
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.Conversation
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LedgerPeriod
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.net.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

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

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _accountUiState = MutableStateFlow(AccountUiState())
    val accountUiState: StateFlow<AccountUiState> = _accountUiState.asStateFlow()

    private val _connectionTestResult = MutableStateFlow(ConnectionTestResult())
    val connectionTestResult: StateFlow<ConnectionTestResult> = _connectionTestResult.asStateFlow()

    private val _backendModelStatus = MutableStateFlow<BackendModelStatus?>(null)
    val backendModelStatus: StateFlow<BackendModelStatus?> = _backendModelStatus.asStateFlow()

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

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow("")
    val currentConversationId: StateFlow<String> = _currentConversationId.asStateFlow()

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
            localStore.clearLocalConversationStorage()
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

        viewModelScope.launch {
            val optimisticMessage = ChatMessage(role = "user", content = content)
            _chatMessages.value = _chatMessages.value + optimisticMessage
            _uiState.value = _uiState.value.copy(
                input = "",
                loading = true,
                error = null,
            )

            runCatching {
                authorizedCall { token ->
                    val conversationId = ensureActiveConversation(content, token)
                    repository.processNaturalLanguage(
                        text = content,
                        conversationId = conversationId,
                        settings = modelSettings.value,
                        accessToken = token,
                    )
                }
            }.onSuccess { response ->
                if (response.conversationId.isNotBlank()) {
                    _currentConversationId.value = response.conversationId
                }
                if (response.changedDomains.any { it == "schedule" || it == "ledger" }) {
                    authorizedCall { token ->
                        syncStructuredCache(modelSettings.value, token, authState.value.user?.id.orEmpty())
                    }
                }
                refreshConversationState(selectConversationId = response.conversationId.ifBlank { _currentConversationId.value })
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = null,
                )
            }.onFailure { throwable ->
                val friendlyError = throwable.toReadableMessage()
                _chatMessages.value = _chatMessages.value + listOf(
                    ChatMessage(
                        role = "assistant",
                        content = "当前连接暂时不可用，请检查后端地址或设置页里的模型配置后再试。",
                    ),
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
            // Action receipts from older local chats are no longer persisted after conversations moved to backend.
        }
    }

    fun deleteChatMessageAt(index: Int) {
        viewModelScope.launch {
            val conversationId = _currentConversationId.value
            val message = _chatMessages.value.getOrNull(index)
            if (conversationId.isBlank() || message?.id.isNullOrBlank()) return@launch
            runCatching {
                authorizedCall { token ->
                    repository.deleteConversationMessage(conversationId, message.id, modelSettings.value, token)
                    loadConversationMessages(conversationId, modelSettings.value, token)
                    refreshConversations(modelSettings.value, token)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun createConversation() {
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    val created = repository.createConversation("新对话", modelSettings.value, token)
                    _currentConversationId.value = created.id
                    refreshConversationState(selectConversationId = created.id)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun selectConversation(conversationId: String) {
        if (conversationId == _currentConversationId.value) return
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    _currentConversationId.value = conversationId
                    loadConversationMessages(conversationId, modelSettings.value, token)
                    refreshConversations(modelSettings.value, token)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun renameConversation(conversationId: String, title: String) {
        val cleanTitle = title.trim()
        if (conversationId.isBlank() || cleanTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    repository.updateConversation(conversationId, cleanTitle, modelSettings.value, token)
                    refreshConversations(modelSettings.value, token)
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                authorizedCall { token ->
                    repository.deleteConversation(conversationId, modelSettings.value, token)
                    if (_currentConversationId.value == conversationId) {
                        _currentConversationId.value = ""
                        _chatMessages.value = emptyList()
                    }
                    refreshConversationState(selectConversationId = "")
                }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(error = throwable.toReadableMessage())
            }
        }
    }

    fun saveModelSettings(settings: ModelSettings) {
        viewModelScope.launch {
            localStore.saveModelSettings(settings)
            refreshBackendModelStatus(settings)
            refreshAgentStatus(settings)
        }
    }

    fun refreshBackendModelStatus(candidateSettings: ModelSettings? = null) {
        viewModelScope.launch {
            val settings = candidateSettings ?: modelSettings.value
            _backendModelStatus.value = runCatching {
                repository.getBackendModelStatus(settings)
            }.getOrNull()
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
            _conversations.value = emptyList()
            _currentConversationId.value = ""
            _chatMessages.value = emptyList()
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
                _conversations.value = emptyList()
                _currentConversationId.value = ""
                _chatMessages.value = emptyList()
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
                    refreshConversationState(settings = modelSettings.value, accessToken = token)
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
        refreshConversationState(settings = modelSettings.value, accessToken = response.accessToken)
        _accountUiState.value = AccountUiState(notice = "登录成功，数据已同步。")
    }

    private suspend fun syncStoredSessionSafely() {
        runCatching {
            authorizedCall { token ->
                val ownerId = localStore.getAuthState().user?.id.orEmpty()
                syncStructuredCache(modelSettings.value, token, ownerId)
                refreshConversationState(settings = modelSettings.value, accessToken = token)
            }
        }.onFailure { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                localStore.clearAuthSession()
                _conversations.value = emptyList()
                _currentConversationId.value = ""
                _chatMessages.value = emptyList()
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

    private suspend fun ensureActiveConversation(seedText: String, accessToken: String): String {
        val current = _currentConversationId.value
        if (current.isNotBlank() && _conversations.value.any { it.id == current }) {
            return current
        }
        val title = seedText.trim().replace(Regex("\\s+"), " ").take(18).ifBlank { "新对话" }
        val created = repository.createConversation(title, modelSettings.value, accessToken)
        _currentConversationId.value = created.id
        refreshConversations(modelSettings.value, accessToken)
        _chatMessages.value = emptyList()
        return created.id
    }

    private suspend fun refreshConversationState(
        selectConversationId: String = _currentConversationId.value,
        settings: ModelSettings = modelSettings.value,
        accessToken: String? = null,
    ) {
        val token = accessToken ?: localStore.getAuthState().accessToken
        if (token.isBlank()) {
            _conversations.value = emptyList()
            _currentConversationId.value = ""
            _chatMessages.value = emptyList()
            return
        }
        val conversations = refreshConversations(settings, token)
        val targetId = when {
            selectConversationId.isNotBlank() && conversations.any { it.id == selectConversationId } -> selectConversationId
            _currentConversationId.value.isNotBlank() && conversations.any { it.id == _currentConversationId.value } -> _currentConversationId.value
            else -> conversations.firstOrNull()?.id.orEmpty()
        }
        _currentConversationId.value = targetId
        if (targetId.isBlank()) {
            _chatMessages.value = emptyList()
        } else {
            loadConversationMessages(targetId, settings, token)
        }
    }

    private suspend fun refreshConversations(settings: ModelSettings, accessToken: String): List<Conversation> {
        val conversations = repository.listConversations(settings, accessToken)
        _conversations.value = conversations
        return conversations
    }

    private suspend fun loadConversationMessages(conversationId: String, settings: ModelSettings, accessToken: String) {
        if (conversationId.isBlank()) {
            _chatMessages.value = emptyList()
            return
        }
        _chatMessages.value = repository.listConversationMessages(conversationId, settings, accessToken)
    }
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

