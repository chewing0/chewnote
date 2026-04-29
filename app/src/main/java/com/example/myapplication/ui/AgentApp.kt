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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
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
import com.example.myapplication.ui.screen.PendingScreen
import com.example.myapplication.ui.screen.ScheduleScreen
import com.example.myapplication.ui.screen.SettingsScreen
import com.example.myapplication.ui.theme.InkDeep

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable (Boolean) -> Unit,
)

private object AgentRoutes {
    const val Agent = "agent"
    const val Pending = "pending"
    const val Profile = "profile"
    const val Schedule = "schedule"
    const val Ledger = "ledger"

    const val SchedulePattern = "$Schedule?targetDate={targetDate}&actionBatchId={actionBatchId}"
    const val LedgerPattern = "$Ledger?period={period}&actionBatchId={actionBatchId}"

    fun schedule(targetDate: String? = null, actionBatchId: String? = null): String {
        return buildString {
            append(Schedule)
            append("?targetDate=")
            append(Uri.encode(targetDate ?: ""))
            append("&actionBatchId=")
            append(Uri.encode(actionBatchId ?: ""))
        }
    }

    fun ledger(period: LedgerPeriod = LedgerPeriod.MONTH, actionBatchId: String? = null): String {
        return buildString {
            append(Ledger)
            append("?period=")
            append(Uri.encode(period.name))
            append("&actionBatchId=")
            append(Uri.encode(actionBatchId ?: ""))
        }
    }

    fun rootRouteOf(route: String?): String? {
        return when {
            route == null -> null
            route.startsWith(Schedule) -> Schedule
            route.startsWith(Ledger) -> Ledger
            else -> route
        }
    }

    fun targetRouteOf(rootRoute: String): String {
        return when (rootRoute) {
            Schedule -> schedule()
            Ledger -> ledger()
            Agent -> Agent
            Pending -> Pending
            Profile -> Profile
            else -> Agent
        }
    }
}

@Composable
private fun ConstructivistBottomBar(
    navItems: List<NavItem>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Surface(color = Color.Transparent) {
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp, vertical = 14.dp)
                .fillMaxWidth()
                .height(66.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF8F8F8), Color(0xFFF0F0F0)),
                    ),
                    shape = RoundedCornerShape(34.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { item ->
                val selected = selectedRoute == item.route
                val isCenter = item.route == AgentRoutes.Agent
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
                    label = "nav-scale-${item.route}",
                )

                if (isCenter) {
                    val elevation by animateDpAsState(
                        targetValue = if (selected) 8.dp else 5.dp,
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                        label = "nav-center-elevation",
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .offset(y = (-8).dp)
                                .size(58.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clickable { onNavigate(item.route) },
                            color = Color(0xFF1F1F1F),
                            shape = RoundedCornerShape(29.dp),
                            tonalElevation = elevation,
                            shadowElevation = elevation,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                item.icon(selected)
                            }
                        }
                    }
                } else {
                    val textColor by animateColorAsState(
                        targetValue = if (selected) InkDeep else Color(0xFF6F6F6F),
                        animationSpec = spring(stiffness = 700f),
                        label = "nav-text-${item.route}",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable { onNavigate(item.route) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item.icon(selected)
                        Text(
                            text = item.label,
                            color = textColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
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
        NavItem(AgentRoutes.Schedule, "日程") { selected ->
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = if (selected) InkDeep else Color(0xFF6F6F6F),
                modifier = Modifier.size(25.dp),
            )
        },
        NavItem(AgentRoutes.Ledger, "记账") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = if (selected) InkDeep else Color(0xFF6F6F6F),
                modifier = Modifier.size(25.dp),
            )
        },
        NavItem(AgentRoutes.Agent, "聊天") { _ ->
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(25.dp),
            )
        },
        NavItem(AgentRoutes.Pending, "暂定") { selected ->
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = null,
                tint = if (selected) InkDeep else Color(0xFF6F6F6F),
                modifier = Modifier.size(25.dp),
            )
        },
        NavItem(AgentRoutes.Profile, "我的") { selected ->
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = if (selected) InkDeep else Color(0xFF6F6F6F),
                modifier = Modifier.size(25.dp),
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
                ConstructivistBottomBar(
                    navItems = navItems,
                    selectedRoute = selectedRoute,
                    onNavigate = { route -> navigateTo(AgentRoutes.targetRouteOf(route)) },
                )
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
                composable(AgentRoutes.Pending) {
                    PendingScreen()
                }
                composable(AgentRoutes.Profile) {
                    SettingsScreen(viewModel)
                }
            }
        }
    }
}
