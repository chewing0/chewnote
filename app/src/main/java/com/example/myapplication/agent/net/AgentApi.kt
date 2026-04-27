package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.DeleteResponse
import com.example.myapplication.agent.model.HealthResponse
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.model.SyncResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST

interface AgentApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("sync")
    suspend fun sync(): SyncResponse

    @POST("schedules")
    suspend fun createSchedule(@Body item: ScheduleItem): ScheduleItem

    @PATCH("schedules/{id}")
    suspend fun updateSchedule(@Path("id") id: String, @Body item: ScheduleItem): ScheduleItem

    @DELETE("schedules/{id}")
    suspend fun deleteSchedule(@Path("id") id: String): DeleteResponse

    @POST("ledgers")
    suspend fun createLedger(@Body entry: LedgerEntry): LedgerEntry

    @PATCH("ledgers/{id}")
    suspend fun updateLedger(@Path("id") id: String, @Body entry: LedgerEntry): LedgerEntry

    @DELETE("ledgers/{id}")
    suspend fun deleteLedger(@Path("id") id: String): DeleteResponse

    @POST("agent/process")
    suspend fun processText(@Body request: AgentRequest): AgentResponse
}
