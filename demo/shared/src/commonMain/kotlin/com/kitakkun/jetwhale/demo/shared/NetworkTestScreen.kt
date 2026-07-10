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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lets the user point the monitored Ktor client at any server and fire sample requests.
 *
 * Defaults to the public JSONPlaceholder API (https://jsonplaceholder.typicode.com) — a free,
 * real HTTPS test API — so this works out of the box on every platform with no local server or
 * cleartext-traffic setup needed. Change the base URL to point at your own backend instead.
 */
@Composable
internal fun NetworkTestScreen() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("https://jsonplaceholder.typicode.com") }
    val log = remember { mutableStateListOf<String>() }

    fun fire(label: String, block: suspend (baseUrl: String) -> String) {
        val target = baseUrl.trimEnd('/')
        scope.launch {
            val line = try {
                "$label → ${block(target)}"
            } catch (e: CancellationException) {
                // Never swallow cancellation: re-throw so the coroutine cancellation mechanism keeps working.
                throw e
            } catch (e: Throwable) {
                "$label → error: ${e.message}"
            }
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
                    fire("GET /todos/1") { base ->
                        val response = DIModule.httpClient.get("$base/todos/1")
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("GET /todos/1")
            }
        }
        item {
            Button(
                onClick = {
                    fire("POST /todos") { base ->
                        val response = DIModule.httpClient.post("$base/todos") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"New todo"}""")
                        }
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("POST /todos")
            }
        }
        item {
            Button(
                onClick = {
                    fire("DELETE /todos/1") { base ->
                        val response = DIModule.httpClient.delete("$base/todos/1")
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("DELETE /todos/1")
            }
        }
        item {
            Button(
                onClick = {
                    fire("GET /nonexistent-path") { base ->
                        val response = DIModule.httpClient.get("$base/nonexistent-path")
                        "${response.status.value} ${response.bodyAsText()}"
                    }
                },
            ) {
                Text("GET /nonexistent-path (404)")
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
