package com.example.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.ui.design.EditorialBackground
import com.example.myapplication.ui.screen.AgentHomeScreen
import com.example.myapplication.ui.screen.LedgerScreen
import com.example.myapplication.ui.screen.ScheduleScreen
import com.example.myapplication.ui.screen.SettingsScreen
import com.example.myapplication.ui.theme.AccentVermilion
import com.example.myapplication.ui.theme.CanvasIvory
import com.example.myapplication.ui.theme.InkDeep
import com.example.myapplication.ui.theme.InkSoft
import com.example.myapplication.ui.theme.LineSoft

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable (Boolean) -> Unit
)

@Composable
fun AgentApp() {
    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val hideBottomBar = currentRoute == "agent" && imeVisible

    val navItems = listOf(
        NavItem("schedule", "日程") { selected ->
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = if (selected) InkDeep else InkSoft
            )
        },
        NavItem("agent", "交互") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = if (selected) CanvasIvory else InkSoft
            )
        },
        NavItem("ledger", "记账") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = if (selected) InkDeep else InkSoft
            )
        }
    )

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(targetScale = 0.96f)
            ) {
                Surface(color = Color.Transparent) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFFF8EC),
                                        Color(0xFFF0E2CC)
                                    )
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = LineSoft,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            navItems.forEachIndexed { index, item ->
                                val selected = currentRoute == item.route
                                val isCenter = index == 1
                                val navigate = { navigateTo(item.route) }
                                val scale by animateFloatAsState(
                                    targetValue = if (selected) 1f else 0.96f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
                                    label = "nav-scale-${item.route}"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (selected) InkDeep else InkSoft,
                                    animationSpec = spring(stiffness = 700f),
                                    label = "nav-text-${item.route}"
                                )

                                if (isCenter) {
                                    val containerColor by animateColorAsState(
                                        targetValue = if (selected) AccentVermilion else Color(0xFFE4D5BD),
                                        animationSpec = spring(stiffness = 700f),
                                        label = "nav-center-bg"
                                    )
                                    val elevation by animateDpAsState(
                                        targetValue = if (selected) 8.dp else 0.dp,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                                        label = "nav-center-elevation"
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clickable(onClick = navigate),
                                        color = containerColor,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                        tonalElevation = elevation
                                    ) {
                                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            item.icon(selected)
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clickable(onClick = navigate),
                                        color = Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            item.icon(selected)
                                            Text(
                                                text = item.label,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        EditorialBackground {
            NavHost(
                navController = navController,
                startDestination = "agent",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("agent") {
                    AgentHomeScreen(
                        viewModel = viewModel,
                        onOpenSettings = { navigateTo("settings") }
                    )
                }
                composable("ledger") {
                    LedgerScreen(viewModel)
                }
                composable("schedule") {
                    ScheduleScreen(viewModel)
                }
                composable("settings") {
                    SettingsScreen(viewModel)
                }
            }
        }
    }
}
