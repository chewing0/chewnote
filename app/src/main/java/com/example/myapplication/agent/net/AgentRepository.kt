package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.ChatMessagePayload
import com.example.myapplication.agent.model.ModelConfigPayload
import com.example.myapplication.agent.model.ModelSettings

class AgentRepository {
    suspend fun processNaturalLanguage(
        text: String,
        history: List<ChatMessagePayload>,
        settings: ModelSettings,
    ): AgentResponse {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        val modelConfig = settings.toModelConfigOrNull()
        return api.processText(
            AgentRequest(
                text = text.trim(),
                history = history,
                modelConfig = modelConfig,
            )
        )
    }
}

private fun ModelSettings.toModelConfigOrNull(): ModelConfigPayload? {
    val baseUrl = modelBaseUrl.trim()
    val model = modelName.trim()
    val apiKey = apiKey.trim()
    if (baseUrl.isBlank() && model.isBlank() && apiKey.isBlank()) {
        return null
    }
    return ModelConfigPayload(
        baseUrl = baseUrl,
        model = model,
        apiKey = apiKey,
    )
}
