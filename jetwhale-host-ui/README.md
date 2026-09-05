# jetwhale-host-ui

The theme and component library the JetWhale host is built from, published so a host plugin can
look like one more pane of the same tool. Every public name carries the `Jw` prefix.

```kotlin
dependencies {
    // Provided by the host at runtime, like jetwhale-host-sdk: compileOnly, never bundled.
    compileOnly("com.kitakkun.jetwhale:jetwhale-host-ui:<version>")
}
```

The host wraps a plugin's `Content()` in `JwTheme` before calling it, so inside a plugin the
components already draw with the host's configured scheme. Call `JwTheme(darkTheme = …)` yourself
only in previews and tests.

## What is here

| Layer | Names |
|-------|-------|
| Theme | `JwTheme` (+ `JwTheme.colors`, `JwTheme.isDark`), `JwColorSchemes`, `JwExtendedColors`, `JwTypography` (+ `.code`), `JwShapes`, `JwSpacing`, `JwMetrics`, `JwTone`, `JwIcons` |
| Structure | `JwToolbar`, `JwTabRow` + `JwTab`, `JwPanel`, `JwSectionHeader`, `JwHorizontalDivider` / `JwVerticalDivider`, `JwDialog` / `JwDialogSurface` |
| Rows and labels | `JwListItem`, `JwTreeRow`, `JwKeyValueRow`, `JwTag`, `JwStatusDot`, `JwBanner`, `JwEmptyState` |
| Controls | `JwButton` (text and slot overloads), `JwIconButton` + `JwIcon`, `JwTooltip`, `JwTextField`, `JwSearchField`, `JwFormField`, `JwSwitch`, `JwCheckbox`, `JwDropdownButton` + `JwDropdownMenu` + `JwMenuItem` |

Every component is sized for a desktop tool window — `JwMetrics.controlHeight` (28dp) controls,
13sp body text, 4dp corners — and takes its colors from the theme, so it follows the user's
light, dark or custom scheme without any work on the caller's side.

## Conventions

- **Parameter order**: required data first, then `modifier`, then options with defaults, then
  composable slots last — so a trailing lambda is always the main content.
- **Slots**: a slot meant for a glyph is named `leadingIcon` / `trailingIcon` (or `icon` when a
  component has one); a slot that takes arbitrary content is `leading` / `trailing`. Slots receive
  the component's content color through `LocalContentColor`, so a `JwIcon` inside needs no tint.
- **Tones**: `JwTone` (`Neutral`, `Accent`, `Success`, `Warning`, `Error`, `Info`) is the one
  vocabulary for semantic color, shared by `JwTag`, `JwStatusDot`, `JwBanner`, `JwButton` and
  `JwMenuItem`.
- **Accessibility**: a `JwIconButton`'s `tooltip` is also its accessibility name; `JwListItem`,
  `JwTreeRow`, `JwTab` and `JwMenuItem` expose `selected` in semantics. The host's MCP tools read
  the same semantics tree, so an agent driving a plugin sees the same names and states a screen
  reader would.
- **Sizes**: `JwSpacing` steps (`tiny` 2dp … `huge` 24dp) and `JwMetrics` are the only lengths the
  components use; lay out custom content on the same steps and it lines up.

Material 3 and any other Compose library keep working inside a plugin; prefer these components
where they have what you need, and drop to Material or your own composables for the rest.
