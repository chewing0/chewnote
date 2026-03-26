package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.agent.AppViewModel
import com.example.myapplication.ui.screen.AgentHomeScreen
import com.example.myapplication.ui.screen.LedgerScreen
import com.example.myapplication.ui.screen.ScheduleScreen

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
                tint = if (selected) Color(0xFF20252A) else Color(0xFF6B7280)
            )
        },
        NavItem("agent", "交互") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF6B7280)
            )
        },
        NavItem("ledger", "记账") { selected ->
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = if (selected) Color(0xFF20252A) else Color(0xFF6B7280)
            )
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF6F0E5),
                        Color(0xFFEAE2D3)
                    )
                )
            ),
        containerColor = Color.Transparent,
        bottomBar = {
            if (hideBottomBar) {
                return@Scaffold
            }

            Surface(color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .background(Color(0xFFF7F1E8), shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    navItems.forEachIndexed { index, item ->
                        val selected = currentRoute == item.route
                        val isCenter = index == 1
                        val navigate = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                        if (isCenter) {
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable(onClick = navigate),
                                color = if (selected) Color(0xFF20252A) else Color(0xFFE5DED1),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                tonalElevation = if (selected) 6.dp else 0.dp
                            ) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    item.icon(selected)
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
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
                                        color = if (selected) Color(0xFF20252A) else Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "agent",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("agent") {
                AgentHomeScreen(viewModel)
            }
            composable("ledger") {
                LedgerScreen(viewModel)
            }
            composable("schedule") {
                ScheduleScreen(viewModel)
            }
        }
    }
}
