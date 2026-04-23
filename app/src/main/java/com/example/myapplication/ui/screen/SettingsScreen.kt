package com.example.myapplication.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ConnectionTestStatus
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.agent.net.NetworkModule
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.EditorialTitle
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentMoss
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.modelSettings.collectAsState()
    val connectionResult by viewModel.connectionTestResult.collectAsState()

    var backendUrl by remember { mutableStateOf(settings.backendUrl) }
    var modelBaseUrl by remember { mutableStateOf(settings.modelBaseUrl) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var saveTip by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        backendUrl = settings.backendUrl
        modelBaseUrl = settings.modelBaseUrl
        modelName = settings.modelName
        apiKey = settings.apiKey
    }

    val candidateSettings = ModelSettings(
        backendUrl = backendUrl.trim(),
        modelBaseUrl = modelBaseUrl.trim(),
        modelName = modelName.trim(),
        apiKey = apiKey.trim(),
    )
    val usesLocalModelConfig = candidateSettings.modelBaseUrl.isNotBlank()
        || candidateSettings.modelName.isNotBlank()
        || candidateSettings.apiKey.isNotBlank()

    EditorialBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorialReveal(delayMillis = 0) {
                EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                    EditorialTitle(
                        title = "设置",
                        subtitle = "管理后端地址、模型来源和连接信心反馈",
                        modifier = Modifier.padding(14.dp),
                        trailing = {
                            TonePill(
                                text = if (usesLocalModelConfig) "本地模型配置" else "走后端 .env",
                                tone = AccentVermilion,
                            )
                        },
                    )
                }
            }

            EditorialReveal(delayMillis = 60) {
                ConfigSummaryCard(
                    backendUrl = NetworkModule.resolveBackendUrl(candidateSettings.backendUrl),
                    usesLocalModelConfig = usesLocalModelConfig,
                    modelBaseUrlSource = if (candidateSettings.modelBaseUrl.isBlank()) "后端 .env" else "本地输入",
                    modelNameSource = if (candidateSettings.modelName.isBlank()) "后端 .env" else "本地输入",
                    apiKeySource = if (candidateSettings.apiKey.isBlank()) "后端 .env" else "本地输入",
                )
            }

            EditorialReveal(delayMillis = 80) {
                EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = 760f,
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = {
                                backendUrl = it
                                saveTip = ""
                                viewModel.clearConnectionTestResult()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("后端 URL") },
                            placeholder = { Text("默认: http://10.0.2.2:8000/") },
                            supportingText = {
                                Text("留空则使用默认联调地址；填写后使用你输入的地址。")
                            },
                        )

                        OutlinedTextField(
                            value = modelBaseUrl,
                            onValueChange = {
                                modelBaseUrl = it
                                saveTip = ""
                                viewModel.clearConnectionTestResult()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型 Base URL") },
                            placeholder = { Text("例如: https://api.siliconflow.cn/v1") },
                            supportingText = {
                                Text("留空则继续使用后端 .env 中的 OPENAI_BASE_URL。")
                            },
                        )

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = {
                                modelName = it
                                saveTip = ""
                                viewModel.clearConnectionTestResult()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型名称") },
                            placeholder = { Text("例如: Pro/MiniMaxAI/MiniMax-M2.5") },
                            supportingText = {
                                Text("留空则继续使用后端 .env 中的 OPENAI_MODEL。")
                            },
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                saveTip = ""
                                viewModel.clearConnectionTestResult()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = { Text("例如: sk-...") },
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = {
                                Text("留空则继续使用后端 .env 中的 OPENAI_API_KEY；填写后会加密保存在本机。")
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveModelSettings(candidateSettings)
                                    saveTip = "设置已保存，新的配置会在下一次请求时生效。"
                                },
                            ) {
                                Text("保存设置")
                            }

                            Button(
                                onClick = { viewModel.testModelConnection(candidateSettings) },
                                enabled = connectionResult.status != ConnectionTestStatus.TESTING,
                            ) {
                                Text(if (connectionResult.status == ConnectionTestStatus.TESTING) "测试中..." else "测试连接")
                            }
                        }

                        AnimatedVisibility(
                            visible = saveTip.isNotBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Text(
                                text = saveTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = connectionResult.status != ConnectionTestStatus.IDLE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ConnectionResultCard(
                    title = connectionResult.title,
                    detail = connectionResult.detail,
                    status = connectionResult.status,
                )
            }
        }
    }
}

@Composable
private fun ConfigSummaryCard(
    backendUrl: String,
    usesLocalModelConfig: Boolean,
    modelBaseUrlSource: String,
    modelNameSource: String,
    apiKeySource: String,
) {
    Card {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "当前生效摘要",
                style = MaterialTheme.typography.titleMedium,
                color = InkDeep,
            )
            SummaryLine(label = "后端地址", value = backendUrl)
            SummaryLine(label = "模型来源", value = if (usesLocalModelConfig) "本地输入优先" else "完全使用后端 .env")
            SummaryLine(label = "Base URL", value = modelBaseUrlSource)
            SummaryLine(label = "模型名称", value = modelNameSource)
            SummaryLine(label = "API Key", value = apiKeySource)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = InkSoft)
        Text(text = value, color = InkDeep)
    }
}

@Composable
private fun ConnectionResultCard(
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
        color = Color(0xFFFFF7EA),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TonePill(
                text = when (status) {
                    ConnectionTestStatus.SUCCESS -> "连接正常"
                    ConnectionTestStatus.FAILURE -> "连接失败"
                    ConnectionTestStatus.TESTING -> "测试中"
                    ConnectionTestStatus.IDLE -> ""
                },
                tone = tone,
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = InkDeep)
            Text(text = detail, color = InkSoft)
        }
    }
}
