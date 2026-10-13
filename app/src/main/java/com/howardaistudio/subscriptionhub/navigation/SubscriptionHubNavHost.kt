package com.howardaistudio.subscriptionhub.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.howardaistudio.subscriptionhub.core.ui.PlaceholderScreen
import com.howardaistudio.subscriptionhub.feature.subscription.detail.SubscriptionDetailScreen
import com.howardaistudio.subscriptionhub.feature.subscription.edit.SubscriptionEditScreen
import com.howardaistudio.subscriptionhub.feature.subscription.list.SubscriptionListScreen

/** App shell (SA §8.3 `AppNavGraph`): adaptive top-level navigation plus the feature graph. */
@Composable
fun SubscriptionHubNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Bottom bar / rail only on the four top-level tabs; forms and details use the full screen.
    val layoutType = if (currentDestination.isTopLevel()) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = DashboardRoute) {
            composable<DashboardRoute> { PlaceholderScreen(stringResource(TopLevelDestination.DASHBOARD.labelRes)) }
            composable<SubscriptionsRoute> {
                SubscriptionListScreen(
                    onAdd = { navController.navigate(SubscriptionCreateRoute) },
                    onOpen = { id -> navController.navigate(SubscriptionDetailRoute(id)) },
                )
            }
            composable<CalendarRoute> { PlaceholderScreen(stringResource(TopLevelDestination.CALENDAR.labelRes)) }
            composable<ReportRoute> { PlaceholderScreen(stringResource(TopLevelDestination.REPORT.labelRes)) }

            composable<SubscriptionCreateRoute> { SubscriptionEditScreen(onDone = { navController.popBackStack() }) }
            composable<SubscriptionEditRoute> { SubscriptionEditScreen(onDone = { navController.popBackStack() }) }
            composable<SubscriptionDetailRoute> {
                SubscriptionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(SubscriptionEditRoute(id)) },
                )
            }
        }
    }
}

private fun NavDestination?.isTopLevel(): Boolean =
    this == null || TopLevelDestination.entries.any { destination -> hierarchy.any { it.hasRoute(destination.routeClass) } }
