package com.example.myapplication.agent.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.util.UUID

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val category: String,
    val note: String,
    val date: String = LocalDate.now().toString(),
    val entryType: String = "expense"
)

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String = LocalDate.now().toString(),
    val time: String = "09:00",
    val note: String = ""
)

data class AgentRequest(
    val text: String,
    val history: List<ChatMessagePayload> = emptyList(),
    @SerializedName("model_config")
    val modelConfig: ModelConfigPayload? = null
)

data class ModelConfigPayload(
    @SerializedName("base_url")
    val baseUrl: String,
    val model: String,
    @SerializedName("api_key")
    val apiKey: String
)

data class ModelSettings(
    val backendUrl: String = "http://10.0.2.2:8000/",
    val modelBaseUrl: String = "https://api.moonshot.cn/v1",
    val modelName: String = "moonshot-v1-8k",
    val apiKey: String = ""
)

data class ChatMessagePayload(
    val role: String,
    val content: String
)

data class ChatMessage(
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class AgentAction(
    val type: String,
    val payload: Map<String, Any?> = emptyMap()
)

data class AgentResponse(
    val reply: String,
    val actions: List<AgentAction> = emptyList()
)
