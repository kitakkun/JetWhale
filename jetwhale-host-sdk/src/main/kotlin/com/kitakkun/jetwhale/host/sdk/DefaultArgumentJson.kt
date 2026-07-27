package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.json.Json

/**
 * Default format for [JetWhaleMcpCommand] arguments, tuned for input written by an AI agent: it
 * ignores unknown keys (so a value read back from another tool round-trips even if the agent
 * annotated it), accepts a scalar of the wrong JSON type where the value is unambiguous, and
 * matches enum entries case-insensitively — the same tolerance the scalar `enum` declarator has.
 *
 * Trailing commas and comments are accepted too. Those are lexer settings, so they do not affect
 * tool arguments — the MCP transport hands those over already parsed — but they do apply when a
 * command uses this same format from [JetWhaleMcpCommand.execute] to parse a string that holds
 * JSON, such as a captured response body.
 *
 * `coerceInputValues` is deliberately off: it would swap an unrecognized enum entry for the
 * property's default, turning a caller mistake into a silently different result.
 *
 * Build a variant with `Json(from = DefaultArgumentJson) { ... }` and pass it to the command's
 * constructor.
 */
@ExperimentalJetWhaleApi
public val DefaultArgumentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    decodeEnumsCaseInsensitive = true
    allowTrailingComma = true
    allowComments = true
}
