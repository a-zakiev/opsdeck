package com.zakiev.spatialdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zakiev.spatialdashboard.ui.DashboardApp
import com.zakiev.spatialdashboard.ui.theme.SpatialDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpatialDashboardTheme {
                DashboardApp()
            }
        }
    }
}
