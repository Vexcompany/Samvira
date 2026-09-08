package com.vexcompany.samvira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vexcompany.samvira.ui.navigation.SamviraNavHost
import com.vexcompany.samvira.ui.theme.SamviraTheme

/**
 * Single-activity host for the Compose UI. All screens live inside
 * [SamviraNavHost]; navigation state is owned by the NavHost's controller.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamviraTheme {
                val container = (application as SamviraApplication).container
                SamviraNavHost(container = container)
            }
        }
    }
}
