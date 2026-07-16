package com.kevinboutwell.lightmeter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevinboutwell.lightmeter.ui.calibration.CalibrationScreen
import com.kevinboutwell.lightmeter.ui.log.LogScreen
import com.kevinboutwell.lightmeter.ui.meter.MeterScreen
import com.kevinboutwell.lightmeter.ui.settings.SettingsScreen

@Composable
fun LightMeterNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "meter") {
        composable("meter") {
            MeterScreen(
                onOpenLog = { navController.navigate("log") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("log") {
            LogScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCalibrate = { reflective -> navController.navigate("calibration/$reflective") },
            )
        }
        composable(
            route = "calibration/{reflective}",
            arguments = listOf(navArgument("reflective") { type = NavType.BoolType }),
        ) { entry ->
            CalibrationScreen(
                isReflective = entry.arguments?.getBoolean("reflective") ?: true,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
