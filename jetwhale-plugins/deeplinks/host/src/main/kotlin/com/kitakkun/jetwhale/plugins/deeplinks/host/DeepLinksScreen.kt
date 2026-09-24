package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher

/** The catalog is a column of patterns; the composer and history beside it need the room. */
private const val CATALOG_FRACTION = 0.4f

/** Binds the host-owned state — the browser and the persisted favorites — to [DeepLinksScreen]. */
@Composable
internal fun DeepLinksScreenRoot(browser: DeepLinkBrowser, modifier: Modifier = Modifier) {
    var favorites by rememberPersistent("favorites", default = emptyList<String>())
    DeepLinksScreen(
        catalog = browser.catalog,
        composer = LinkComposerState(template = browser.template, draftUrl = browser.draftUrl, parameters = browser.parameters, check = browser.draft),
        history = browser.history,
        favorites = favorites,
        error = browser.error,
        actions = browser,
        onToggleFavorite = { url -> favorites = if (url in favorites) favorites - url else favorites + url },
        modifier = modifier,
    )
}

@Composable
internal fun DeepLinksScreen(
    catalog: DeepLinkCatalog?,
    composer: LinkComposerState,
    history: List<OpenedLink>,
    favorites: List<String>,
    error: String?,
    actions: DeepLinkActions,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Deep Links",
            actions = { JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text) },
        )
        error?.let { JwBanner(text = it, tone = JwTone.Error) }
        catalog?.notes?.forEach { JwBanner(text = it, tone = JwTone.Info) }
        if (catalog != null && !catalog.canOpen) {
            JwBanner(text = "This app cannot open links from here; its agent has no opener for this platform.", tone = JwTone.Warning)
        }
        JwSplitPane(
            state = rememberJwSplitPaneState(CATALOG_FRACTION),
            first = { CatalogList(catalog = catalog, favorites = favorites, actions = actions) },
            second = {
                LinkComposer(
                    state = composer,
                    history = history,
                    favorites = favorites,
                    canOpen = catalog?.canOpen == true,
                    actions = actions,
                    onToggleFavorite = onToggleFavorite,
                )
            },
        )
    }
}

@Composable
private fun CatalogList(catalog: DeepLinkCatalog?, favorites: List<String>, actions: DeepLinkActions) {
    if (catalog == null) {
        JwEmptyState(title = "Loading", description = "Asking the app for the links it handles.")
        return
    }
    if (catalog.declared.isEmpty() && catalog.templates.isEmpty() && favorites.isEmpty()) {
        JwEmptyState(
            title = "No deep links",
            description = "The app declares no links the platform can list. Register templates with JetWhaleDeepLinkAgentPlugin to offer some.",
        )
        return
    }
    val groups = catalog.declared.groupBy(::groupTitleOf)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (favorites.isNotEmpty()) {
                item { JwSectionHeader(title = "Favorites", count = favorites.size) }
                items(favorites) { url -> JwListItem(text = url, selected = false, onClick = { actions.editUrl(url) }) }
            }
            if (catalog.templates.isNotEmpty()) {
                item { JwSectionHeader(title = "Templates", count = catalog.templates.size) }
                items(catalog.templates) { template -> TemplateRow(template, actions) }
            }
            groups.forEach { (title, links) ->
                item { JwSectionHeader(title = title, count = links.size) }
                items(links) { link -> DeclarationRow(link, actions) }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: DeepLinkTemplate, actions: DeepLinkActions) {
    JwListItem(
        text = template.name,
        supportingText = template.description?.let { "${template.template} — $it" } ?: template.template,
        selected = false,
        onClick = { actions.startFrom(template) },
    )
}

@Composable
private fun DeclarationRow(link: DeclaredDeepLink, actions: DeepLinkActions) {
    val paths = link.paths.joinToString(transform = ::describe).ifEmpty { "any path" }
    val flags = buildList {
        if (!link.browsable) add("not browsable")
        if (link.autoVerify) add("App Links")
        link.hosts.mapNotNull { host -> host.verification?.let { "${host.host}: $it" } }.forEach(::add)
    }
    JwListItem(
        text = paths,
        supportingText = (listOf(link.handler.substringAfterLast('.')) + flags).joinToString(" · "),
        selected = false,
        onClick = { actions.startFrom(link) },
    )
}

/** `scheme://host` for a declaration, listing every scheme and host it combines. */
private fun groupTitleOf(link: DeclaredDeepLink): String {
    val schemes = link.schemes.joinToString("|")
    val hosts = link.hosts.joinToString("|") { it.host + (it.port?.let { port -> ":$port" } ?: "") }.ifEmpty { "*" }
    return "$schemes://$hosts"
}

private fun describe(matcher: PathMatcher): String = when (matcher.kind) {
    PathMatchKind.Exact -> matcher.value
    PathMatchKind.Prefix -> "${matcher.value}…"
    PathMatchKind.Suffix -> "…${matcher.value}"
    PathMatchKind.Pattern -> "pattern ${matcher.value}"
    PathMatchKind.AdvancedPattern -> "regex ${matcher.value}"
}
