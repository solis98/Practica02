package com.example.practica02.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.practica02.screens.ConversionScreen
import com.example.practica02.screens.HistorialScreen

@Composable
fun SesionNavigation(
    uid: String,
    onCerrarSesion: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "conversion"
    ) {
        composable("conversion") {
            ConversionScreen(
                uid = uid,
                onCerrarSesion = onCerrarSesion,
                onVerHistorial = {
                    navController.navigate("historial") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("historial") {
            HistorialScreen(
                uid = uid,
                onVolver = {
                    navController.popBackStack()
                }
            )
        }
    }
}