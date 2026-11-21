package com.example.ui

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.screens.BlockPageScreen
import com.example.ui.screens.BlocksHomeScreen
import com.example.ui.screens.QueryLabScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.VaultSyncScreen
import com.example.ui.theme.VaultBlockTheme

private sealed class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Blocks : TopTab("blocks", R.string.nav_blocks, Icons.Rounded.Widgets, Icons.Outlined.Widgets, "nav_blocks")
    data object QueryLab : TopTab("query_lab", R.string.nav_query_lab, Icons.Rounded.Science, Icons.Outlined.Science, "nav_query_lab")
    data object Tasks : TopTab("tasks", R.string.nav_tasks, Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt, "nav_tasks")
    data object Vault : TopTab("vault", R.string.nav_vault, Icons.Rounded.FolderCopy, Icons.Outlined.FolderCopy, "nav_vault")
}

private const val ROUTE_BLOCK_PAGE = "block_page/{notePath}"

private fun blockPageRoute(path: String): String = "block_page/${Uri.encode(path)}"

@Composable
fun VaultBlockApp(viewModel: VaultViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val jsCache by viewModel.jsCache.collectAsStateWithLifecycle()
    val labMode by viewModel.labMode.collectAsStateWithLifecycle()
    val labDql by viewModel.labDqlQuery.collectAsStateWithLifecycle()
    val labJs by viewModel.labJsScript.collectAsStateWithLifecycle()
    val labDqlResult by viewModel.labDqlResult.collectAsStateWithLifecycle()
    val labJsNodes by viewModel.labJsNodes.collectAsStateWithLifecycle()
    val labRunning by viewModel.labRunning.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    VaultBlockTheme(
        darkTheme = state.settings.darkMode,
        dynamicColor = state.settings.dynamicColor
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDest = backStackEntry?.destination
        val tabs = remember { listOf(TopTab.Blocks, TopTab.QueryLab, TopTab.Tasks, TopTab.Vault) }
        val isTopLevelRoute = tabs.any { tab -> currentDest?.hierarchy?.any { it.route == tab.route } == true }

        val navigateToNote: (String) -> Unit = { target ->
            val resolved = viewModel.resolveNotePath(target) ?: target
            navController.navigate(blockPageRoute(resolved))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets.statusBars,
                bottomBar = {
                    if (!useRail && isTopLevelRoute) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDest?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && isTopLevelRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDest?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = TopTab.Blocks.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(TopTab.Blocks.route) {
                            BlocksHomeScreen(
                                state = state,
                                onSearchChange = viewModel::setSearchQuery,
                                onFilterChange = viewModel::setFilter,
                                onTagFilterClick = viewModel::setTagFilter,
                                onOpenNote = navigateToNote,
                                onTogglePin = viewModel::togglePinned,
                                onSyncNow = viewModel::syncVaultNow,
                                onCreateBlockPage = { title, folder, template ->
                                    viewModel.createBlockPage(title, folder, template) { createdPath ->
                                        navController.navigate(blockPageRoute(createdPath))
                                    }
                                }
                            )
                        }

                        composable(TopTab.QueryLab.route) {
                            QueryLabScreen(
                                mode = labMode,
                                dqlQuery = labDql,
                                jsScript = labJs,
                                dqlResult = labDqlResult,
                                jsNodes = labJsNodes,
                                isRunning = labRunning,
                                onModeChange = viewModel::setLabMode,
                                onDqlChange = viewModel::setLabDqlQuery,
                                onJsChange = viewModel::setLabJsScript,
                                onApplyPreset = viewModel::applyLabPreset,
                                onRunQuery = viewModel::runQueryLab,
                                onSaveAsPage = { title, folder ->
                                    viewModel.saveLabAsBlockPage(title, folder) { createdPath ->
                                        navController.navigate(blockPageRoute(createdPath))
                                    }
                                },
                                onOpenNote = navigateToNote,
                                onToggleTask = viewModel::toggleTask
                            )
                        }

                        composable(TopTab.Tasks.route) {
                            TasksScreen(
                                tasks = state.tasks,
                                notes = state.notes,
                                onToggleTask = viewModel::toggleTask,
                                onOpenNote = navigateToNote,
                                onAddTaskToNote = { note, newTaskBlock ->
                                    viewModel.saveNoteBlocks(note.path, note.properties, note.blocks + newTaskBlock)
                                }
                            )
                        }

                        composable(TopTab.Vault.route) {
                            VaultSyncScreen(
                                state = state,
                                onSyncNow = viewModel::syncVaultNow,
                                onLinkSafFolder = viewModel::linkSafVault,
                                onSwitchToLocalVault = viewModel::switchToInternalVault,
                                onResetStarterVault = viewModel::resetStarterVault,
                                onToggleDarkMode = viewModel::setDarkMode,
                                onToggleDynamicColor = viewModel::setDynamicColor
                            )
                        }

                        composable(
                            route = ROUTE_BLOCK_PAGE,
                            arguments = listOf(navArgument("notePath") { type = NavType.StringType })
                        ) { entry ->
                            val encoded = entry.arguments?.getString("notePath").orEmpty()
                            val notePath = Uri.decode(encoded)
                            val noteFlow = remember(notePath) { viewModel.observeNote(notePath) }
                            val note by noteFlow.collectAsStateWithLifecycle(initialValue = state.notes.firstOrNull { it.path == notePath })

                            BlockPageScreen(
                                note = note,
                                allNotes = state.notes,
                                jsCache = jsCache,
                                jsCacheKeyProvider = viewModel::jsCacheKey,
                                onEvaluateDql = viewModel::evaluateDataview,
                                onRequestJsEval = viewModel::evaluateDataviewJsBlock,
                                onRefreshJsEval = viewModel::forceRefreshDataviewJsBlock,
                                onUpsertProperty = viewModel::upsertProperty,
                                onRemoveProperty = viewModel::removeProperty,
                                onSaveBlocks = viewModel::saveNoteBlocks,
                                onToggleTaskInQuery = viewModel::toggleTask,
                                onToggleBlockTask = viewModel::toggleBlockTask,
                                onTogglePin = viewModel::togglePinned,
                                onDeleteNote = { path ->
                                    viewModel.deleteNote(path) {
                                        navController.popBackStack()
                                    }
                                },
                                onOpenNote = navigateToNote,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
