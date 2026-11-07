package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppThemeMode
import com.example.ui.ChatScreen
import com.example.ui.ChatViewModel
import com.example.ui.theme.MyApplicationTheme

/**
 * Activité principale de l'application LiteRT AI
 * Instancie le ChatViewModel (qui initialise LlmEngine et télécharge le modèle dès le démarrage)
 * et applique les préférences de thème dynamiques.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = (themeMode == AppThemeMode.SYSTEM)) {
                ChatScreen(viewModel = viewModel)
            }
        }
    }

    companion object {
        init {
            try {
                System.loadLibrary("litertlm_jni")
            } catch (_: Throwable) {
                // Ignore if loaded on demand
            }
        }
    }
}

