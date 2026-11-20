package com.forgeprom.studio.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.ui.screens.AboutScreen
import com.forgeprom.studio.ui.screens.AnalysisScreen
import com.forgeprom.studio.ui.screens.CreateScreen
import com.forgeprom.studio.ui.screens.DashboardScreen
import com.forgeprom.studio.ui.screens.EditorScreen
import com.forgeprom.studio.ui.screens.ImageScreen
import com.forgeprom.studio.ui.screens.ModelsScreen
import com.forgeprom.studio.ui.screens.ProjectDetailScreen
import com.forgeprom.studio.ui.screens.ProjectsScreen
import com.forgeprom.studio.ui.screens.ResultScreen
import com.forgeprom.studio.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Destinations.DASHBOARD, "الرئيسية", Icons.Filled.Home),
    Tab(Destinations.CREATE, "توليد", Icons.Filled.Create),
    Tab(Destinations.PROJECTS, "المشاريع", Icons.Filled.List),
    Tab(Destinations.MODELS, "المحركات", Icons.Filled.Build),
    Tab(Destinations.SETTINGS, "الإعدادات", Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(container: AppContainer) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    fun select(route: String) {
        if (currentRoute == route) return
        nav.navigate(route) {
            popUpTo(Destinations.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val detailTitle = when (currentRoute) {
        Destinations.ANALYSIS -> "التحليل"
        Destinations.RESULT -> "النتيجة"
        Destinations.IMAGE -> "الصورة"
        Destinations.PROJECT_DETAIL -> "تفاصيل المشروع"
        Destinations.EDITOR -> "محرّر الموجّه"
        Destinations.ABOUT -> "حول التطبيق"
        else -> null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxHeight >= 500.dp
        Scaffold(
            topBar = {
                if (detailTitle != null) TopAppBar(title = { Text(detailTitle) }, navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                })
            },
            bottomBar = {
                if (!wide) NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { select(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = { select(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    NavHost(
                        navController = nav,
                        startDestination = Destinations.DASHBOARD,
                        modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()
                    ) {
                        composable(Destinations.DASHBOARD) {
                            DashboardScreen(
                                container = container,
                                onCreate = { nav.navigate(Destinations.CREATE) },
                                onProjects = { nav.navigate(Destinations.PROJECTS) },
                                onImage = { nav.navigate(Destinations.IMAGE) },
                                onAbout = { nav.navigate(Destinations.ABOUT) }
                            )
                        }
                        composable(Destinations.CREATE) {
                            CreateScreen(container = container, onAnalyzed = { nav.navigate(Destinations.ANALYSIS) })
                        }
                        composable(Destinations.ANALYSIS) {
                            AnalysisScreen(container = container, onApproved = { nav.navigate(Destinations.RESULT) })
                        }
                        composable(Destinations.RESULT) {
                            ResultScreen(container = container, onSaved = { id -> nav.navigate(Destinations.projectDetail(id)) })
                        }
                        composable(Destinations.IMAGE) {
                            ImageScreen(container = container, onUseAnalysis = { nav.navigate(Destinations.ANALYSIS) })
                        }
                        composable(Destinations.PROJECTS) {
                            ProjectsScreen(container = container, onOpen = { id -> nav.navigate(Destinations.projectDetail(id)) })
                        }
                        composable(Destinations.PROJECT_DETAIL) { entry ->
                            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            ProjectDetailScreen(
                                container = container,
                                projectId = id,
                                onOpenEditor = { promptId -> nav.navigate(Destinations.editor(promptId)) }
                            )
                        }
                        composable(Destinations.EDITOR) { entry ->
                            val promptId = entry.arguments?.getString("promptId")?.toLongOrNull() ?: 0L
                            EditorScreen(container = container, promptId = promptId, onDone = { nav.popBackStack() })
                        }
                        composable(Destinations.MODELS) { ModelsScreen(container = container) }
                        composable(Destinations.SETTINGS) {
                            SettingsScreen(container = container, onAbout = { nav.navigate(Destinations.ABOUT) })
                        }
                        composable(Destinations.ABOUT) { AboutScreen() }
                    }
                }
            }
        }
    }
}
