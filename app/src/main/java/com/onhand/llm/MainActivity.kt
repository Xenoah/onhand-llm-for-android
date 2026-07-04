package com.onhand.llm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.onhand.llm.ui.OnHandRoot
import com.onhand.llm.ui.theme.OnHandTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = OnHandApp.from(this)
        setContent {
            OnHandTheme {
                LaunchedEffect(Unit) { container.maybeAutoLoadModel() }
                OnHandRoot(container)
            }
        }
    }
}
