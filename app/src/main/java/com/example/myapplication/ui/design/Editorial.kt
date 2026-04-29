package com.example.myapplication.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AccentMoss
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.CanvasIvory
import com.example.myapplication.ui.theme.CanvasWarm
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import com.example.myapplication.ui.theme.LineSoft
import com.example.myapplication.ui.theme.PaperCard
import kotlinx.coroutines.delay

@Composable
fun EditorialBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasIvory)
            .drawBehind {
                val rightSlab = Path().apply {
                    moveTo(size.width * 0.70f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width * 0.78f, size.height)
                    lineTo(size.width * 0.48f, size.height)
                    close()
                }
                val leftWedge = Path().apply {
                    moveTo(0f, size.height * 0.10f)
                    lineTo(size.width * 0.26f, 0f)
                    lineTo(size.width * 0.14f, size.height * 0.34f)
                    lineTo(0f, size.height * 0.44f)
                    close()
                }
                drawPath(rightSlab, CanvasWarm.copy(alpha = 0.42f))
                drawPath(leftWedge, AccentVermilion.copy(alpha = 0.06f))
                drawLine(
                    color = AccentVermilion.copy(alpha = 0.24f),
                    start = Offset(size.width * 0.08f, size.height * 0.05f),
                    end = Offset(size.width * 0.92f, size.height * 0.18f),
                    strokeWidth = 3f,
                )
                drawLine(
                    color = AccentMoss.copy(alpha = 0.20f),
                    start = Offset(size.width * 0.14f, size.height),
                    end = Offset(size.width * 0.86f, 0f),
                    strokeWidth = 1.4f,
                )
            }
    ) {
        content()
    }
}

@Composable
fun EditorialTitle(
    title: String,
    subtitle: String = "",
    showSubtitle: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = InkDeep
            )
            if (subtitle.isNotBlank() && showSubtitle) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EditorialPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.drawBehind {
            val strokeWidth = 1.dp.toPx()
            drawLine(
                color = AccentVermilion.copy(alpha = 0.36f),
                start = Offset(0f, strokeWidth),
                end = Offset(size.width * 0.38f, strokeWidth),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = AccentMoss.copy(alpha = 0.16f),
                start = Offset(0f, size.height - strokeWidth),
                end = Offset(size.width, size.height - strokeWidth),
                strokeWidth = strokeWidth,
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PaperCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft.copy(alpha = 0.38f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun TonePill(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = tone.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(1.dp, tone.copy(alpha = 0.34f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = tone,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun EditorialReveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        visible = true
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 7 },
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 650f
                )
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = spring(
                    dampingRatio = 0.95f,
                    stiffness = 700f
                )
            )
    ) {
        content()
    }
}
