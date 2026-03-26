package com.example.myapplication.agent.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.agent.model.ChatMessage
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ScheduleItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agentDataStore by preferencesDataStore(name = "agent_store")

class LocalStore(private val context: Context) {
    private val gson = Gson()

    private val ledgerKey = stringPreferencesKey("ledger_entries")
    private val scheduleKey = stringPreferencesKey("schedule_items")
    private val chatMessagesKey = stringPreferencesKey("chat_messages")

    fun observeLedgerEntries(): Flow<List<LedgerEntry>> {
        return context.agentDataStore.data.map { prefs ->
            decodeList(prefs, ledgerKey)
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

    suspend fun appendLedger(entry: LedgerEntry) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            prefs[ledgerKey] = gson.toJson(listOf(entry) + current)
        }
    }

    suspend fun updateLedger(entry: LedgerEntry) {
        context.agentDataStore.edit { prefs ->
            val current = decodeList<LedgerEntry>(prefs, ledgerKey)
            val updated = current.map { if (it.id == entry.id) entry else it }
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

    private inline fun <reified T> decodeList(
        prefs: Preferences,
        key: Preferences.Key<String>
    ): List<T> {
        val json = prefs[key] ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrDefault(emptyList())
    }
}
