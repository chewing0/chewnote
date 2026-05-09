package com.example.myapplication.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.example.myapplication.agent.model.ActionReceipt
import com.example.myapplication.agent.model.AuthResponse
import com.example.myapplication.agent.model.AuthState
import com.example.myapplication.agent.model.AuthUser
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.ChatMessageKind
import com.example.myapplication.agent.model.ContextSnapshot
import com.example.myapplication.agent.model.LedgerCategoryCatalog
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.ScheduleItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.UUID

private val Context.agentDataStore by preferencesDataStore(name = "agent_store")

class LocalStore(private val context: Context) {
    private val gson = Gson()
    private val secureSettingsStore = SecureSettingsStore(context)
    private val database = AgentDatabase.getInstance(context)
    private val ledgerDao = database.ledgerDao()
    private val scheduleDao = database.scheduleDao()

    private val legacyLedgerKey = stringPreferencesKey("ledger_entries")
    private val legacyScheduleKey = stringPreferencesKey("schedule_items")
    private val chatMessagesKey = stringPreferencesKey("chat_messages")
    private val contextSnapshotKey = stringPreferencesKey("context_snapshot")
    private val sessionIdKey = stringPreferencesKey("session_id")
    private val modelSettingsKey = stringPreferencesKey("model_settings")
    private val authUserKey = stringPreferencesKey("auth_user")
    private val structuredCacheOwnerIdKey = stringPreferencesKey("structured_cache_owner_id")
    private val roomMigrationKey = booleanPreferencesKey("structured_room_migration_v1")
    private val ledgerCategoryMigrationKey = booleanPreferencesKey("ledger_category_migration_v1")
    private val backendStorageMigrationKey = booleanPreferencesKey("backend_storage_migration_v1")

    fun observeLedgerEntries(): Flow<List<LedgerEntry>> {
        return ledgerDao.observeAll()
    }

    fun observeScheduleItems(): Flow<List<ScheduleItem>> {
        return scheduleDao.observeAll()
    }

    fun observeChatMessages(): Flow<List<ChatMessage>> {
        return context.agentDataStore.data.map { prefs ->
            decodeList(prefs, chatMessagesKey)
        }
    }

    fun observeContextSnapshot(): Flow<ContextSnapshot> {
        return context.agentDataStore.data.map { prefs ->
            decodeObject(prefs, contextSnapshotKey) ?: ContextSnapshot()
        }
    }

    fun observeModelSettings(): Flow<ModelSettings> {
        return context.agentDataStore.data.map { prefs ->
            val json = prefs[modelSettingsKey]
            val stored = if (json.isNullOrBlank()) {
                ModelSettings()
            } else {
                runCatching { gson.fromJson(json, ModelSettings::class.java) }.getOrDefault(ModelSettings())
            }

            stored.backendOnly()
        }
    }

    suspend fun appendLedger(entry: LedgerEntry) {
        ledgerDao.upsert(normalizeLedgerEntry(entry))
    }

    suspend fun updateLedger(entry: LedgerEntry) {
        ledgerDao.upsert(normalizeLedgerEntry(entry))
    }

    suspend fun deleteLedger(entryId: String) {
        ledgerDao.deleteById(entryId)
    }

    suspend fun appendSchedule(item: ScheduleItem) {
        scheduleDao.upsert(normalizeScheduleItem(item))
    }

    suspend fun updateSchedule(item: ScheduleItem) {
        scheduleDao.upsert(normalizeScheduleItem(item))
    }

    suspend fun deleteSchedule(itemId: String) {
        scheduleDao.deleteById(itemId)
    }

    suspend fun deleteEntriesForActionBatch(batchId: String) {
        database.withTransaction {
            ledgerDao.deleteByActionBatchId(batchId)
            scheduleDao.deleteByActionBatchId(batchId)
        }
    }

    fun observeAuthState(): Flow<AuthState> {
        return context.agentDataStore.data.map { prefs ->
            val user = decodeObject<AuthUser>(prefs, authUserKey)
            AuthState(
                user = user,
                accessToken = secureSettingsStore.getAccessToken(),
                refreshToken = secureSettingsStore.getRefreshToken(),
                structuredCacheOwnerId = prefs[structuredCacheOwnerIdKey].orEmpty(),
            )
        }
    }

    suspend fun getAuthState(): AuthState {
        val prefs = context.agentDataStore.data.first()
        return AuthState(
            user = decodeObject<AuthUser>(prefs, authUserKey),
            accessToken = secureSettingsStore.getAccessToken(),
            refreshToken = secureSettingsStore.getRefreshToken(),
            structuredCacheOwnerId = prefs[structuredCacheOwnerIdKey].orEmpty(),
        )
    }

