package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.io.IOException

/** Where a running emulator's gRPC endpoint listens, and the token it accepts. */
internal class EmulatorEndpoint(val port: Int, val token: String?)

/**
 * The gRPC endpoints of the running emulators that leave their discovery files in
 * [runningDirectories], and how a call to an endpoint's `EmulatorController` service is made and
 * answered. Each caller sends its calls through an `OkHttpClient` of its own, set up for them.
 */
internal class EmulatorGrpcEndpoints(private val runningDirectories: List<File>) {
    /** The gRPC endpoint of the emulator with [serial] (e.g. `emulator-5554`), from its discovery file, or null. */
    fun findEndpoint(serial: String): EmulatorEndpoint? {
        val consolePort = serial.removePrefix("emulator-").toIntOrNull() ?: return null
        return runningDirectories.asSequence()
            .flatMap { it.listFiles { file -> file.name.startsWith("pid_") && file.name.endsWith(".ini") }.orEmpty().asSequence() }
            // An emulator that is shutting down removes its file between the listing and the read.
            .mapNotNull { file -> file.readTextOrNull()?.let(::parseEmulatorDiscovery) }
            .firstOrNull { it.first == consolePort }
            ?.second
    }

    /** A call of the `EmulatorController` [method] at [endpoint] with [body], carrying the endpoint's token when it has one. */
    fun buildRequest(endpoint: EmulatorEndpoint, method: String, body: RequestBody): Request = Request.Builder()
        .url("http://127.0.0.1:${endpoint.port}/android.emulation.control.EmulatorController/$method")
        .header("te", "trailers")
        .apply { endpoint.token?.let { header("authorization", "Bearer $it") } }
        .post(body)
        .build()

    /**
     * Why [response] refuses its call, from its headers alone, or null when they do not: a call refused
     * at once answers with headers only, its status among them.
     */
    fun refusalInHeaders(response: Response): String? {
        if (response.code != 200) return "HTTP ${response.code}"
        val status = response.header("grpc-status") ?: return null
        return if (status == "0") null else response.header("grpc-message") ?: "status $status"
    }

    /**
     * Why the unary [response], whose body has been read, refuses its call, or null when it answered:
     * a call that was not refused at once sends its status in trailers after the reply.
     */
    fun refusalOfUnaryReply(response: Response): String? {
        refusalInHeaders(response)?.let { return it }
        if (response.header("grpc-status") == "0") return null
        val status = response.trailers()["grpc-status"]
        return if (status == "0") null else response.trailers()["grpc-message"] ?: "status ${status ?: "missing"}"
    }
}

/**
 * The directories where running emulators leave a `pid_<n>.ini` naming their ports and gRPC token,
 * per OS. [home] and [temp] are the user's home and temporary directories; [runtimeDir] is
 * `XDG_RUNTIME_DIR` on Linux.
 */
internal fun emulatorRunningDirectories(osName: String, home: File, temp: File, runtimeDir: String?): List<File> = when {
    osName.startsWith("Mac") -> listOf(File(home, "Library/Caches/TemporaryItems/avd/running"))
    osName.startsWith("Windows") -> listOf(File(temp, "avd/running"))
    else -> listOfNotNull(runtimeDir?.let { File(it, "avd/running") }, File(temp, "android-${home.name}/avd/running"))
}

private fun File.readTextOrNull(): String? = try {
    readText()
} catch (_: IOException) {
    null
}

/** The console port and gRPC endpoint in the text of an emulator's discovery file, or null without a gRPC port. */
@VisibleForTesting
internal fun parseEmulatorDiscovery(text: String): Pair<Int, EmulatorEndpoint>? {
    val values = text.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
    val consolePort = values["port.serial"]?.toIntOrNull() ?: return null
    val grpcPort = values["grpc.port"]?.toIntOrNull() ?: return null
    return consolePort to EmulatorEndpoint(port = grpcPort, token = values["grpc.token"]?.takeIf(String::isNotEmpty))
}

/** [message] with gRPC's five-byte prefix: not compressed, then its length. */
internal fun grpcMessage(message: ByteArray): ByteArray = Buffer().writeByte(0).writeInt(message.size).write(message).readByteArray()

internal val GRPC_MEDIA_TYPE = "application/grpc".toMediaType()
