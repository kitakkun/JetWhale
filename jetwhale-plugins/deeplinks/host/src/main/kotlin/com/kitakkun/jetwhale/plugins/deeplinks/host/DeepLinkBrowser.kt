package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How many opened links the history keeps; older ones fall off. */
private const val HISTORY_LIMIT = 50

/** One attempt to open a link from the host, with what the app said about it. */
internal data class LinkOpenAttempt(val url: String, val result: DeepLinkOpenResult)

/**
 * What the link being composed resolves to: the URL, or why there is none yet, and the declarations
 * it matches.
 */
internal data class DraftCheck(val url: String?, val problem: String?, val matchingDeclarations: List<DeclaredDeepLink>)

/** What the user does in the Deep Links UI. */
internal interface DeepLinkActions {
    fun refresh()

    /** Starts a draft from [declaration]'s scheme, host and path. */
    fun startDraftFrom(declaration: DeclaredDeepLink)

    /** Starts a draft from [template], whose placeholders become form fields. */
    fun startDraftFrom(template: DeepLinkTemplate)

    fun editDraftUrl(url: String)

    fun editParameter(name: String, value: String)

    fun open(url: String)
}

/**
 * The app's link catalog and the link being composed. Every call goes through [client], launched on
 * [scope] except [load]; a failure to reach the app lands in [error] rather than being thrown.
 */
@Stable
internal class DeepLinkBrowser(
    private val client: DeepLinkClient,
    private val scope: CoroutineScope,
) : DeepLinkActions {
    var catalog: DeepLinkCatalog? by mutableStateOf(null)
        private set

    /** The template being filled in, or null while the URL is edited directly. */
    var template: DeepLinkTemplate? by mutableStateOf(null)
        private set

    var draftUrl: String by mutableStateOf("")
        private set

    var parameters: Map<String, String> by mutableStateOf(emptyMap())
        private set

    var history: List<LinkOpenAttempt> by mutableStateOf(emptyList())
        private set

    var error: String? by mutableStateOf(null)
        private set

    val draftCheck: DraftCheck
        get() {
            val url = template?.let { current ->
                try {
                    fillTemplate(current.template, parameters)
                } catch (e: IllegalArgumentException) {
                    return DraftCheck(url = null, problem = e.message, matchingDeclarations = emptyList())
                }
            } ?: draftUrl.trim().ifEmpty { return DraftCheck(url = null, problem = "Enter a link", matchingDeclarations = emptyList()) }
            return try {
                DraftCheck(url = url, problem = null, matchingDeclarations = declarationsMatching(url, catalog?.declared.orEmpty()))
            } catch (e: IllegalArgumentException) {
                DraftCheck(url = null, problem = e.message, matchingDeclarations = emptyList())
            }
        }

    suspend fun load() = reportingFailure {
        catalog = client.catalog()
    }

    override fun refresh() {
        scope.launch { load() }
    }

    override fun startDraftFrom(declaration: DeclaredDeepLink) {
        template = null
        draftUrl = sampleUrlOf(declaration) ?: "${declaration.schemes.first()}://"
    }

    override fun startDraftFrom(template: DeepLinkTemplate) {
        this.template = template
        parameters = placeholdersOf(template.template).associateWith { parameters[it].orEmpty() }
    }

    override fun editDraftUrl(url: String) {
        template = null
        draftUrl = url
    }

    override fun editParameter(name: String, value: String) {
        parameters = parameters + (name to value)
    }

    override fun open(url: String) {
        scope.launch {
            reportingFailure {
                val result = client.open(url)
                history = (listOf(LinkOpenAttempt(url, result)) + history).take(HISTORY_LIMIT)
            }
        }
    }

    private suspend fun reportingFailure(block: suspend () -> Unit) {
        try {
            block()
            error = null
        } catch (e: JetWhaleMessagingException) {
            error = "Failed to reach the app: ${e.message}"
        }
    }
}
