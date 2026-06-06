package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch

@Composable
internal fun NetworkTestScreen() {
    val scope = rememberCoroutineScope()

    fun request(path: String) {
        scope.launch {
            runCatching {
                DIModule.httpClient
                    .get("http://127.0.0.1:8080$path")
                    .bodyAsText()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text("Send requests through the monitored Ktor client; watch them in the Network Inspector.")
        }
        item {
            Button(onClick = { request("/api/todos/1") }) {
                Text("GET /api/todos/1")
            }
        }
        item {
            Button(onClick = { request("/api/todos/2") }) {
                Text("GET /api/todos/2")
            }
        }
        item {
            Button(onClick = { request("/api/missing") }) {
                Text("GET /api/missing (404)")
            }
        }
    }
}
