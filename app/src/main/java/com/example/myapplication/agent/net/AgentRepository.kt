package com.example.myapplication.agent.net

import com.example.myapplication.agent.model.AgentRequest
import com.example.myapplication.agent.model.AgentResponse
import com.example.myapplication.agent.model.AuthResponse
import com.example.myapplication.agent.model.ChangePasswordRequest
import com.example.myapplication.agent.model.ChatMessagePayload
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.ForgotPasswordRequest
import com.example.myapplication.agent.model.ForgotPasswordResponse
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.LoginRequest
import com.example.myapplication.agent.model.ModelConfigPayload
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.model.RefreshTokenRequest
import com.example.myapplication.agent.model.RegisterRequest
import com.example.myapplication.agent.model.ResetPasswordRequest
import com.example.myapplication.agent.model.ScheduleItem
import com.example.myapplication.agent.model.SyncImportRequest
import com.example.myapplication.agent.model.SyncResponse

class AgentRepository {
    suspend fun register(
        username: String,
        email: String,
        password: String,
        settings: ModelSettings,
    ): AuthResponse {
        return NetworkModule.createAgentApi(settings.backendUrl).register(RegisterRequest(username, email, password))
    }

    suspend fun login(identifier: String, password: String, settings: ModelSettings): AuthResponse {
        return NetworkModule.createAgentApi(settings.backendUrl).login(LoginRequest(identifier, password))
    }

    suspend fun refresh(refreshToken: String, settings: ModelSettings): AuthResponse {
        return NetworkModule.createAgentApi(settings.backendUrl).refresh(RefreshTokenRequest(refreshToken))
    }

    suspend fun logout(refreshToken: String, settings: ModelSettings) {
        NetworkModule.createAgentApi(settings.backendUrl).logout(RefreshTokenRequest(refreshToken))
    }

    suspend fun forgotPassword(email: String, settings: ModelSettings): ForgotPasswordResponse {
        return NetworkModule.createAgentApi(settings.backendUrl).forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun resetPassword(token: String, newPassword: String, settings: ModelSettings) {
        NetworkModule.createAgentApi(settings.backendUrl).resetPassword(ResetPasswordRequest(token, newPassword))
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
        settings: ModelSettings,
        accessToken: String,
    ) {
        NetworkModule.createAgentApi(settings.backendUrl)
            .changePassword(bearer(accessToken), ChangePasswordRequest(oldPassword, newPassword))
    }

    suspend fun processNaturalLanguage(
        text: String,
        sessionId: String,
        history: List<ChatMessagePayload>,
        contextSummary: String,
        summaryHistory: List<ChatMessagePayload>,
        settings: ModelSettings,
        accessToken: String,
    ): AgentResponse {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        val modelConfig = settings.toModelConfigOrNull()
        return api.processText(
            bearer(accessToken),
            AgentRequest(
                text = text.trim(),
                sessionId = sessionId,
                history = history,
                contextSummary = contextSummary,
                summaryHistory = summaryHistory,
                modelConfig = modelConfig,
            )
        )
    }

    suspend fun syncData(settings: ModelSettings, accessToken: String): SyncResponse {
        return NetworkModule.createAgentApi(settings.backendUrl).sync(bearer(accessToken))
    }

    suspend fun importSync(
        schedules: List<ScheduleItem>,
        ledgers: List<LedgerEntry>,
        settings: ModelSettings,
        accessToken: String,
    ): SyncResponse {
        return NetworkModule.createAgentApi(settings.backendUrl)
            .importSync(bearer(accessToken), SyncImportRequest(schedules, ledgers))
    }

    suspend fun updateLedger(entry: LedgerEntry, settings: ModelSettings, accessToken: String): LedgerEntry {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        return api.updateLedger(bearer(accessToken), entry.id, entry)
    }

    suspend fun deleteLedger(entryId: String, settings: ModelSettings, accessToken: String) {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        api.deleteLedger(bearer(accessToken), entryId)
    }

    suspend fun updateSchedule(item: ScheduleItem, settings: ModelSettings, accessToken: String): ScheduleItem {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        return api.updateSchedule(bearer(accessToken), item.id, item)
    }

    suspend fun deleteSchedule(itemId: String, settings: ModelSettings, accessToken: String) {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        api.deleteSchedule(bearer(accessToken), itemId)
    }

    suspend fun testConnection(settings: ModelSettings, accessToken: String = ""): ConnectionTestResult {
        val api = NetworkModule.createAgentApi(settings.backendUrl)
        val modelConfig = settings.toModelConfigOrNull()

        val health = runCatching { api.health() }.getOrElse {
            return ConnectionTestResult(
                status = ConnectionTestStatus.FAILURE,
                title = "后端不可达",
                detail = "未能连接到 ${NetworkModule.resolveBackendUrl(settings.backendUrl)}，请确认服务是否启动。",
            )
        }

        if (!health.status.equals("ok", ignoreCase = true)) {
            return ConnectionTestResult(
                status = ConnectionTestStatus.FAILURE,
                title = "后端状态异常",
                detail = "健康检查没有返回可用状态，请先确认后端服务本身正常。",
            )
        }

        if (accessToken.isBlank()) {
            return ConnectionTestResult(
                status = ConnectionTestStatus.SUCCESS,
                title = "后端可达",
                detail = "后端服务正常。登录后可以继续验证模型和 Agent 工具调用。",
            )
        }

        val probe = runCatching {
            api.processText(
                bearer(accessToken),
                AgentRequest(
                    text = "连接测试。请只回复“测试成功”，不要调用任何工具。",
                    sessionId = "connection-test",
                    history = emptyList(),
                    modelConfig = modelConfig,
                )
            )
        }.getOrElse { throwable ->
            return ConnectionTestResult(
                status = ConnectionTestStatus.FAILURE,
                title = "模型探测失败",
                detail = throwable.message?.ifBlank {
                    "后端可以访问，但模型调用没有成功，请检查当前模型配置。"
                } ?: "后端可以访问，但模型调用没有成功，请检查当前模型配置。",
            )
        }

        return interpretProbeReply(probe.reply)
    }
}

private fun bearer(token: String): String = "Bearer $token"

private fun interpretProbeReply(reply: String): ConnectionTestResult {
    val text = reply.trim()
    val lower = text.lowercase()
    return when {
        text.isBlank() -> ConnectionTestResult(
            status = ConnectionTestStatus.SUCCESS,
            title = "连接正常",
            detail = "后端和模型都已经连通，可以直接开始使用。",
        )

        lower.contains("api key")
            || text.contains("未配置模型")
            || text.contains("未配置") -> ConnectionTestResult(
            status = ConnectionTestStatus.FAILURE,
            title = "模型 Key 未配置",
            detail = text,
        )

        text.contains("模型")
            || text.contains("Base URL")
            || text.contains("超时")
            || text.contains("不可用")
            || text.contains("无权限")
            || text.contains("不可达")
            || text.contains("服务暂时不可用") -> ConnectionTestResult(
            status = ConnectionTestStatus.FAILURE,
            title = "模型不可用",
            detail = text,
        )

        else -> ConnectionTestResult(
            status = ConnectionTestStatus.SUCCESS,
            title = "连接正常",
            detail = text,
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
