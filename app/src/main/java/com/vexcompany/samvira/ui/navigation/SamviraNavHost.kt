package com.vexcompany.samvira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vexcompany.samvira.core.di.AppContainer
import com.vexcompany.samvira.ui.gallery.GalleryScreen
import com.vexcompany.samvira.ui.gallery.GalleryViewModel
import com.vexcompany.samvira.ui.home.HomeScreen
import com.vexcompany.samvira.ui.home.HomeViewModel

object Routes {
    const val HOME = "home"
    const val GALLERY = "gallery"
}

@Composable
fun SamviraNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel {
                HomeViewModel(
                    identityRepository = container.identityRepository,
                    authRepository = container.authRepository,
                    organizationRepository = container.organizationRepository,
                    organizationSelection = container.organizationSelection,
                    mediaRepository = container.mediaRepository,
                )
            }
            HomeScreen(
                viewModel = viewModel,
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
            )
        }
        composable(Routes.GALLERY) {
            val viewModel: GalleryViewModel = viewModel {
                GalleryViewModel(
                    mediaRepository = container.mediaRepository,
                    sessionStore = container.sessionStore,
                    organizationSelection = container.organizationSelection,
                )
            }
            GalleryScreen(viewModel = viewModel)
        }
    }
}
