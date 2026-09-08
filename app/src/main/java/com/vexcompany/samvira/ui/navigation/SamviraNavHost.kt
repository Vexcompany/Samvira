package com.vexcompany.samvira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vexcompany.samvira.core.di.AppContainer
import com.vexcompany.samvira.ui.home.HomeScreen
import com.vexcompany.samvira.ui.home.HomeViewModel

/**
 * Central navigation routes. Future product screens (albums, timelines, …)
 * are added here; the foundation ships only the home/status destination.
 */
object Routes {
    const val HOME = "home"
}

/**
 * Navigation host for the single-activity Compose app.
 *
 * Only the foundation home destination exists today. The NavHost scaffolding
 * keeps route/back-stack concerns in one place so future screens can be added
 * without restructuring the activity.
 */
@Composable
fun SamviraNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel {
                HomeViewModel(
                    identityRepository = container.identityRepository,
                    authRepository = container.authRepository,
                    organizationRepository = container.organizationRepository,
                    organizationSelection = container.organizationSelection,
                )
            }
            HomeScreen(viewModel = viewModel)
        }
    }
}
