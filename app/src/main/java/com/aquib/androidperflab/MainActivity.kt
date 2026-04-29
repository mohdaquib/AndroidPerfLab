package com.aquib.androidperflab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aquib.androidperflab.ui.HomeScreen
import com.aquib.androidperflab.ui.theme.AndroidPerfLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidPerfLabTheme {
                HomeScreen()
            }
        }
    }
}