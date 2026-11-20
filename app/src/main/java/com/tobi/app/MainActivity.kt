package com.tobi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobi.app.core.designsystem.TobiTheme
import com.tobi.app.navigation.TobiNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TobiApplication).container
        setContent {
            val prefs by container.userPreferences.state.collectAsStateWithLifecycle()
            TobiTheme(oled = prefs.oledBlack, accentHex = prefs.accentHex) {
                TobiNavHost(container = container)
            }
        }
    }
}
