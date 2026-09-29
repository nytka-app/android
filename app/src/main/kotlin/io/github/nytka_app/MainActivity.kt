package io.github.nytka_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.ui.NytkaNavHost
import io.github.nytka_app.ui.theme.NytkaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NytkaTheme {
                NytkaNavHost(
                    conversations = { Text("Conversations") },
                    device = { Text("Device") },
                )
            }
        }
    }
}
