package com.example.myapplication.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.AuthState
import com.example.myapplication.agent.model.BackendModelStatus
import com.example.myapplication.agent.model.ConnectionTestResult
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.net.NetworkModule
import com.example.myapplication.ui.theme.AccentMoss
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft

private enum class ProfilePage {
    Home,
    Account,
    Settings,
    Sync,
}

private val ProfileBackground = Color(0xFFF2F3F5)
private val ProfileCard = Color(0xFFFFFFFF)
private val ProfileBlack = Color(0xFF1D1D1B)
private val ProfileMutedIcon = Color(0xFF8B8B88)
private val ProfileDivider = Color(0xFFE8E8E4)

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.modelSettings.collectAsState()
    val connectionResult by viewModel.connectionTestResult.collectAsState()
    val backendModelStatus by viewModel.backendModelStatus.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val accountState by viewModel.accountUiState.collectAsState()

    var backendUrl by remember { mutableStateOf(settings.backendUrl) }
    var saveTip by remember { mutableStateOf("") }
    var accountMode by remember { mutableStateOf("login") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(ProfilePage.Home) }

    LaunchedEffect(settings) {
        backendUrl = settings.backendUrl
    }

    LaunchedEffect(settings.backendUrl) {
        viewModel.refreshBackendModelStatus(settings)
    }

    if (currentPage != ProfilePage.Home) {
        BackHandler { currentPage = ProfilePage.Home }
    }

    val candidateSettings = ModelSettings(
        backendUrl = backendUrl.trim(),
    )

    if (accountState.pendingSyncUserId.isNotBlank()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("同步本机缓存") },
            text = { Text("检测到本机已有日程或记账缓存。可以上传合并到当前账号，也可以清空本机缓存后只使用云端数据。") },
            confirmButton = {
                Button(onClick = { viewModel.resolvePendingSync(uploadLocal = true) }) {
                    Text("上传并合并")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolvePendingSync(uploadLocal = false) }) {
                    Text("清空后同步")
                }
            },
        )
    }

    when (currentPage) {
        ProfilePage.Home -> ProfileHomePage(
            authState = authState,
            backendUrl = NetworkModule.resolveBackendUrl(candidateSettings.backendUrl),
            backendModelStatus = backendModelStatus,
            onAccountClick = { currentPage = ProfilePage.Account },
            onSettingsClick = { currentPage = ProfilePage.Settings },
            onSyncClick = { currentPage = ProfilePage.Sync },
        )

        ProfilePage.Account -> ProfileSubPage(
            title = "账号管理",
            subtitle = if (authState.isLoggedIn) "管理登录状态和密码" else "登录后同步日程和记账",
            onBack = { currentPage = ProfilePage.Home },
        ) {
            AccountPanel(
                authState = authState,
                loading = accountState.loading,
                error = accountState.error,
                notice = accountState.notice,
                mode = accountMode,
                onModeChange = { accountMode = it },
                username = username,
                onUsernameChange = { username = it },
                email = email,
                onEmailChange = { email = it },
                identifier = identifier,
                onIdentifierChange = { identifier = it },
                password = password,
                onPasswordChange = { password = it },
                resetToken = resetToken,
                onResetTokenChange = { resetToken = it },
                newPassword = newPassword,
                onNewPasswordChange = { newPassword = it },
                oldPassword = oldPassword,
                onOldPasswordChange = { oldPassword = it },
                onLogin = {
                    viewModel.loginAccount(identifier, password)
                    password = ""
                },
                onRegister = {
                    viewModel.registerAccount(username, email, password)
                    password = ""
                },
                onForgotPassword = { viewModel.forgotPassword(email) },
                onResetPassword = {
                    viewModel.resetPassword(resetToken, newPassword)
                    newPassword = ""
                },
                onChangePassword = {
                    viewModel.changePassword(oldPassword, newPassword)
                    oldPassword = ""
                    newPassword = ""
                },
                onLogout = { viewModel.logoutAccount() },
            )
        }

        ProfilePage.Settings -> ProfileSubPage(
            title = "前端连接",
            subtitle = "仅配置后端地址和连接测试",
            onBack = { currentPage = ProfilePage.Home },
        ) {
            ModelSettingsPanel(
                backendUrl = backendUrl,
                onBackendUrlChange = {
                    backendUrl = it
                    saveTip = ""
                    viewModel.clearConnectionTestResult()
                },
                candidateSettings = candidateSettings,
                backendModelStatus = backendModelStatus,
                saveTip = saveTip,
                connectionResult = connectionResult,
                onSave = {
                    viewModel.saveModelSettings(candidateSettings)
                    saveTip = "后端地址已保存。模型配置统一使用后端 .env，不再由前端覆盖。"
                },
                onTest = { viewModel.testModelConnection(candidateSettings) },
                onRefreshModelStatus = { viewModel.refreshBackendModelStatus(candidateSettings) },
            )
        }

        ProfilePage.Sync -> ProfileSubPage(
            title = "数据同步",
            subtitle = "查看当前结构化数据同步状态",
            onBack = { currentPage = ProfilePage.Home },
        ) {
            SyncPanel(authState = authState)
        }
    }
}

