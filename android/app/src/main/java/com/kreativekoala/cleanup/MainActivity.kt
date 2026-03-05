package com.kreativekoala.cleanup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kreativekoala.cleanup.navigation.BottomNavItem
import com.kreativekoala.cleanup.navigation.CleanupNavGraph
import com.kreativekoala.cleanup.navigation.Screen
import com.kreativekoala.cleanup.ui.theme.CleanupTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CleanupTheme {
                CleanupApp()
            }
        }
    }
}

@Composable
private fun CleanupApp() {
    val navController = rememberNavController()

    // Check onboarding status from preferences
    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("cleanup_prefs", android.content.Context.MODE_PRIVATE)
    var hasCompletedOnboarding by rememberSaveable {
        mutableStateOf(prefs.getBoolean("has_completed_onboarding", false))
    }

    val startDestination = if (hasCompletedOnboarding) Screen.Home.route else Screen.Onboarding.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavRoutes = BottomNavItem.entries.map { it.screen.route }
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                CleanupBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        CleanupNavGraph(
            navController = navController,
            startDestination = startDestination,
            onOnboardingComplete = {
                hasCompletedOnboarding = true
                prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun CleanupBottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.screen.route,
                onClick = { onNavigate(item.screen) }
            )
        }
    }
}
