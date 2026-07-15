package com.badmintonledger.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.badmintonledger.app.LedgerViewModel

@Composable
fun AppNav(vm: LedgerViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onRecordSession = { nav.navigate("session") { launchSingleTop = true } },
                onOpenRefill = { nav.navigate("refill") { launchSingleTop = true } },
                onOpenPayment = { nav.navigate("payment") { launchSingleTop = true } },
                onOpenReport = { nav.navigate("report") { launchSingleTop = true } },
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("session") {
            SessionScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSaved = { sessionId ->
                    nav.navigate("report?sessionId=$sessionId") {
                        launchSingleTop = true
                        popUpTo("home")
                    }
                },
            )
        }
        composable("refill") {
            RefillScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("payment") {
            PaymentScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(
            route = "report?sessionId={sessionId}",
            arguments =
                listOf(
                    navArgument("sessionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { entry ->
            ReportScreen(
                vm = vm,
                initialSessionId = entry.arguments?.getString("sessionId"),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
