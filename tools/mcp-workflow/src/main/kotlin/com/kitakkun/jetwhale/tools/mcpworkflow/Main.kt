package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.TimeSource

private const val USAGE = """mcp-workflow — run, record and serve workflows of MCP tool calls

  run <workflow...>        Run workflows; exit code 1 if any step fails
      --mcp-config FILE    Servers to use, as in .mcp.json (a workflow's own `servers` win)
      --input NAME=VALUE   Workflow input; VALUE is JSON when it parses as JSON, else a string
      --report-dir DIR     Write report.md, junit.xml and images there
  validate <workflow...>   Check workflows without calling anything
      --mcp-config FILE    --online: also check that each called tool exists
  record                   Serve a recording proxy in front of the configured servers
      --mcp-config FILE    --server NAME (repeatable; default: every server in the config)
      --out FILE           --name NAME   --port PORT (SSE instead of stdio)
  serve <workflow|dir...>  Serve each workflow as one MCP tool
      --mcp-config FILE    --report-dir DIR   --port PORT (SSE instead of stdio)
  tools                    List the tools of the configured servers with their arguments
      --mcp-config FILE    --server NAME (repeatable; default: every server)
  demo-server              A small in-memory accounts server over stdio, for trying things out
"""

private const val EXIT_FAILED = 1
private const val EXIT_USAGE = 2

fun main(arguments: Array<String>) {
    val command = arguments.firstOrNull()
    val commandLine = CommandLine(arguments.drop(1))
    val exitCode = try {
        when (command) {
            "run" -> runWorkflows(commandLine)

            "validate" -> validateWorkflowFiles(commandLine)

            "record" -> serveRecordingProxy(commandLine)

            "serve" -> serveWorkflowTools(commandLine)

            "tools" -> printServerTools(commandLine)

            "demo-server" -> runBlocking { serveStdio(demoServer()) }.let { 0 }

            "--help", "-h", "help" -> {
                println(USAGE)
                0
            }

            else -> {
                System.err.println(USAGE)
                EXIT_USAGE
            }
        }
    } catch (e: WorkflowFormatException) {
        System.err.println("mcp-workflow: ${e.message}")
        EXIT_USAGE
    }
    exitProcess(exitCode)
}

private fun runWorkflows(commandLine: CommandLine): Int {
    val files = commandLine.positionalArguments.map(::File)
    if (files.isEmpty()) throw WorkflowFormatException("run needs at least one workflow file")
    val reportDirectory = commandLine.optionValue("report-dir")?.let(::File)
    val inputs = commandLine.optionValues("input").associate(::parseAssignment)
    val runs = files.map { file ->
        val workflow = readWorkflow(file)
        val configs = readServerConfigs(commandLine, workflow)
        val caller = McpServerConnections(configs)
        println("▶ ${workflow.name} (${file.path})")
        runBlocking {
            try {
                val runner = WorkflowRunner(caller, configs.keys, TimeSource.Monotonic, System.getenv(), reportDirectory?.let { File(it, "artifacts") }) { step ->
                    println(consoleLine(step))
                }
                runner.run(workflow, inputs.filterKeys(workflow.inputs::containsKey))
            } finally {
                caller.close()
            }
        }.also { println(consoleSummary(it)) }
    }
    reportDirectory?.let { directory ->
        writeJUnitReport(runs, File(directory, "junit.xml"))
        writeMarkdownReport(runs, File(directory, "report.md"))
        println("Reports written to ${directory.path}")
    }
    return if (runs.all(RunOutcome::passed)) 0 else EXIT_FAILED
}

private fun parseAssignment(text: String): Pair<String, JsonElement> {
    val name = text.substringBefore('=', missingDelimiterValue = "")
    if (name.isEmpty()) throw WorkflowFormatException("--input expects NAME=VALUE, got '$text'")
    val jsonOrText = text.substringAfter('=')
    val value = try {
        Json.parseToJsonElement(jsonOrText)
    } catch (_: SerializationException) {
        JsonPrimitive(jsonOrText)
    }
    return name to value
}

private fun validateWorkflowFiles(commandLine: CommandLine): Int {
    var problemCount = 0
    commandLine.positionalArguments.map(::File).forEach { file ->
        val workflow = readWorkflow(file)
        val configs = readServerConfigs(commandLine, workflow)
        val problems = validate(workflow, configs.keys) + if (commandLine.hasSwitch("online")) checkToolCallsAgainstServers(workflow, configs) else emptyList()
        problems.forEach { System.err.println("${file.path}: $it") }
        if (problems.isEmpty()) println("${file.path}: ok")
        problemCount += problems.size
    }
    return if (problemCount == 0) 0 else EXIT_FAILED
}