    suspend fun replaceStructuredCache(
        schedules: List<ScheduleItem>,
        ledgers: List<LedgerEntry>,
        ownerId: String? = null,
    ) {
        database.withTransaction {
            scheduleDao.deleteAll()
            ledgerDao.deleteAll()
            if (schedules.isNotEmpty()) {
                scheduleDao.upsertAll(schedules.map(::normalizeScheduleItem))
            }
            if (ledgers.isNotEmpty()) {
                ledgerDao.upsertAll(ledgers.map(::normalizeLedgerEntry))
            }
        }
        if (ownerId != null) {
            saveStructuredCacheOwner(ownerId)
        }
    }

    suspend fun getStructuredCacheSnapshot(): Pair<List<ScheduleItem>, List<LedgerEntry>> {
        return scheduleDao.getAll() to ledgerDao.getAll()
    }

    suspend fun clearStructuredCache(ownerId: String? = null) {
        replaceStructuredCache(emptyList(), emptyList(), ownerId)
    }

    suspend fun saveStructuredCacheOwner(ownerId: String) {
        context.agentDataStore.edit { prefs ->
            prefs[structuredCacheOwnerIdKey] = ownerId
        }
    }

    suspend fun saveAuthSession(response: AuthResponse) {
        secureSettingsStore.saveAuthTokens(response.accessToken, response.refreshToken)
        context.agentDataStore.edit { prefs ->
            prefs[authUserKey] = gson.toJson(response.user)
        }
    }

    suspend fun clearAuthSession() {
        secureSettingsStore.clearAuthTokens()
        context.agentDataStore.edit { prefs ->
            prefs.remove(authUserKey)
        }
    }

    suspend fun migrateToBackendStorageCache() {
        val prefs = context.agentDataStore.data.first()
        if (prefs[backendStorageMigrationKey] == true) {
            return
        }

        context.agentDataStore.edit { mutablePrefs ->
            mutablePrefs[backendStorageMigrationKey] = true
        }
    }

    suspend fun getOrCreateSessionId(): String {
        val current = context.agentDataStore.data.first()[sessionIdKey]
        if (!current.isNullOrBlank()) {
            return current
        }
        val created = UUID.randomUUID().toString()
        context.agentDataStore.edit { prefs ->
            prefs[sessionIdKey] = created
        }
        return created
    }

    suspend fun appendChatMessage(message: ChatMessage) {
        appendChatMessages(listOf(message))
    }

