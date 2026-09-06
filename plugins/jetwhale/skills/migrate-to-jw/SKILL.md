---
name: migrate-to-jw
description: Migrate a JetWhale host plugin's UI from plain Material 3 to jetwhale-host-ui (the Jw* components), so it looks like part of the host — dependency, component mapping, color and spacing rules, tests, and verification.
---

# Migrate a plugin UI to jetwhale-host-ui

How to move a host plugin written against Material 3 onto **`jetwhale-host-ui`**, the theme and
component library the host itself is built from. The point is not to swap widget names but to make
the plugin read as one more pane of the same tool: compact 28dp controls, 13sp text, hairline
borders, the host's colors in light, dark and custom themes.

The library's README (`jetwhale-host-ui/README.md` in the JetWhale repository) is the reference:
it lists every component, the Material → `Jw` mapping, and the API conventions. Read it first.

## 1. Add the dependency

The host provides the library at runtime, exactly like `jetwhale-host-sdk`, so it is `compileOnly`
and must not be bundled:

```kotlin
dependencies {
    compileOnly("com.kitakkun.jetwhale:jetwhale-host-ui:<hostVersion>")
    // Tests compose the plugin's UI outside the host, so they need it on the classpath.
    testImplementation("com.kitakkun.jetwhale:jetwhale-host-ui:<hostVersion>")
}
```

Use the same version as `jetwhalePlugin.hostVersion`. A plugin compiled against a newer library
than the host ships will fail to load with `NoClassDefFoundError`.

## 2. Inventory what the plugin uses

Before touching code, list the Material calls so nothing is missed at the end:

```bash
grep -rnoE "\b(Scaffold|TopAppBar|Button|OutlinedButton|TextButton|FilledTonalButton|IconButton|Card|OutlinedCard|ElevatedCard|Switch|Checkbox|OutlinedTextField|TextField|TabRow|SecondaryTabRow|PrimaryTabRow|ScrollableTabRow|Tab|FilterChip|AssistChip|SuggestionChip|Badge|AlertDialog|DropdownMenu|DropdownMenuItem|ExposedDropdownMenuBox|HorizontalDivider|VerticalDivider|ListItem|Surface|CircularProgressIndicator)\(" src/main | sort | uniq -c
```

Also grep for hand-rolled equivalents — `RoundedCornerShape(`, `Color(0x`, `FontFamily.Monospace`,
`MaterialTheme.colorScheme.` — they are where the plugin's look diverges from the host's.

## 3. Replace structure first, then controls

Work top-down; the skeleton decides where everything else lands.

| Was | Becomes | Notes |
|-----|---------|-------|
| `Scaffold(topBar = { TopAppBar(...) }) { padding -> ... }` | `Column(Modifier.fillMaxSize()) { JwToolbar(title = ..., actions = { ... }); ... }` | Drop the padding lambda; `JwToolbar` is a 36dp bar in the column, not an overlay |
| `SecondaryTabRow { Tab(text = { Text("Traffic (3)") }) }` | `JwTabRow { JwTab(selected, onClick, text = "Traffic", count = 3) }` | Counts go in `count`, not in the label |
| `Card { Column(Modifier.padding(12.dp)) { ... } }` | `JwPanel { ... }` | The panel pads its content; pass `contentPadding = PaddingValues(0.dp)` to fill it with rows |
| `HorizontalDivider()` / `VerticalDivider()` | `JwHorizontalDivider()` / `JwVerticalDivider()` | |
| `Text("Select something", color = outline)` centered in a `Box` | `JwEmptyState(title, description)` | |
| `Surface(color = surfaceVariant) { Text(title) }` as a group heading | `JwSectionHeader(title, count, trailing)` | |
| `AlertDialog(title, text, confirmButton, dismissButton)` | `JwDialog(onDismissRequest, title, closeLabel, confirmButton, dismissButton) { ... }` | `closeLabel` is required: pass the plugin's own "Close" string. A dialog with no title bar builds on `JwDialogSurface` |

Then the text and the controls:

