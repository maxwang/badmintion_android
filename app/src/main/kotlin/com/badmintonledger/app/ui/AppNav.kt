package com.badmintonledger.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("session") {
            SessionScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("refill") {
            RefillScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("payment") {
            PaymentScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
