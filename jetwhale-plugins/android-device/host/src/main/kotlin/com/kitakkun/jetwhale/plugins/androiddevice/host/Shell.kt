package com.kitakkun.jetwhale.plugins.androiddevice.host

/**
 * Wraps a value in single quotes for the device's shell, so nothing in it is expanded, split or
 * redirected. A single quote inside the value ends the quoting, emits an escaped quote, and starts
 * it again — the only way to carry one through `sh`.
 */
internal fun singleQuoteForShell(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * `input text` receives its argument through the device's shell and then turns every `%s` in it
 * into a space, so a typed string has to survive both: spaces become `%s`, and the whole argument
 * is single-quoted so that nothing in it is expanded, split or dropped as a comment.
 */
internal fun escapeForInputText(text: String): String = singleQuoteForShell(text.replace(" ", INPUT_TEXT_SPACE))

/** What `input text` turns into a space, so it cannot type this sequence as itself. */
internal const val INPUT_TEXT_SPACE = "%s"

/**
 * `input text` writes its argument through the key character map, which only covers printable
 * ASCII: anything outside it is dropped or turned into a different character rather than typed.
 */
internal fun unsupportedInputTextCharacters(text: String): List<Char> = text.filterNot(PRINTABLE_ASCII::contains).toSet().toList()

private val PRINTABLE_ASCII = ' '..'~'
