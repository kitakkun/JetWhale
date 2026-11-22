package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

actual val platformExtraTabLabel: String? = "HTML interop"

/** HTML elements embedded with `HtmlElementView`, which the Compose Semantics Inspector shows as HTML nodes. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformExtraTabScreen() {
    var typedName by remember { mutableStateOf("") }
    var clickCount by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HTML elements embedded in the composition", style = MaterialTheme.typography.titleMedium)
        HtmlElementView(
            factory = {
                (document.createElement("input") as HTMLInputElement).apply {
                    id = "demo-name"
                    className = "demo-field"
                    placeholder = "Your name"
                    style.cssText = DEMO_ELEMENT_STYLE
                    addEventListener("input", { typedName = value })
                }
            },
            modifier = Modifier.size(width = 240.dp, height = 36.dp),
        )
        Text(if (typedName.isEmpty()) "Type a name into the HTML field" else "Hello, $typedName", Modifier.testTag("html-greeting"))
        HtmlElementView(
            factory = {
                (document.createElement("button") as HTMLButtonElement).apply {
                    id = "demo-button"
                    textContent = "HTML button"
                    style.cssText = DEMO_ELEMENT_STYLE
                    addEventListener("click", { clickCount++ })
                }
            },
            modifier = Modifier.size(width = 160.dp, height = 36.dp),
        )
        Text("HTML button clicks: $clickCount", Modifier.testTag("html-click-count"))
        HtmlElementView(
            factory = {
                (document.createElement("a") as HTMLAnchorElement).apply {
                    id = "demo-link"
                    href = "https://kitakkun.github.io/JetWhale/guide/compose-semantics-inspector"
                    target = "_blank"
                    rel = "noopener"
                    textContent = "Compose Semantics Inspector guide"
                    style.cssText = "font: 14px sans-serif;"
                }
            },
            modifier = Modifier.size(width = 280.dp, height = 24.dp),
        )
    }
}

private const val DEMO_ELEMENT_STYLE: String = "box-sizing: border-box; font: 14px sans-serif;"