private fun checkToolCallsAgainstServers(workflow: Workflow, configs: Map<String, ServerConfig>): List<String> = runBlocking {
    val caller = McpServerConnections(configs)
    try {
        val toolsByServer = configs.keys.associateWith { server -> runCatching { caller.listTools(server) }.getOrNull() }
        workflow.steps.mapIndexedNotNull { index, step ->
            val server = step.server ?: configs.keys.singleOrNull() ?: return@mapIndexedNotNull null
            val tools = toolsByServer[server] ?: return@mapIndexedNotNull "step ${index + 1}: could not list tools of '$server'"
            val tool = tools.firstOrNull { it.name == step.call } ?: return@mapIndexedNotNull "step ${index + 1}: '$server' has no tool '${step.call}'"
            val missing = tool.inputSchema.required.orEmpty() - step.args.keys
            if (missing.isNotEmpty()) "step ${index + 1}: '${step.call}' requires ${missing.joinToString()}" else null
        }
    } finally {
        caller.close()
    }
}

private fun serveRecordingProxy(commandLine: CommandLine): Int {
    val configs = commandLine.optionValue("mcp-config")?.let { readMcpConfig(File(it)) }
        ?: throw WorkflowFormatException("record needs --mcp-config with the servers to record")
    val selectedServers = commandLine.optionValues("server").ifEmpty { configs.keys.toList() }
    val caller = McpServerConnections(configs.filterKeys(selectedServers::contains))
    val proxy = RecordingProxy(caller, selectedServers)
    commandLine.optionValue("out")?.let(::File)?.let { output ->
        val workflowName = commandLine.optionValue("name") ?: "Recorded flow"
        Runtime.getRuntime().addShutdownHook(Thread { proxy.writeRecording(output, workflowName) })
    }
    val port = commandLine.optionValue("port")?.toInt()
    if (port != null) {
        System.err.println("mcp-workflow: recording proxy on http://127.0.0.1:$port/sse")
        serveSse(port, wait = true, proxy::createServer)
    } else {
        runBlocking { serveStdio(proxy.createServer()) }
    }
    return 0
}

private fun serveWorkflowTools(commandLine: CommandLine): Int {
    val files = commandLine.positionalArguments.map(::File).flatMap { path ->
        if (path.isDirectory) path.listFiles { file -> file.extension in setOf("yaml", "yml", "json") }.orEmpty().sortedBy(File::getName) else listOf(path)
    }
    if (files.isEmpty()) throw WorkflowFormatException("serve found no workflow files")
    val reportDirectory = commandLine.optionValue("report-dir")?.let(::File)
    val toolServer = WorkflowToolServer(files) { workflow, inputs ->
        val configs = readServerConfigs(commandLine, workflow)
        val caller = McpServerConnections(configs)
        try {
            WorkflowRunner(caller, configs.keys, TimeSource.Monotonic, System.getenv(), reportDirectory?.let { File(it, "artifacts") }) {}.run(workflow, inputs)
        } finally {
            caller.close()
        }
    }
    val port = commandLine.optionValue("port")?.toInt()
    if (port != null) {
        System.err.println("mcp-workflow: serving ${files.size} workflows on http://127.0.0.1:$port/sse")
        serveSse(port, wait = true) { toolServer.createServer() }
    } else {
        runBlocking { serveStdio(toolServer.createServer()) }
    }
    return 0
}

private fun printServerTools(commandLine: CommandLine): Int {
    val configs = commandLine.optionValue("mcp-config")?.let { readMcpConfig(File(it)) }
        ?: throw WorkflowFormatException("tools needs --mcp-config")
    val selectedServers = commandLine.optionValues("server").ifEmpty { configs.keys.toList() }
    val caller = McpServerConnections(configs.filterKeys(selectedServers::contains))
    runBlocking {
        try {
            selectedServers.forEach { server ->
                println("# $server")
                caller.listTools(server).sortedBy(Tool::name).forEach { tool ->
                    val required = tool.inputSchema.required.orEmpty()
                    val arguments = tool.inputSchema.properties?.keys.orEmpty().joinToString { if (it in required) it else "$it?" }
                    println("${tool.name}($arguments)")
                    tool.description?.let { println("    ${it.lineSequence().first()}") }
                }
            }
        } finally {
            caller.close()
        }
    }
    return 0
}

private fun readServerConfigs(commandLine: CommandLine, workflow: Workflow): Map<String, ServerConfig> {
    val fromConfig = commandLine.optionValue("mcp-config")?.let { readMcpConfig(File(it)) }.orEmpty()
    return fromConfig + workflow.servers
}

/** `--name value` options (repeatable), `--flag` switches, and positional arguments. */
private class CommandLine(arguments: List<String>) {
    val positionalArguments = mutableListOf<String>()
    private val values = mutableMapOf<String, MutableList<String>>()
    private val switches = mutableSetOf<String>()

    init {
        var index = 0
        while (index < arguments.size) {
            val argument = arguments[index]
            if (argument.startsWith("--")) {
                val name = argument.removePrefix("--")
                val next = arguments.getOrNull(index + 1)
                if (name in KnownSwitchNames || next == null || next.startsWith("--")) {
                    switches += name
                } else {
                    values.getOrPut(name) { mutableListOf() } += next
                    index++
                }
            } else {
                positionalArguments += argument
            }
            index++
        }
    }

    fun optionValue(name: String): String? = values[name]?.last()

    fun optionValues(name: String): List<String> = values[name].orEmpty()

    fun hasSwitch(name: String): Boolean = name in switches
}

private val KnownSwitchNames = setOf("online")
