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

/** One link opened from the host, with what the app said about it. */
internal data class OpenedLink(val url: String, val result: DeepLinkOpenResult)

/**
 * What the link being composed resolves to: the URL, or why there is none yet, and the declarations
 * it matches.
 */
internal data class DraftCheck(val url: String?, val problem: String?, val matches: List<DeclaredDeepLink>)

/** What the user does in the Deep Links UI. */
internal interface DeepLinkActions {
    fun refresh()

    /** Starts a draft from [link]'s scheme, host and path. */
    fun startFrom(link: DeclaredDeepLink)

    /** Starts a draft from [template], whose placeholders become form fields. */
    fun startFrom(template: DeepLinkTemplate)

    fun editUrl(url: String)

    fun editParameter(name: String, value: String)

    fun open(url: String)
}

/**
 * The app's link catalog and the link being composed. Every call goes through [client] on [scope];
 * a failure to reach the app lands in [error] rather than being thrown.
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

    var history: List<OpenedLink> by mutableStateOf(emptyList())
        private set

    var error: String? by mutableStateOf(null)
        private set

    val draft: DraftCheck
        get() {
            val url = template?.let { current ->
                try {
                    fillTemplate(current.template, parameters)
                } catch (e: IllegalArgumentException) {
                    return DraftCheck(url = null, problem = e.message, matches = emptyList())
                }
            } ?: draftUrl.trim().ifEmpty { return DraftCheck(url = null, problem = "Enter a link", matches = emptyList()) }
            return try {
                DraftCheck(url = url, problem = null, matches = declarationsMatching(url, catalog?.declared.orEmpty()))
            } catch (e: IllegalArgumentException) {
                DraftCheck(url = null, problem = e.message, matches = emptyList())
            }
        }

    suspend fun load() {
        catalog = client.catalog()
    }

    override fun refresh() = launchReporting(::load)

    override fun startFrom(link: DeclaredDeepLink) {
        template = null
        draftUrl = sampleUrlOf(link)
    }

    override fun startFrom(template: DeepLinkTemplate) {
        this.template = template
        parameters = placeholdersOf(template.template).associateWith { parameters[it].orEmpty() }
    }

    override fun editUrl(url: String) {
        template = null
        draftUrl = url
    }

    override fun editParameter(name: String, value: String) {
        parameters = parameters + (name to value)
    }

    override fun open(url: String) = launchReporting {
        val result = client.open(url)
        history = (listOf(OpenedLink(url, result)) + history).take(HISTORY_LIMIT)
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                error = null
            } catch (e: JetWhaleMessagingException) {
                error = "Failed to reach the app: ${e.message}"
            }
        }
    }
}
