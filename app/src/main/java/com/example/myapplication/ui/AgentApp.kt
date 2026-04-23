package com.example.myapplication.ui

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.agent.model.LedgerPeriod
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
    val icon: @Composable (Boolean) -> Unit,
)

private object AgentRoutes {
    const val Agent = "agent"
    const val Settings = "settings"

    private const val ScheduleBase = "schedule"
    private const val LedgerBase = "ledger"

    const val SchedulePattern = "$ScheduleBase?targetDate={targetDate}&actionBatchId={actionBatchId}"
    const val LedgerPattern = "$LedgerBase?period={period}&actionBatchId={actionBatchId}"

    fun schedule(targetDate: String? = null, actionBatchId: String? = null): String {
        return buildString {
            append(ScheduleBase)
            append("?targetDate=")
            append(Uri.encode(targetDate ?: ""))
            append("&actionBatchId=")
            append(Uri.encode(actionBatchId ?: ""))
        }
    }

    fun ledger(period: LedgerPeriod = LedgerPeriod.MONTH, actionBatchId: String? = null): String {
        return buildString {
            append(LedgerBase)
            append("?period=")
            append(Uri.encode(period.name))
            append("&actionBatchId=")
            append(Uri.encode(actionBatchId ?: ""))
        }
    }

    fun rootRouteOf(route: String?): String? {
        return when {
            route == null -> null
            route.startsWith(ScheduleBase) -> ScheduleBase
            route.startsWith(LedgerBase) -> LedgerBase
            else -> route
        }
    }
}

@Composable
fun AgentApp() {
    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedRoute = AgentRoutes.rootRouteOf(currentRoute)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val hideBottomBar = selectedRoute == AgentRoutes.Agent && imeVisible

    val navItems = listOf(
        NavItem("schedule", "日程") { selected ->
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = if (selected) InkDeep else InkSoft,
            )
        },
        NavItem(AgentRoutes.Agent, "互动") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = if (selected) CanvasIvory else InkSoft,
            )
        },
        NavItem("ledger", "记账") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = if (selected) InkDeep else InkSoft,
            )
        },
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
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(targetScale = 0.96f),
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
                                        Color(0xFFF0E2CC),
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = LineSoft,
                                shape = RoundedCornerShape(26.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            navItems.forEachIndexed { index, item ->
                                val selected = selectedRoute == item.route
                                val isCenter = index == 1
                                val navigate = {
                                    val route = when (item.route) {
                                        "schedule" -> AgentRoutes.schedule()
                                        "ledger" -> AgentRoutes.ledger()
                                        else -> AgentRoutes.Agent
                                    }
                                    navigateTo(route)
                                }
                                val scale by animateFloatAsState(
                                    targetValue = if (selected) 1f else 0.96f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
                                    label = "nav-scale-${item.route}",
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (selected) InkDeep else InkSoft,
                                    animationSpec = spring(stiffness = 700f),
                                    label = "nav-text-${item.route}",
                                )

                                if (isCenter) {
                                    val containerColor by animateColorAsState(
                                        targetValue = if (selected) AccentVermilion else Color(0xFFE4D5BD),
                                        animationSpec = spring(stiffness = 700f),
                                        label = "nav-center-bg",
                                    )
                                    val elevation by animateDpAsState(
                                        targetValue = if (selected) 8.dp else 0.dp,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                                        label = "nav-center-elevation",
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
                                        shape = RoundedCornerShape(18.dp),
                                        tonalElevation = elevation,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
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
                                        color = Color.Transparent,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            item.icon(selected)
                                            Text(
                                                text = item.label,
                                                color = textColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        EditorialBackground {
            NavHost(
                navController = navController,
                startDestination = AgentRoutes.Agent,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(AgentRoutes.Agent) {
                    AgentHomeScreen(
                        viewModel = viewModel,
                        onOpenSettings = { navigateTo(AgentRoutes.Settings) },
                        onOpenSchedule = { targetDate, batchId ->
                            navigateTo(AgentRoutes.schedule(targetDate = targetDate, actionBatchId = batchId))
                        },
                        onOpenLedger = { period, batchId ->
                            navigateTo(AgentRoutes.ledger(period = period, actionBatchId = batchId))
                        },
                    )
                }
                composable(
                    route = AgentRoutes.SchedulePattern,
                    arguments = listOf(
                        navArgument("targetDate") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        },
                        navArgument("actionBatchId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    ScheduleScreen(
                        viewModel = viewModel,
                        targetDate = entry.arguments?.getString("targetDate").orEmpty().ifBlank { null },
                        highlightBatchId = entry.arguments?.getString("actionBatchId").orEmpty().ifBlank { null },
                    )
                }
                composable(
                    route = AgentRoutes.LedgerPattern,
                    arguments = listOf(
                        navArgument("period") {
                            type = NavType.StringType
                            defaultValue = LedgerPeriod.MONTH.name
                        },
                        navArgument("actionBatchId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val initialPeriod = runCatching {
                        LedgerPeriod.valueOf(entry.arguments?.getString("period") ?: LedgerPeriod.MONTH.name)
                    }.getOrDefault(LedgerPeriod.MONTH)
                    LedgerScreen(
                        viewModel = viewModel,
                        initialPeriod = initialPeriod,
                        highlightBatchId = entry.arguments?.getString("actionBatchId").orEmpty().ifBlank { null },
                    )
                }
                composable(AgentRoutes.Settings) {
                    SettingsScreen(viewModel)
                }
            }
        }
    }
}
