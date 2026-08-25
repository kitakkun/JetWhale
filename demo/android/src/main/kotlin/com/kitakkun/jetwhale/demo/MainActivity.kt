package com.kitakkun.jetwhale.demo

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kitakkun.jetwhale.demo.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(0, 0))

        super.onCreate(savedInstanceState)

        // The composition is hosted by a plain View layout rather than by setContent, so the
        // Compose Semantics Inspector's Android View support has both directions to show: the
        // views around a ComposeView, and the views an AndroidView places inside the composition.
        val header = TextView(this).apply {
            id = R.id.demo_view_host_header
            text = getString(R.string.demo_view_host_header)
            // Edge-to-edge puts this view under the status bar; the composition below handles its
            // own insets, so only the header needs padding.
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(statusBar.left, statusBar.top, statusBar.right, 0)
                insets
            }
        }
        val composeView = ComposeView(this).apply {
            id = R.id.demo_compose_view
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setContent {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        App()
                    }
                    AndroidViewDemoStrip()
                }
            }
        }
        setContentView(
            LinearLayout(this).apply {
                id = R.id.demo_view_host_root
                orientation = LinearLayout.VERTICAL
                addView(header)
                addView(composeView)
            },
        )
    }
}

/**
 * A strip of plain Android `View`s inside the composition, so the Compose Semantics Inspector's
 * Android View support has something to show: these appear in the captured tree as `View` nodes
 * underneath the semantics node that placed them.
 */
@Composable
private fun AndroidViewDemoStrip() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            val label = TextView(context).apply {
                id = R.id.demo_android_view_label
                text = context.getString(R.string.demo_android_view_label)
            }
            val button = Button(context).apply {
                id = R.id.demo_android_view_button
                text = context.getString(R.string.demo_android_view_button)
                setOnClickListener {
                    label.text = context.getString(R.string.demo_android_view_label_tapped)
                }
            }
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label)
                addView(button)
            }
        },
    )
}
