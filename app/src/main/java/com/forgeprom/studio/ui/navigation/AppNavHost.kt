package com.forgeprom.studio.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import com.forgeprom.studio.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.forgeprom.studio.R
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgeprom.studio.core.AppContainer
import com.forgeprom.studio.ui.screens.AboutScreen
import com.forgeprom.studio.ui.screens.AnalysisScreen
import com.forgeprom.studio.ui.screens.BackupScreen
import com.forgeprom.studio.ui.screens.CreateScreen
import com.forgeprom.studio.ui.screens.DashboardScreen
import com.forgeprom.studio.ui.screens.EditorScreen
import com.forgeprom.studio.ui.screens.ImageScreen
import com.forgeprom.studio.ui.screens.ModelsScreen
import com.forgeprom.studio.ui.screens.ProjectDetailScreen
import com.forgeprom.studio.ui.screens.ProjectChatScreen
import com.forgeprom.studio.ui.screens.ProjectConversationsScreen
import com.forgeprom.studio.ui.screens.ProjectsScreen
import com.forgeprom.studio.ui.screens.ResultScreen
import com.forgeprom.studio.ui.screens.SettingsScreen

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabs = listOf(
    Tab(Destinations.DASHBOARD, "الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Destinations.CREATE, "توليد", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    Tab(Destinations.PROJECTS, "المشاريع", Icons.Filled.Folder, Icons.Outlined.Folder),
    Tab(Destinations.MODELS, "المحركات", Icons.Filled.Memory, Icons.Outlined.Memory),
    Tab(Destinations.SETTINGS, "الإعدادات", Icons.Filled.Tune, Icons.Outlined.Tune)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(container: AppContainer, onSignOut: () -> Unit = {}) {
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
        Destinations.PROJECT_CONVERSATIONS -> "محادثات المشروع"
        Destinations.PROJECT_CHAT -> "محادثة المشروع"
        Destinations.EDITOR -> "محرّر الموجّه"
        Destinations.BACKUP -> "النسخ الاحتياطي"
        Destinations.ABOUT -> "حول التطبيق"
        else -> null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxHeight >= 500.dp
        val barColors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
        val activeTab = tabs.firstOrNull { it.route == currentRoute }
        Scaffold(
            topBar = {
                if (detailTitle != null) {
                    TopAppBar(
                        title = { Text(detailTitle) },
                        navigationIcon = {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                            }
                        },
                        colors = barColors
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                activeTab?.label ?: "Forgeprom",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            Icon(
                                painterResource(R.drawable.ic_brand),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        },
                        colors = barColors
                    )
                }
            },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { select(tab.route) },
                            icon = {
                                Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
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
                                onOpenEditor = { promptId -> nav.navigate(Destinations.editor(promptId)) },
                                onOpenConversations = { projectId -> nav.navigate(Destinations.projectConversations(projectId)) }
                            )
                        }
                        composable(Destinations.PROJECT_CONVERSATIONS) { entry ->
                            val projectId = entry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                            ProjectConversationsScreen(
                                container = container,
                                projectId = projectId,
                                onOpenConversation = { id -> nav.navigate(Destinations.projectChat(id)) }
                            )
                        }
                        composable(Destinations.PROJECT_CHAT) { entry ->
                            val conversationId = entry.arguments?.getString("conversationId")?.toLongOrNull() ?: 0L
                            ProjectChatScreen(
                                container = container,
                                conversationId = conversationId,
                                onOpenModels = { nav.navigate(Destinations.MODELS) },
                                onOpenSettings = { nav.navigate(Destinations.SETTINGS) }
                            )
                        }
                        composable(Destinations.EDITOR) { entry ->
                            val promptId = entry.arguments?.getString("promptId")?.toLongOrNull() ?: 0L
                            EditorScreen(container = container, promptId = promptId, onDone = { nav.popBackStack() })
                        }
                        composable(Destinations.MODELS) { ModelsScreen(container = container) }
                        composable(Destinations.SETTINGS) {
                            SettingsScreen(
                                container = container,
                                onBackup = { nav.navigate(Destinations.BACKUP) },
                                onAbout = { nav.navigate(Destinations.ABOUT) },
                                onSignOut = onSignOut
                            )
                        }
                        composable(Destinations.BACKUP) { BackupScreen(container = container) }
                        composable(Destinations.ABOUT) { AboutScreen() }
                    }
                }
            }
        }
    }
}