    suspend fun appendChatMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            prefs[chatMessagesKey] = gson.toJson(current + messages)
        }
    }

    suspend fun deleteChatMessageAt(index: Int) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            if (index !in current.indices) return@edit
            val updated = current.toMutableList().apply { removeAt(index) }
            prefs[chatMessagesKey] = gson.toJson(updated)
            prefs.remove(contextSnapshotKey)
        }
    }

    suspend fun saveContextSnapshot(snapshot: ContextSnapshot) {
        context.agentDataStore.edit { prefs ->
            prefs[contextSnapshotKey] = gson.toJson(snapshot)
        }
    }

    suspend fun clearLocalConversationStorage() {
        context.agentDataStore.edit { prefs ->
            prefs.remove(chatMessagesKey)
            prefs.remove(contextSnapshotKey)
            prefs.remove(sessionIdKey)
        }
    }

    suspend fun clearContextSnapshot() {
        context.agentDataStore.edit { prefs ->
            prefs.remove(contextSnapshotKey)
        }
    }

    suspend fun markReceiptUndone(batchId: String) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            val updated = current.map { message ->
                val receipt = message.actionReceipt
                if (message.kind == ChatMessageKind.ACTION_RECEIPT && receipt?.batchId == batchId && !receipt.undone) {
                    message.copy(actionReceipt = receipt.copy(undone = true))
                } else {
                    message
                }
            }
            if (updated != current) {
                prefs[chatMessagesKey] = gson.toJson(updated)
            }
        }
    }

    suspend fun ensureWelcomeMessage(message: ChatMessage) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            if (current.isEmpty()) {
                prefs[chatMessagesKey] = gson.toJson(listOf(message))
            }
        }
    }

    suspend fun migrateLegacyWelcomeMessage(oldContent: String, newContent: String) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            if (current.isEmpty()) return@edit

            val updated = current.mapIndexed { index, message ->
                if (index == 0 && message.role == "assistant" && message.content == oldContent) {
                    message.copy(content = newContent)
                } else {
                    message
                }
            }

            if (updated != current) {
                prefs[chatMessagesKey] = gson.toJson(updated)
            }
        }
    }

    suspend fun saveModelSettings(settings: ModelSettings) {
        secureSettingsStore.saveApiKey("")
        context.agentDataStore.edit { prefs ->
            prefs[modelSettingsKey] = gson.toJson(settings.backendOnly())
        }
    }

    suspend fun migrateLegacyModelSettings() {
        context.agentDataStore.edit { prefs ->
            val json = prefs[modelSettingsKey] ?: return@edit
            val stored = runCatching { gson.fromJson(json, ModelSettings::class.java) }
                .getOrDefault(ModelSettings())

            secureSettingsStore.saveApiKey("")
            prefs[modelSettingsKey] = gson.toJson(stored.backendOnly())
        }
    }

    suspend fun migrateLegacyStructuredData() {
        val prefs = context.agentDataStore.data.first()
        if (prefs[roomMigrationKey] == true) {
            return
        }

        val legacyLedgers = decodeList<LedgerEntry>(prefs, legacyLedgerKey).map(::normalizeLedgerEntry)
        val legacySchedules = decodeList<ScheduleItem>(prefs, legacyScheduleKey).map(::normalizeScheduleItem)

        database.withTransaction {
            if (legacyLedgers.isNotEmpty()) {
                ledgerDao.upsertAll(legacyLedgers)
            }
            if (legacySchedules.isNotEmpty()) {
                scheduleDao.upsertAll(legacySchedules)
            }
        }

        context.agentDataStore.edit { mutablePrefs ->
            mutablePrefs[roomMigrationKey] = true
            mutablePrefs.remove(legacyLedgerKey)
            mutablePrefs.remove(legacyScheduleKey)
        }
    }

    suspend fun migrateLedgerCategoriesToPreset() {
        val prefs = context.agentDataStore.data.first()
        if (prefs[ledgerCategoryMigrationKey] == true) {
            return
        }

        val currentEntries = ledgerDao.getAll()
        val normalizedEntries = currentEntries.map(::normalizeLedgerEntry)
        val changed = normalizedEntries.zip(currentEntries).any { (normalized, original) ->
            normalized.category != original.category || normalized.entryType != original.entryType
        }

        if (changed) {
            ledgerDao.upsertAll(normalizedEntries)
        }

        context.agentDataStore.edit { mutablePrefs ->
            mutablePrefs[ledgerCategoryMigrationKey] = true
        }
    }

    private inline fun <reified T> decodeList(
        prefs: Preferences,
        key: Preferences.Key<String>,
    ): List<T> {
        val json = prefs[key] ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrDefault(emptyList())
    }

    private inline fun <reified T> decodeObject(
        prefs: Preferences,
        key: Preferences.Key<String>,
    ): T? {
        val json = prefs[key] ?: return null
        return runCatching { gson.fromJson(json, T::class.java) }.getOrNull()
    }

    private fun normalizeLedgerEntry(entry: LedgerEntry): LedgerEntry {
        val normalizedEntryType = normalizeLedgerEntryType(entry.entryType)
        return entry.copy(
            id = entry.id.ifBlank { UUID.randomUUID().toString() },
            date = normalizeDate(entry.date),
            entryType = normalizedEntryType,
            category = LedgerCategoryCatalog.normalizeCategory(entry.category, normalizedEntryType),
            createdAt = entry.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            actionBatchId = entry.actionBatchId?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun normalizeScheduleItem(item: ScheduleItem): ScheduleItem {
        return item.copy(
            id = item.id.ifBlank { UUID.randomUUID().toString() },
            date = normalizeDate(item.date),
            time = normalizeTime(item.time),
            createdAt = item.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            actionBatchId = item.actionBatchId?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun normalizeDate(raw: String): String {
        val sanitized = raw.trim()
            .substringBefore("T")
            .substringBefore(" ")
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", "")
            .replace("/", "-")
            .replace(".", "-")
            .replace("：", ":")
            .trim('-')

        if (sanitized.isBlank()) {
            return LocalDate.now().toString()
        }

        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter()

        return runCatching {
            LocalDate.parse(sanitized, formatter).toString()
        }.getOrElse {
            LocalDate.now().toString()
        }
    }

    private fun normalizeTime(raw: String): String {
        val sanitized = raw.trim().replace("：", ":")
        if (sanitized.isBlank()) {
            return "09:00"
        }

        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR)
            .toFormatter()

        return runCatching {
            LocalTime.parse(sanitized, formatter).toString().take(5)
        }.getOrElse {
            "09:00"
        }
    }

    private fun normalizeLedgerEntryType(raw: String): String {
        return when (raw.trim().lowercase()) {
            "income", "收入", "入账", "进账", "earning", "earnings" -> "income"
            else -> "expense"
        }
    }
}

private fun ModelSettings.backendOnly(): ModelSettings {
    return copy(
        backendUrl = backendUrl.trim(),
        modelBaseUrl = "",
        modelName = "",
        apiKey = "",
    )
}
