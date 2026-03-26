package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.ChatMessagePayload

class AgentRepository(private val api: AgentApi) {
    suspend fun processNaturalLanguage(text: String, history: List<ChatMessagePayload>): AgentResponse {
        return api.processText(AgentRequest(text = text.trim(), history = history))
    }
}
