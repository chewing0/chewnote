package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.AuthResponse
import com.example.myapplication.agent.model.AuthUser
import com.example.myapplication.agent.model.ChangePasswordRequest
import com.example.myapplication.agent.model.DeleteResponse
import com.example.myapplication.agent.model.ForgotPasswordRequest
import com.example.myapplication.agent.model.ForgotPasswordResponse
import com.example.myapplication.agent.model.HealthResponse
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LoginRequest
import com.example.myapplication.agent.model.RefreshTokenRequest
import com.example.myapplication.agent.model.RegisterRequest
import com.example.myapplication.agent.model.ResetPasswordRequest
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.model.SimpleStatusResponse
import com.example.myapplication.agent.model.SyncImportRequest
import com.example.myapplication.agent.model.SyncResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST

interface AgentApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequest): SimpleStatusResponse

    @GET("auth/me")
    suspend fun me(@Header("Authorization") authorization: String): AuthUser

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("auth/password/reset")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): SimpleStatusResponse

    @POST("auth/password/change")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequest,
    ): SimpleStatusResponse

    @GET("sync")
    suspend fun sync(@Header("Authorization") authorization: String): SyncResponse

    @POST("sync/import")
    suspend fun importSync(
        @Header("Authorization") authorization: String,
        @Body request: SyncImportRequest,
    ): SyncResponse

    @POST("schedules")
    suspend fun createSchedule(@Header("Authorization") authorization: String, @Body item: ScheduleItem): ScheduleItem

    @PATCH("schedules/{id}")
    suspend fun updateSchedule(@Header("Authorization") authorization: String, @Path("id") id: String, @Body item: ScheduleItem): ScheduleItem

    @DELETE("schedules/{id}")
    suspend fun deleteSchedule(@Header("Authorization") authorization: String, @Path("id") id: String): DeleteResponse

    @POST("ledgers")
    suspend fun createLedger(@Header("Authorization") authorization: String, @Body entry: LedgerEntry): LedgerEntry

    @PATCH("ledgers/{id}")
    suspend fun updateLedger(@Header("Authorization") authorization: String, @Path("id") id: String, @Body entry: LedgerEntry): LedgerEntry

    @DELETE("ledgers/{id}")
    suspend fun deleteLedger(@Header("Authorization") authorization: String, @Path("id") id: String): DeleteResponse

    @POST("agent/process")
    suspend fun processText(@Header("Authorization") authorization: String, @Body request: AgentRequest): AgentResponse
}
