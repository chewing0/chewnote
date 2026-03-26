package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AgentApi {
    @POST("agent/process")
    suspend fun processText(@Body request: AgentRequest): AgentResponse
}
