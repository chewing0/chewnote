package com.example.myapplication.agent.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val category: String,
    val note: String,
    val date: String = LocalDate.now().toString(),
    val entryType: String = "expense",
    val createdAt: Long = System.currentTimeMillis(),
    val actionBatchId: String? = null,
)

@Entity(tableName = "schedule_items")
data class ScheduleItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String = LocalDate.now().toString(),
    val time: String = "09:00",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val actionBatchId: String? = null,
)

data class AgentRequest(
    val text: String,
    @SerializedName("session_id")
    val sessionId: String = "",
    val history: List<ChatMessagePayload> = emptyList(),
    @SerializedName("context_summary")
    val contextSummary: String = "",
    @SerializedName("summary_history")
    val summaryHistory: List<ChatMessagePayload> = emptyList(),
    @SerializedName("model_config")
    val modelConfig: ModelConfigPayload? = null,
)

data class ModelConfigPayload(
    @SerializedName("base_url")
    val baseUrl: String,
    val model: String,
    @SerializedName("api_key")
    val apiKey: String,
)

data class ModelSettings(
    val backendUrl: String = "",
    val modelBaseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
)

data class ChatMessagePayload(
    val role: String,
    val content: String,
)

data class ContextSnapshot(
    val summary: String = "",
    val summarizedMessageCount: Int = 0,
    val updatedAt: Long = 0L,
)

enum class ChatMessageKind {
    MESSAGE,
    ACTION_RECEIPT,
}

enum class ActionReceiptKind {
    LEDGER,
    SCHEDULE,
    MIXED,
}

enum class ReceiptActionTarget {
    LEDGER,
    SCHEDULE,
}

enum class LedgerPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

data class ActionReceipt(
    val batchId: String,
    val kind: ActionReceiptKind,
    val summary: String,
    val primaryAction: ReceiptActionTarget,
    val targetDate: String? = null,
    val period: LedgerPeriod? = null,
    val secondaryAction: ReceiptActionTarget? = null,
    val secondaryTargetDate: String? = null,
    val secondaryPeriod: LedgerPeriod? = null,
    val scheduleCount: Int = 0,
    val ledgerCount: Int = 0,
    val undone: Boolean = false,
)

data class ChatMessage(
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: ChatMessageKind = ChatMessageKind.MESSAGE,
    @SerializedName("action_receipt")
    val actionReceipt: ActionReceipt? = null,
)

data class AgentAction(
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
)

data class AgentResponse(
    val reply: String,
    val actions: List<AgentAction> = emptyList(),
    @SerializedName("context_summary")
    val contextSummary: String? = null,
    @SerializedName("changed_domains")
    val changedDomains: List<String> = emptyList(),
)

data class HealthResponse(
    val status: String,
)

data class SyncResponse(
    val schedules: List<ScheduleItem> = emptyList(),
    val ledgers: List<LedgerEntry> = emptyList(),
)

data class SyncImportRequest(
    val schedules: List<ScheduleItem> = emptyList(),
    val ledgers: List<LedgerEntry> = emptyList(),
)

data class DeleteResponse(
    val deleted: Int = 0,
)

data class AuthUser(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class AuthState(
    val user: AuthUser? = null,
    val accessToken: String = "",
    val refreshToken: String = "",
    val structuredCacheOwnerId: String = "",
) {
    val isLoggedIn: Boolean
        get() = user != null && accessToken.isNotBlank()
}

data class AuthResponse(
    val user: AuthUser,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 0,
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

data class LoginRequest(
    val identifier: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class ForgotPasswordResponse(
    val message: String,
    val devResetToken: String? = null,
)

data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

data class SimpleStatusResponse(
    val status: String = "ok",
)

enum class ConnectionTestStatus {
    IDLE,
    TESTING,
    SUCCESS,
    FAILURE,
}

data class ConnectionTestResult(
    val status: ConnectionTestStatus = ConnectionTestStatus.IDLE,
    val title: String = "",
    val detail: String = "",
)

object LedgerCategoryCatalog {
    const val OTHER = "其他"

    val expenseCategories = listOf(
        "餐饮",
        "交通",
        "购物",
        "住房",
        "日用",
        "医疗",
        "教育",
        "娱乐",
        "旅行",
        "人情",
        "宠物",
        OTHER,
    )

    val incomeCategories = listOf(
        "工资",
        "奖金",
        "副业",
        "报销",
        "理财",
        "礼金",
        "退款",
        OTHER,
    )

    fun categoriesFor(entryType: String): List<String> {
        return if (entryType == "income") incomeCategories else expenseCategories
    }

    fun normalizeCategory(raw: String, entryType: String): String {
        val value = raw.trim()
        if (value.isBlank()) return OTHER

        val normalized = value.lowercase()
        return if (entryType == "income") {
            when {
                normalized in setOf("工资", "薪资", "salary", "pay") -> "工资"
                normalized in setOf("奖金", "提成", "年终奖", "奖励", "bonus") -> "奖金"
                normalized in setOf("副业", "兼职", "外快", "freelance", "parttime") -> "副业"
                normalized in setOf("报销", "reimbursement") -> "报销"
                normalized in setOf("理财", "利息", "分红", "投资收益", "interest", "dividend") -> "理财"
                normalized in setOf("礼金", "红包", "赠与", "gift") -> "礼金"
                normalized in setOf("退款", "退货", "refund", "return") -> "退款"
                else -> OTHER
            }
        } else {
            when {
                normalized in setOf("餐饮", "餐费", "吃饭", "早餐", "午餐", "晚餐", "宵夜", "外卖", "奶茶", "咖啡", "零食") -> "餐饮"
                normalized in setOf("交通", "打车", "地铁", "公交", "高铁", "火车", "机票接驳", "油费", "停车", "过路费") -> "交通"
                normalized in setOf("购物", "买衣服", "衣服", "服饰", "数码", "网购", "购物消费") -> "购物"
                normalized in setOf("住房", "房租", "水电", "燃气", "物业", "宽带", "家居") -> "住房"
                normalized in setOf("日用", "超市", "生活用品", "日用品", "买菜", "百货") -> "日用"
                normalized in setOf("医疗", "医院", "买药", "药品", "体检", "看病") -> "医疗"
                normalized in setOf("教育", "课程", "培训", "学习", "书籍", "学费") -> "教育"
                normalized in setOf("娱乐", "休闲娱乐", "游戏", "电影", "ktv", "洗脚", "聚会", "玩乐") -> "娱乐"
                normalized in setOf("旅行", "旅游", "酒店", "度假", "景点", "出行") -> "旅行"
                normalized in setOf("人情", "礼物", "礼金", "红包", "随礼") -> "人情"
                normalized in setOf("宠物", "猫", "狗", "猫粮", "狗粮", "宠物医院") -> "宠物"
                else -> OTHER
            }
        }
    }
}
