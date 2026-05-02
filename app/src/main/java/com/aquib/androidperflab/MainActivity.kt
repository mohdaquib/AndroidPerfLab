package com.aquib.androidperflab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aquib.androidperflab.ui.HomeScreen
import com.aquib.androidperflab.ui.theme.AndroidPerfLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidPerfLabTheme {
                var showDashboard by remember { mutableStateOf(false) }
                if (showDashboard) {
                    BenchmarkDashboard(onBack = { showDashboard = false })
                } else {
                    HomeScreen(onShowDashboard = { showDashboard = true })
                }
            }
        }
    }
}