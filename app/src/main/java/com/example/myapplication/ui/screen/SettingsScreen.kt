package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.ModelSettings
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.EditorialTitle
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkSoft

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.modelSettings.collectAsState()

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

    EditorialBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditorialReveal(delayMillis = 0) {
                EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                    EditorialTitle(
                        title = "设置",
                        subtitle = "管理后端地址与模型参数",
                        modifier = Modifier.padding(14.dp),
                        trailing = {
                            TonePill(text = "模型配置", tone = AccentVermilion)
                        }
                    )
                }
            }

            EditorialReveal(delayMillis = 80) {
                EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("后端 URL") },
                            supportingText = { Text("模拟器请用 10.0.2.2:8000；真机请用电脑局域网 IP") }
                        )

                        OutlinedTextField(
                            value = modelBaseUrl,
                            onValueChange = { modelBaseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型 Base URL") },
                            supportingText = { Text("例如: https://api.moonshot.cn/v1") }
                        )

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("基准模型") },
                            supportingText = { Text("例如: moonshot-v1-8k") }
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = { Text("仅保存在本机 DataStore") }
                        )

                        Button(
                            onClick = {
                                viewModel.saveModelSettings(
                                    ModelSettings(
                                        backendUrl = backendUrl.trim(),
                                        modelBaseUrl = modelBaseUrl.trim(),
                                        modelName = modelName.trim(),
                                        apiKey = apiKey.trim()
                                    )
                                )
                                saveTip = "已保存，下次请求立即生效"
                            }
                        ) {
                            Text("保存设置")
                        }

                        if (saveTip.isNotBlank()) {
                            Text(
                                text = saveTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}
