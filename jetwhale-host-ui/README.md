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
| Structure | `JwToolbar`, `JwTabRow` + `JwTab`, `JwSplitPane`, `JwPanel`, `JwSectionHeader`, `JwStatusLine`, `JwHorizontalDivider` / `JwVerticalDivider`, `JwDialog` / `JwDialogSurface` |
| Rows and labels | `JwTable` + `JwTableColumn`, `JwListItem`, `JwTreeRow`, `JwKeyValueRow`, `JwCodeBlock`, `JwTag`, `JwCountBadge`, `JwStatusDot`, `JwBanner`, `JwEmptyState` |
| Controls | `JwButton` (text and slot overloads), `JwIconButton` + `JwIcon`, `JwTooltip`, `JwTextField`, `JwSearchField`, `JwFormField`, `JwSwitch`, `JwCheckbox`, `JwSegmentedButtons`, `JwDropdownButton` + `JwDropdownMenu` + `JwMenuItem` |

Every component is sized for a desktop tool window — `JwMetrics.controlHeight` (28dp) controls,
13sp body text, 4dp corners — and takes its colors from the theme, so it follows the user's
light, dark or custom scheme without any work on the caller's side.

## Coming from Material 3

Where a component stands in for a Material one it keeps the Material name; where it deliberately
does less, the name differs so the difference is not a surprise.

| Material 3 | jetwhale-host-ui | Difference |
|------------|------------------|------------|
| `Button` / `OutlinedButton` / `TextButton` | `JwButton` with `style = Primary` / `Secondary` / `Text` | One composable, 28dp tall, plus `tone` for destructive actions |
| `IconButton` | `JwIconButton` | Takes a `tooltip`, which is also its accessibility name |
| `Switch`, `Checkbox` | `JwSwitch`, `JwCheckbox` | Compact; `JwCheckbox` includes its label |
| `OutlinedTextField` | `JwTextField`, `JwSearchField` | Label lives in `JwFormField` above the field, not inside it |
| `ExposedDropdownMenuBox` | `JwDropdownButton` | Caller owns `expanded`; a plain button, not a text field |
| `DropdownMenu`, `DropdownMenuItem` | `JwDropdownMenu`, `JwMenuItem` | 26dp rows; `selected` draws a check mark |
| `TopAppBar` | `JwToolbar` | 36dp bar for a pane, no scroll behavior, no navigation icon slot |
| `TabRow` / `SecondaryTabRow`, `Tab` | `JwTabRow`, `JwTab` | Tabs size to their label; optional `count` |
| `Card` / `OutlinedCard` | `JwPanel` | Optional header strip with actions |
| `ListItem`, `NavigationDrawerItem` | `JwListItem` | 28dp; one line plus an optional supporting line |
| — | `JwTable` | Lazy rows under a header, columns declared once as `JwTableColumn`s |
| `SingleChoiceSegmentedButtonRow` | `JwSegmentedButtons` | One control tall; takes the options as a list |
| — | `JwSplitPane` | Draggable list-and-detail split; hoist `JwSplitPaneState` to persist it |
| — | `JwCodeBlock`, `JwStatusLine`, `JwCountBadge` | Monospace block with copy; one-line status strip; count pill |
| — | `JwTreeRow` | Indented, expandable list row |
| `AssistChip` / `FilterChip`, `Badge` | `JwTag`, `JwStatusDot` | 18dp tag with a `JwTone`; 8dp dot |
| `AlertDialog` | `JwDialog` (`JwDialogSurface` for custom chrome) | Title bar with a close button, footer with dismiss/confirm |
| `Snackbar` (informational use) | `JwBanner` | Inline strip, not a floating overlay |
| `HorizontalDivider`, `VerticalDivider` | `JwHorizontalDivider`, `JwVerticalDivider` | Same |
| `MaterialTheme` | `JwTheme` | Applies Material with the tool-window scale, plus `JwTheme.colors` |

## Conventions

- **Names and order follow Material 3** wherever a component has a Material counterpart, so
  what you know from `TextField`, `ListItem`, `DropdownMenuItem`, `Tab`, `AlertDialog` and
  `TopAppBar` carries over: `leadingIcon` / `trailingIcon` on fields and menu items,
  `leadingContent` / `trailingContent` on list and tree rows, `navigationIcon` / `actions` on the
  toolbar, `text` for a dialog's body, `selected, onClick` first on a tab. Where the library
  simplifies, it does so the same way everywhere: a `String` in place of a composable slot for
  titles, labels and placeholders, with a slot overload where richer content is common
  (`JwButton`, `JwListItem`).
- **Components without a Material counterpart** put required data first, then `modifier`, then
  options with defaults, then composable slots last — so a trailing lambda is always the main
  content. Slots receive the component's content color through `LocalContentColor`, so a `JwIcon`
  inside needs no tint.
- **Tones**: `JwTone` (`Neutral`, `Accent`, `Success`, `Warning`, `Error`, `Info`) is the one
  vocabulary for semantic color, shared by `JwTag`, `JwStatusDot`, `JwBanner`, `JwButton` and
  `JwMenuItem`.
- **Accessibility**: a `JwIconButton`'s `tooltip` is also its accessibility name, and the
  buttons a component creates for you take a label (`JwDialog.closeLabel`,
  `JwSearchField.clearLabel`, `JwBanner.dismissLabel`, `JwSwitch.contentDescription`) rather than
  a baked-in English word. `JwListItem`, `JwTreeRow`, `JwTab` and `JwMenuItem` expose `selected`;
  collapsible headers and tree chevrons expose `expand`/`collapse` actions. The host's MCP tools
  read the same semantics tree, so an agent driving a plugin sees the same names and states a
  screen reader would.
- **Interaction feedback**: components suppress the Material ripple and draw a hover tint and an
  accent focus ring instead. The ring marks whatever holds focus — a click moves focus on desktop,
  so the control last clicked keeps it until focus moves on. `Modifier.jwFocusRing(interactionSource,
  shape)` gives a custom control the same ring; it is drawn just outside the bounds, so a parent
  that clips (`JwPanel`, `JwDialog`) trims it on a row flush with the edge.
- **Disabled vs muted**: `enabled = false` removes interaction and greys the row; `muted = true`
  (on `JwListItem` and `JwTreeRow`) only de-emphasizes it — for an item that is present but not
  current.
- **Sizes**: `JwSpacing` steps (`tiny` 2dp … `huge` 24dp) are the gaps and paddings; `JwMetrics`
  holds the few sizes every component shares (control and toolbar height, icon size, border and
  focus strokes); a size that belongs to one component lives in that component's `Defaults`
  object — `JwDialogDefaults.width`, `JwTagDefaults.height`, `JwMenuDefaults.itemHeight`, and so
  on — the way Material's `ButtonDefaults` does. Lay out custom content on the same values and it
  lines up.

## Compatibility

A plugin compiles against this library but runs against the copy the host ships, so a signature
that changes between releases breaks already-built plugins with `NoSuchMethodError`. Until 1.0 the
API may still move; from 1.0 on, a published composable's parameter list is frozen — new knobs
arrive as a new overload, and the old one stays (deprecated at most). `JwExtendedColors` has no
public constructor for the same reason: obtain one with `JwExtendedColors.from` and adjust it with
`copy`, and it can grow new colors without breaking callers.

Material 3 and any other Compose library keep working inside a plugin; prefer these components
where they have what you need, and drop to Material or your own composables for the rest.
