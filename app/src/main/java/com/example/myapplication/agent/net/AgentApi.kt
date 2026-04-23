package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.HealthResponse
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST

interface AgentApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("agent/process")
    suspend fun processText(@Body request: AgentRequest): AgentResponse
}
