package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

/**
 * Lets the user point the monitored Ktor client at any server and fire sample requests.
 *
 * On the JVM/desktop app a local demo server is started automatically, so the default URL works
 * out of the box; on other platforms (Android/iOS/web) there is no in-process server, so enter a
 * reachable server address (e.g. the desktop app's host, or your own backend).
 */
@Composable
internal fun NetworkTestScreen() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("http://127.0.0.1:8080") }
    val log = remember { mutableStateListOf<String>() }

    fun fire(label: String, block: suspend (baseUrl: String) -> String) {
        val target = baseUrl.trimEnd('/')
        scope.launch {
            val line = runCatching { block(target) }.fold(
                onSuccess = { "$label → $it" },
                onFailure = { "$label → error: ${it.message}" },
            )
            log.add(0, line)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Server base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text("Requests go through the monitored Ktor client; watch them in the Network Inspector.")
        }
        item {
            Button(
                onClick = {
                    fire("GET /api/todos/1") { base ->
                        val response = DIModule.httpClient.get("$base/api/todos/1")
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("GET /api/todos/1")
            }
        }
        item {
            Button(
                onClick = {
                    fire("POST /api/todos") { base ->
                        val response = DIModule.httpClient.post("$base/api/todos") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"New todo"}""")
                        }
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("POST /api/todos")
            }
        }
        item {
            Button(
                onClick = {
                    fire("GET /api/missing") { base ->
                        val response = DIModule.httpClient.get("$base/api/missing")
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("GET /api/missing (404)")
            }
        }
        items(log) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