@Composable
private fun ProfileHomePage(
    authState: AuthState,
    backendUrl: String,
    backendModelStatus: BackendModelStatus?,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileHeaderCard(authState = authState)

            ProfileGroupCard {
                ProfileRow(
                    icon = Icons.Filled.AccountCircle,
                    title = "账号管理",
                    subtitle = if (authState.isLoggedIn) {
                        "${authState.user?.username.orEmpty()} · 已登录"
                    } else {
                        "登录、注册、找回密码"
                    },
                    onClick = onAccountClick,
                )
                ProfileDividerLine()
                ProfileRow(
                    icon = Icons.Filled.Settings,
                    title = "模型与后端",
                    subtitle = "前端只配置服务地址，模型由后端统一管理",
                    onClick = onSettingsClick,
                )
                ProfileDividerLine()
                ProfileRow(
                    icon = Icons.Filled.Sync,
                    title = "数据同步",
                    subtitle = if (authState.isLoggedIn) "日程和记账已按账号同步" else "登录后启用云端同步",
                    onClick = onSyncClick,
                )
            }

            ProfileGroupCard {
                SummaryRow(label = "后端地址", value = backendUrl)
                ProfileDividerLine()
                SummaryRow(label = "后端模型", value = backendModelStatus?.model?.ifBlank { "未读取" } ?: "未读取")
                ProfileDividerLine()
                SummaryRow(
                    label = "同步范围",
                    value = "对话、日程、记账、Agent 操作结果",
                )
                ProfileDividerLine()
                SummaryRow(
                    label = "本机保留",
                    value = "后端地址、登录令牌、只读缓存",
                )
            }

            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun ProfileHeaderCard(authState: AuthState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ProfileCard,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (authState.isLoggedIn) AccentMoss.copy(alpha = 0.14f) else AccentVermilion.copy(alpha = 0.1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = if (authState.isLoggedIn) AccentMoss else AccentVermilion,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(30.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (authState.isLoggedIn) authState.user?.username.orEmpty() else "未登录",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    ),
                    color = InkDeep,
                )
                Text(
                    text = if (authState.isLoggedIn) {
                        authState.user?.email.orEmpty()
                    } else {
                        "登录后可以在多设备访问同一份日程和记账数据"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                    color = InkSoft,
                )
            }
        }
    }
}

@Composable
private fun ProfileSubPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.clickable(onClick = onBack),
                    color = ProfileCard,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = InkDeep,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                        ),
                        color = InkDeep,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                        color = InkSoft,
                    )
                }
            }

            ProfileContentCard(content = content)
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun ProfileGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ProfileCard,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun ProfileContentCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ProfileCard,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProfileMutedIcon,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
                color = InkDeep,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                color = InkSoft,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = ProfileMutedIcon,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.78f),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            ),
            color = InkDeep,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.22f),
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            color = InkSoft,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ProfileDividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        thickness = 1.dp,
        color = ProfileDivider,
    )
}

