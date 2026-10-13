package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher

private val previewDeclared = listOf(
    DeclaredDeepLink(
        handler = "com.example.app.MainActivity",
        schemes = listOf("demo"),
        hosts = emptyList(),
        paths = emptyList(),
        browsable = true,
        autoVerify = false,
    ),
    DeclaredDeepLink(
        handler = "com.example.app.MainActivity",
        schemes = listOf("https"),
        hosts = listOf(DeepLinkHost(host = "example.com", port = null, verification = "verified")),
        paths = listOf(PathMatcher(PathMatchKind.Prefix, "/item/")),
        browsable = true,
        autoVerify = true,
    ),
)

private val previewTemplate = DeepLinkTemplate(name = "Item", template = "demo://item/{id}", description = "Opens an item page")

private val previewCatalog = DeepLinkCatalog(declared = previewDeclared, templates = listOf(previewTemplate), canOpen = true, notes = emptyList())

private object NoActions : DeepLinkActions {
    override fun refresh() = Unit

    override fun startDraftFrom(declaration: DeclaredDeepLink) = Unit

    override fun startDraftFrom(template: DeepLinkTemplate) = Unit

    override fun editDraftUrl(url: String) = Unit

    override fun editParameter(name: String, value: String) = Unit

    override fun open(url: String) = Unit
}

@Preview
@Composable
private fun DeepLinksScreenPreview() {
    JwTheme(darkTheme = false) {
        DeepLinksScreen(
            catalog = previewCatalog,
            linkComposerState = LinkComposerState(
                template = previewTemplate,
                draftUrl = "",
                parameters = mapOf("id" to "42"),
                draftCheck = DraftCheck(url = "demo://item/42", problem = null, matchingDeclarations = previewDeclared.take(1)),
            ),
            history = listOf(
                LinkOpenAttempt("demo://item/7", DeepLinkOpenResult(opened = true, handledBy = listOf("com.example.app.MainActivity"), error = null)),
                LinkOpenAttempt("https://example.com/cart", DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "no activity of this app handles it")),
            ),
            favoriteUrls = listOf("demo://settings/Privacy"),
            error = null,
            actions = NoActions,
            onToggleFavorite = {},
        )
    }
}

@Preview
@Composable
private fun LinkComposerUnmatchedPreview() {
    JwTheme(darkTheme = true) {
        LinkComposer(
            state = LinkComposerState(template = null, draftUrl = "https://example.org/x", parameters = emptyMap(), draftCheck = DraftCheck(url = "https://example.org/x", problem = null, matchingDeclarations = emptyList())),
            history = emptyList(),
            favoriteUrls = emptyList(),
            canOpen = true,
            actions = NoActions,
            onToggleFavorite = {},
        )
    }
}
