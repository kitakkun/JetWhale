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
import com.kitakkun.jetwhale.plugins.network.agent.okhttp.okHttpInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

actual val platformExtraTabLabel: String? = "Network (OkHttp)"

/**
 * OkHttp-based mirror of [NetworkTestScreen] — shares the same [DIModule.networkAgentPlugin], so
 * both tabs' traffic shows up side by side in the same Network Inspector session. Android-only
 * since OkHttp only targets JVM/Android.
 */
private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(DIModule.networkAgentPlugin.okHttpInterceptor())
        .build()
}

@Composable
actual fun PlatformExtraTabScreen() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("https://jsonplaceholder.typicode.com") }
    val log = remember { mutableStateListOf<String>() }

    fun fire(label: String, block: (baseUrl: String) -> String) {
        val target = baseUrl.trimEnd('/')
        scope.launch {
            val line = try {
                "$label → ${withContext(Dispatchers.IO) { block(target) }}"
            } catch (e: CancellationException) {
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
            Text("Requests go through the monitored OkHttp client; watch them in the Network Inspector.")
        }
        item {
            Button(
                onClick = {
                    fire("GET /todos/1") { base ->
                        val request = Request.Builder().url("$base/todos/1").build()
                        okHttpClient.newCall(request).execute().use { response ->
                            "${response.code} ${response.body?.string()}"
                        }
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
                        val body = """{"title":"New todo"}""".toRequestBody("application/json".toMediaType())
                        val request = Request.Builder().url("$base/todos").post(body).build()
                        okHttpClient.newCall(request).execute().use { response ->
                            "${response.code} ${response.body?.string()}"
                        }
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
                        val request = Request.Builder().url("$base/todos/1").delete().build()
                        okHttpClient.newCall(request).execute().use { response ->
                            "${response.code} ${response.body?.string()}"
                        }
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
                        val request = Request.Builder().url("$base/nonexistent-path").build()
                        okHttpClient.newCall(request).execute().use { response ->
                            "${response.code} ${response.body?.string()}"
                        }
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
