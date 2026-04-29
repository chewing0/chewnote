package com.example.myapplication.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.design.EditorialPanel
import com.example.myapplication.ui.design.EditorialReveal
import com.example.myapplication.ui.design.EditorialTitle
import com.example.myapplication.ui.design.TonePill
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import com.example.myapplication.ui.theme.LineSoft

@Composable
fun PendingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditorialReveal {
            EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                EditorialTitle(
                    title = "暂定",
                    subtitle = "预留给下一阶段功能的入口",
                    modifier = Modifier.padding(12.dp),
                    trailing = {
                        TonePill(text = "PENDING", tone = AccentVermilion)
                    },
                )
            }
        }

        EditorialReveal(delayMillis = 70) {
            EditorialPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1418191D), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "后续可以承载分析、资产、提醒或实验功能。",
                            color = InkDeep,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "当前版本只保留入口，不影响聊天、日程和记账流程。",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LineSoft.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