@Composable
private fun AccountPanel(
    authState: AuthState,
    loading: Boolean,
    error: String?,
    notice: String,
    mode: String,
    onModeChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    resetToken: String,
    onResetTokenChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    oldPassword: String,
    onOldPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onResetPassword: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusStrip(
            text = if (authState.isLoggedIn) {
                "${authState.user?.username.orEmpty()} · ${authState.user?.email.orEmpty()}"
            } else {
                "未登录：日程和记账页面会以本机缓存只读展示"
            },
            tone = if (authState.isLoggedIn) AccentMoss else AccentVermilion,
        )

        if (authState.isLoggedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onModeChange("change") }) {
                    Text("修改密码")
                }
                Button(onClick = onLogout, enabled = !loading) {
                    Text("退出登录")
                }
            }
            AnimatedVisibility(
                visible = mode == "change",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = onOldPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("旧密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = onNewPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = onChangePassword, enabled = !loading && oldPassword.isNotBlank() && newPassword.length >= 8) {
                        Text(if (loading) "处理中..." else "确认修改")
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("登录", mode == "login") { onModeChange("login") }
                ModeButton("注册", mode == "register") { onModeChange("register") }
                ModeButton("找回", mode == "forgot") { onModeChange("forgot") }
            }

            when (mode) {
                "register" -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("用户名") },
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱") },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = onRegister,
                        enabled = !loading && username.isNotBlank() && email.isNotBlank() && password.length >= 8,
                    ) {
                        Text(if (loading) "注册中..." else "注册并登录")
                    }
                }

                "forgot" -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("注册邮箱") },
                    )
                    Button(onClick = onForgotPassword, enabled = !loading && email.isNotBlank()) {
                        Text(if (loading) "发送中..." else "获取重置 Token")
                    }
                    OutlinedTextField(
                        value = resetToken,
                        onValueChange = onResetTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("重置 Token") },
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = onNewPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = onResetPassword, enabled = !loading && resetToken.isNotBlank() && newPassword.length >= 8) {
                        Text("重置密码")
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = onIdentifierChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("用户名或邮箱") },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = onLogin, enabled = !loading && identifier.isNotBlank() && password.isNotBlank()) {
                        Text(if (loading) "登录中..." else "登录")
                    }
                }
            }
        }

        AnimatedVisibility(visible = !error.isNullOrBlank()) {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = AccentVermilion)
        }
        AnimatedVisibility(visible = notice.isNotBlank()) {
            Text(notice, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun ModelSettingsPanel(
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    candidateSettings: ModelSettings,
    backendModelStatus: BackendModelStatus?,
    saveTip: String,
    connectionResult: ConnectionTestResult,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRefreshModelStatus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionTitle(
            title = "前端连接",
            subtitle = "这里只决定 App 请求哪个后端服务，不再配置模型 Key。",
        )

        OutlinedTextField(
            value = backendUrl,
            onValueChange = onBackendUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("后端 URL") },
            placeholder = { Text("默认: http://10.0.2.2:8000/") },
            supportingText = { Text("留空则使用默认联调地址。") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) {
                Text("保存后端地址")
            }
            Button(
                onClick = onTest,
                enabled = connectionResult.status != ConnectionTestStatus.TESTING,
            ) {
                Text(if (connectionResult.status == ConnectionTestStatus.TESTING) "测试中..." else "测试连接")
            }
        }

        ConfigSummaryBlock(
            backendModelStatus = backendModelStatus,
            onRefresh = onRefreshModelStatus,
        )

        AnimatedVisibility(
            visible = saveTip.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(text = saveTip, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }

        AnimatedVisibility(
            visible = connectionResult.status != ConnectionTestStatus.IDLE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ConnectionResultPanel(
                title = connectionResult.title,
                detail = connectionResult.detail,
                status = connectionResult.status,
            )
        }
    }
}

@Composable
private fun ConfigSummaryBlock(
    backendModelStatus: BackendModelStatus?,
    onRefresh: () -> Unit,
) {
    Surface(
        color = Color(0xFFF7F4EC),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("后端统一模型配置", style = MaterialTheme.typography.titleSmall, color = InkDeep)
            Text(
                "模型 Base URL、模型名称和 API Key 只从后端 .env 读取；前端这里只设置后端 URL，避免出现两个后端地址。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            SummaryLine(label = "模型来源", value = "后端 .env")
            SummaryLine(label = "Base URL", value = backendModelStatus?.baseUrl?.ifBlank { "未读取" } ?: "未读取")
            SummaryLine(label = "模型名称", value = backendModelStatus?.model?.ifBlank { "未读取" } ?: "未读取")
            SummaryLine(label = "API Key", value = if (backendModelStatus?.apiKeyConfigured == true) "后端已配置" else "后端未配置或未读取")
            OutlinedButton(onClick = onRefresh) {
                Text("刷新后端模型状态")
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = InkDeep,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
    }
}

@Composable
private fun SyncPanel(authState: AuthState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusStrip(
            text = if (authState.isLoggedIn) {
                "当前账号：${authState.user?.username.orEmpty()}。日程和记账由后端 PostgreSQL 统一存储。"
            } else {
                "未登录时不会写入云端；日程和记账页面只展示本机缓存。"
            },
            tone = if (authState.isLoggedIn) AccentMoss else AccentVermilion,
        )
        SummaryLine(label = "云端同步", value = if (authState.isLoggedIn) "已启用" else "未启用")
        SummaryLine(label = "同步数据", value = "对话、日程、记账、Agent CRUD 结果")
        SummaryLine(label = "本机数据", value = "后端地址、登录令牌、只读缓存")
    }
}

@Composable
private fun StatusStrip(text: String, tone: Color) {
    Surface(
        color = tone.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            color = InkDeep,
        )
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = ProfileBlack,
                contentColor = Color.White,
            ),
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.82f),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.18f),
            style = MaterialTheme.typography.bodySmall,
            color = InkDeep,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ConnectionResultPanel(
    title: String,
    detail: String,
    status: ConnectionTestStatus,
) {
    val tone = when (status) {
        ConnectionTestStatus.SUCCESS -> AccentMoss
        ConnectionTestStatus.FAILURE -> AccentVermilion
        ConnectionTestStatus.TESTING -> InkSoft
        ConnectionTestStatus.IDLE -> InkSoft
    }
    Surface(
        color = tone.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = when (status) {
                    ConnectionTestStatus.SUCCESS -> "连接正常"
                    ConnectionTestStatus.FAILURE -> "连接失败"
                    ConnectionTestStatus.TESTING -> "测试中"
                    ConnectionTestStatus.IDLE -> ""
                },
                style = MaterialTheme.typography.labelLarge,
                color = tone,
            )
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = InkDeep)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}