| Was | Becomes | Notes |
|-----|---------|-------|
| `Text(...)` (Material) | `JwText(...)` | Same parameters you use in practice (`style`, `color`, `maxLines`, `overflow`, `fontFamily`, `fontWeight`); takes the enclosing control's content color, which Material's `Text` does not |
| `Icon(vector, description)` | `JwIcon(vector, contentDescription)` | 16dp, tinted with the content color |
| `CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)` | `JwProgressIndicator()` | Sized for a button label |
| `Button(onClick) { Text("Save") }` | `JwButton(text = "Save", onClick, style = JwButtonStyle.Primary)` | One primary per view |
| `OutlinedButton` / `TextButton` | `JwButton(..., style = Secondary)` / `JwButton(..., style = Text)` | `Secondary` is the default |
| `TextButton(onClick = delete) { Text("Delete") }` | `JwButton(text = "Delete", onClick = delete, style = Text, tone = JwTone.Error)` | Destructive actions carry the `Error` tone |
| A button with a spinner, a count, or an icon after the label | `JwButton(onClick) { CircularProgressIndicator(Modifier.size(12.dp)); Text("Working…") }` | The slot overload provides content color and text style |
| `IconButton(onClick) { Icon(vector, "Refresh") }` | `JwIconButton(onClick, tooltip = "Refresh") { JwIcon(vector, contentDescription = null) }` | The tooltip is also the accessibility name |
| `OutlinedTextField(value, onValueChange, label = { Text("Name") })` | `JwFormField(label = "Name") { JwTextField(value, onValueChange) }` | Labels sit above the field; `supportingText` and `isError` live on the form field / text field |
| `OutlinedTextField(..., leadingIcon = Search, trailingIcon = Clear)` | `JwSearchField(value, onValueChange, placeholder)` | |
| `Switch` / `Checkbox` + `Text(label)` | `JwSwitch(checked, onCheckedChange)` / `JwCheckbox(checked, onCheckedChange, label)` | |
| `ExposedDropdownMenuBox { OutlinedTextField(readOnly) ... }` | `JwDropdownButton(text, expanded, onExpandedChange) { JwMenuItem(text, selected, onClick) }` | The caller closes the menu inside `onClick` |
| `FilterChip(selected, onClick, label)` for a small choice | `JwTag(text, tone = if (selected) Accent else Neutral, style = if (selected) Filled else Outlined, onClick)` | |
| `Surface(color = tint.copy(alpha = 0.14f)) { Text("200") }` status pill | `JwTag(text = "200", tone = JwTone.Success, style = JwTagStyle.Tinted)` | Map 2xx → Success, 3xx → Info, 4xx/5xx → Error, pending → Neutral |
| `Badge(containerColor = Color.Green)` | `JwStatusDot(tone = JwTone.Success)` | `filled = false` for the weaker "available" state |
| A selectable row (`Row.background(if (selected) secondaryContainer ...)`) | `JwListItem(text, selected, onClick, leadingContent, trailingContent, supportingText)` | Exposes `selected` in semantics; a slot overload takes arbitrary row content |
| A tree row with indent and ▸/▾ | `JwTreeRow(text, depth, expandable, expanded, selected, onClick, onToggleExpanded)` | |
| `Row { Text(key, width(140.dp)); Text(value, Monospace) }` | `JwKeyValueRow(key, value, monospace = true, wrap = ...)` | `wrap = false` keeps a long id on one scrolling line |
| A banner `Surface(color = primaryContainer) { Text(msg) }` | `JwBanner(text, tone, actions, onDismiss)` | |

## 4. Colors, type and spacing

These are where a migrated plugin still looks foreign if skipped.

- **Never install a theme.** Delete any `MaterialTheme { ... }` around `Content()`; the host applies
  `JwTheme` (and a Material theme derived from it, for anything still Material).
- **Colors come from `JwTheme.colors`**, named for what they are for: `MaterialTheme.colorScheme.primary`
  → `accent`, `.onSurfaceVariant` → `textSecondary` (readable) — `textDisabled` is only for disabled
  controls — `.surface` → `surface`, `.surfaceContainerLowest` → `panelBackground`, `.surfaceContainer`
  → `elevatedBackground`, `.outline` → `controlBorder`, `.outlineVariant` → `border`, `.error` → `error`.
- **Text styles come from `JwTheme.textStyles`**: `bodyMedium` → `body`, `bodySmall` → `bodySmall`,
  `labelLarge` / `labelMedium` → `label`, `labelSmall` → `labelSmall`, `titleSmall` → `subtitle`,
  `titleMedium` and up → `title`.
- **Selection and hover**: `secondaryContainer` / `primaryContainer` used as a row highlight →
  `JwTheme.colors.selection` / `JwTheme.colors.hover`.
- **Fixed brand hues** (`Color(0xFF2E7D32)` for green, `if (isDark) ... else ...` pairs) → a
  `JwTone` (`Success`, `Warning`, `Error`, `Info`, `Accent`, `Neutral`). Delete the light/dark
  helper; the tones already adapt.
- **Monospace**: `style.copy(fontFamily = FontFamily.Monospace)` → `JwTheme.textStyles.code`.
- **Spacing**: literal `dp` gaps → `JwSpacing.tiny` (2) … `extraSmall` (4), `small` (6),
  `medium` (8), `large` (12), `extraLarge` (16), `huge` (24). Use `JwMetrics.controlHeight` for
  anything that must line up with a control.
- **Background**: a plugin's root should still paint `JwTheme.colors.surface` if it does not fill
  the pane with components; the host does not paint behind the scene for MCP captures.

## 5. Tests and previews

Compose UI tests that wrapped the plugin in `MaterialTheme { }` must wrap it in
`JwTheme(darkTheme = false) { }` instead — the components read `JwTheme.colors`, which throws
outside the theme. The same overload serves `@Preview`s.

## 6. Verify

1. Re-run the inventory grep from §2. Anything left is either deliberate (Material has no `Jw`
   counterpart for it, which is fine — the two coexist) or a miss.
2. Build and run the unit tests.
3. Look at it. Use the `plugin-qa` skill: launch the host with the plugin staged, connect the QA
   agent, and `jetwhale.screenshot` each screen in the state that matters — a list with rows, a
   dialog open, an empty state. Compare against the host's own panes: same row height, same text
   size, same border weight. Check dark **and** light; a fixed color that survived §4 shows up in
   one of them.
4. If the host is already running from a build directory, stop it before rebuilding: a live host
   that lazily loads classes from a rebuilt classpath fails with `NoClassDefFoundError` and shows
   the plugin error fallback, which is not a bug in the migration.

## What not to migrate

- Layout primitives (`Row`, `Column`, `LazyColumn`, `FlowRow`, split panes) stay as they are.
- `SelectionContainer` and `ContextMenuArea` are foundation and fit anywhere.
- A plugin that intentionally looks different — an embedded web view, a canvas — should keep its
  own look and only adopt `JwToolbar` and `JwTheme.colors` at its edges.
