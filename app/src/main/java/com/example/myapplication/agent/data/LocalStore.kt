package com.example.myapplication.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.ScheduleItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

private val Context.agentDataStore by preferencesDataStore(name = "agent_store")

class LocalStore(private val context: Context) {
    private val gson = Gson()
    private val secureSettingsStore = SecureSettingsStore(context)

    private val ledgerKey = stringPreferencesKey("ledger_entries")
    private val scheduleKey = stringPreferencesKey("schedule_items")
    private val chatMessagesKey = stringPreferencesKey("chat_messages")
    private val modelSettingsKey = stringPreferencesKey("model_settings")

    fun observeLedgerEntries(): Flow<List<LedgerEntry>> {
        return context.agentDataStore.data.map { prefs ->
            decodeList<LedgerEntry>(prefs, ledgerKey).map(::normalizeLedgerEntry)
        }
    }

    fun observeScheduleItems(): Flow<List<ScheduleItem>> {
        return context.agentDataStore.data.map { prefs ->
            decodeList(prefs, scheduleKey)
        }
    }

    fun observeChatMessages(): Flow<List<ChatMessage>> {
        return context.agentDataStore.data.map { prefs ->
            decodeList(prefs, chatMessagesKey)
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

            val secureApiKey = secureSettingsStore.getApiKey()
            if (secureApiKey.isBlank() && stored.apiKey.isNotBlank()) {
                secureSettingsStore.saveApiKey(stored.apiKey)
            }

            stored.copy(apiKey = secureSettingsStore.getApiKey())
        }
    }

    suspend fun appendLedger(entry: LedgerEntry) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            prefs[ledgerKey] = gson.toJson(listOf(normalizeLedgerEntry(entry)) + current.map(::normalizeLedgerEntry))
        }
    }

    suspend fun updateLedger(entry: LedgerEntry) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            val updated = current.map {
                if (it.id == entry.id) {
                    normalizeLedgerEntry(entry)
                } else {
                    normalizeLedgerEntry(it)
                }
            }
            prefs[ledgerKey] = gson.toJson(updated)
        }
    }

    suspend fun deleteLedger(entryId: String) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            prefs[ledgerKey] = gson.toJson(current.filterNot { it.id == entryId })
        }
    }

    suspend fun appendSchedule(item: ScheduleItem) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ScheduleItem>(prefs, scheduleKey)
            prefs[scheduleKey] = gson.toJson(listOf(item) + current)
        }
    }

    suspend fun updateSchedule(item: ScheduleItem) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ScheduleItem>(prefs, scheduleKey)
            val updated = current.map { if (it.id == item.id) item else it }
            prefs[scheduleKey] = gson.toJson(updated)
        }
    }

    suspend fun deleteSchedule(itemId: String) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ScheduleItem>(prefs, scheduleKey)
            prefs[scheduleKey] = gson.toJson(current.filterNot { it.id == itemId })
        }
    }

    suspend fun appendChatMessage(message: ChatMessage) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            prefs[chatMessagesKey] = gson.toJson(current + message)
        }
    }

    suspend fun deleteChatMessageAt(index: Int) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<ChatMessage>(prefs, chatMessagesKey)
            if (index !in current.indices) return@edit
            val updated = current.toMutableList().apply { removeAt(index) }
            prefs[chatMessagesKey] = gson.toJson(updated)
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
        secureSettingsStore.saveApiKey(settings.apiKey)
        context.agentDataStore.edit { prefs ->
            prefs[modelSettingsKey] = gson.toJson(settings.copy(apiKey = ""))
        }
    }

    suspend fun migrateLegacyModelSettings() {
        context.agentDataStore.edit { prefs ->
            val json = prefs[modelSettingsKey] ?: return@edit
            val stored = runCatching { gson.fromJson(json, ModelSettings::class.java) }
                .getOrDefault(ModelSettings())

            if (stored.apiKey.isBlank()) {
                return@edit
            }

            if (secureSettingsStore.getApiKey().isBlank()) {
                secureSettingsStore.saveApiKey(stored.apiKey)
            }

            prefs[modelSettingsKey] = gson.toJson(stored.copy(apiKey = ""))
        }
    }

    suspend fun migrateLegacyLedgerEntries() {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            if (current.isEmpty()) return@edit

            val normalized = current.map(::normalizeLedgerEntry)
            if (normalized != current) {
                prefs[ledgerKey] = gson.toJson(normalized)
            }
        }
    }

    private inline fun <reified T> decodeList(
        prefs: Preferences,
        key: Preferences.Key<String>
    ): List<T> {
        val json = prefs[key] ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrDefault(emptyList())
    }

    private fun normalizeLedgerEntry(entry: LedgerEntry): LedgerEntry {
        return entry.copy(
            date = normalizeLedgerDate(entry.date),
            entryType = normalizeLedgerEntryType(entry.entryType)
        )
    }

    private fun normalizeLedgerDate(raw: String): String {
        val sanitized = raw.trim()
            .substringBefore("T")
            .substringBefore(" ")
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", "")
            .replace("/", "-")
            .replace(".", "-")
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

    private fun normalizeLedgerEntryType(raw: String): String {
        return when (raw.trim().lowercase()) {
            "income", "收入", "入账", "进账", "earning", "earnings" -> "income"
            else -> "expense"
        }
    }
}
