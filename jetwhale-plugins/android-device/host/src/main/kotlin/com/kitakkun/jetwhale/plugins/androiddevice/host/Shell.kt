package com.kitakkun.jetwhale.plugins.androiddevice.host

/**
 * Wraps a value in single quotes for the device's shell, so nothing in it is expanded, split or
 * redirected. A single quote inside the value ends the quoting, emits an escaped quote, and starts
 * it again — the only way to carry one through `sh`.
 */
internal fun singleQuoteForShell(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * `input text` receives its argument through the device's shell and then splits it on spaces
 * itself, so a typed string has to survive both: spaces become `%s`, and the characters the shell
 * would otherwise act on are backslash-escaped.
 */
internal fun escapeForInputText(text: String): String = buildString {
    for (character in text) {
        when {
            character == ' ' -> append("%s")
            character in SHELL_SPECIAL_CHARACTERS -> append('\\').append(character)
            else -> append(character)
        }
    }
}

/**
 * `input text` writes its argument through the key character map, which only covers printable
 * ASCII: anything outside it is dropped or turned into a different character rather than typed.
 */
internal fun unsupportedInputTextCharacters(text: String): List<Char> = text.filterNot { it in PRINTABLE_ASCII }.toSet().toList()

private val SHELL_SPECIAL_CHARACTERS = setOf('\\', '\'', '"', '&', '<', '>', '(', ')', '|', ';', '$', '`')

private val PRINTABLE_ASCII = ' '..'~'
