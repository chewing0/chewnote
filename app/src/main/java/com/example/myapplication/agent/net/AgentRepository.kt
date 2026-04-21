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
        val modelConfig = ModelConfigPayload(
            baseUrl = settings.modelBaseUrl,
            model = settings.modelName,
            apiKey = settings.apiKey,
        )
        return api.processText(
            AgentRequest(
                text = text.trim(),
                history = history,
                modelConfig = modelConfig,
            )
        )
    }
}
