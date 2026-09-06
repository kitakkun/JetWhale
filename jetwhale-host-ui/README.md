# jetwhale-host-ui

The theme and component library the JetWhale host is built from, published so a host plugin can
look like one more pane of the same tool. Every public name carries the `Jw` prefix, and the
library depends on Compose foundation alone — no Material — so its API does not move when
Material's does.

```kotlin
dependencies {
    // Provided by the host at runtime, like jetwhale-host-sdk: compileOnly, never bundled.
    compileOnly("com.kitakkun.jetwhale:jetwhale-host-ui:<version>")
}
```

The host wraps a plugin's `Content()` in `JwTheme` before calling it, so inside a plugin the
components already draw with the host's configured scheme. Call `JwTheme(darkTheme = …)` yourself
only in previews and tests. The host also keeps a Material 3 theme derived from the same colors
around plugin content, so Material widgets still work inside a plugin; they are simply not what
this library is made of.

## What is here

| Layer | Names |
|-------|-------|
| Theme | `JwTheme` (+ `JwTheme.colors`, `JwTheme.textStyles`, `JwTheme.isDark`), `JwColors`, `JwTextStyles`, `JwShapes`, `JwSpacing`, `JwMetrics`, `JwTone`, `JwIcons`, `LocalJwContentColor`, `LocalJwTextStyle` |
| Structure | `JwToolbar`, `JwTabRow` + `JwTab`, `JwSplitPane`, `JwPanel`, `JwSectionHeader`, `JwStatusLine`, `JwHorizontalDivider` / `JwVerticalDivider`, `JwDialog` / `JwDialogSurface` |
| Rows and labels | `JwTable` + `JwTableColumn`, `JwListItem`, `JwTreeRow`, `JwKeyValueRow`, `JwCodeBlock`, `JwTag`, `JwCountBadge`, `JwStatusDot`, `JwBanner`, `JwEmptyState` |
| Text and icons | `JwText`, `JwIcon`, `JwProgressIndicator` |
| Controls | `JwButton` (text and slot overloads), `JwIconButton`, `JwTooltip`, `JwTextField`, `JwSearchField`, `JwFormField`, `JwSwitch`, `JwCheckbox`, `JwTriStateCheckbox`, `JwSegmentedButtons`, `JwDropdownButton` + `JwDropdownMenu` + `JwMenuItem` |

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
| `Switch`, `Checkbox`, `TriStateCheckbox` | `JwSwitch`, `JwCheckbox`, `JwTriStateCheckbox` | Compact; the checkboxes include their label |
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
| `SnackbarHost`, `SnackbarHostState` | `JwSnackbarHost`, `JwSnackbarHostState` | Same queue and `showSnackbar`; a dark strip at the bottom of the box it is placed in |
| `Snackbar` (informational use) | `JwBanner` | Inline strip, not a floating overlay |
| `Surface` | `JwSurface` | A background with its content color; no elevation, no click handling |
| `TooltipBox` | `JwTooltip` | Plain text only; `anchor` picks below or beside |
| `HorizontalDivider`, `VerticalDivider` | `JwHorizontalDivider`, `JwVerticalDivider` | Same |
| `Text`, `Icon`, `CircularProgressIndicator` | `JwText`, `JwIcon`, `JwProgressIndicator` | Take the enclosing control's content color and text style; the spinner is therefore text-colored, not accent, unless given a `color` |
| `MaterialTheme` | `JwTheme` | Its own tokens: `JwColors` (`accent`, `surface`, `textSecondary`, the tones…) and `JwTextStyles` (`title`, `body`, `label`, `code`…) |
| `MaterialTheme.colorScheme.primary` / `.onSurfaceVariant` / `.outline` | `JwTheme.colors.accent` / `.textSecondary` / `.controlBorder` | Names say what the color is for, not where it sits in a Material palette |
| `MaterialTheme.typography.bodyMedium` / `.labelSmall` | `JwTheme.textStyles.body` / `.labelSmall` | Seven styles instead of fifteen |

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
  content. Slots receive the component's content color through `LocalJwContentColor`, so a `JwIcon`
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
- **Interaction feedback**: components draw no ripple; a hover tint and an accent focus ring take
  its place. The ring marks whatever holds focus — a click moves focus on desktop,
  so the control last clicked keeps it until focus moves on. `Modifier.jwFocusRing(interactionSource,
  shape)` gives a custom control the same ring; it is drawn just outside the bounds, so a parent
  that clips (`JwPanel`, `JwDialog`) trims it on a row flush with the edge.
- **Content color and text style flow down**: a control that paints a background provides
  `LocalJwContentColor` and `LocalJwTextStyle` for its content, and `JwText` / `JwIcon` read them.
  Use `JwText` rather than Material's `Text` inside Jw components, or the text keeps Material's
  color instead of the control's.
- **Contrast**: the built-in schemes meet WCAG AA — 4.5:1 for every text color on the surfaces
  it is drawn on, including the tones, and 3:1 for control borders and the focus ring. Hairline
  dividers are decorative and lighter than that on purpose. `textDisabled` is the one color below
  the text threshold and is used only on disabled controls; anything quiet but readable
  (placeholders, counts, hints) is `textSecondary`.
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
arrive as a new overload, and the old one stays (deprecated at most). `JwColors` and `JwTextStyles`
have no public constructor for the same reason: obtain one from `JwColors.light()` / `dark()` or
`JwTextStyles.default()` and adjust it with `copy`, and they can grow without breaking callers.
The library's own dependencies are Compose runtime, foundation and ui — the parts of Compose that
change least — plus `jetwhale-host-sdk`.

Material 3 and any other Compose library keep working inside a plugin; prefer these components
where they have what you need, and drop to Material or your own composables for the rest.
