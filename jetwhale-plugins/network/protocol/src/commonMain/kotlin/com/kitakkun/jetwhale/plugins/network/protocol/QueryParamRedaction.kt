package com.kitakkun.jetwhale.plugins.network.protocol

internal fun List<RedactionRule>.redactUrl(url: String): String {
    if (none { it.target == RedactionTarget.URL_QUERY_PARAM }) return url
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return url
    val fragmentStart = url.indexOf('#', startIndex = queryStart)
    val queryEnd = if (fragmentStart < 0) url.length else fragmentStart
    val redactedQuery = url.substring(queryStart + 1, queryEnd)
        .split('&')
        .joinToString("&") { param ->
            val name = param.substringBefore('=')
            val strategy = strategyFor(RedactionTarget.URL_QUERY_PARAM, name.formUrlDecode())
            if ('=' in param && strategy != null) {
                "$name=${strategy.render(param.substringAfter('='))}"
            } else {
                param
            }
        }
    return url.substring(0, queryStart + 1) + redactedQuery + url.substring(queryEnd)
}

internal fun List<RedactionRule>.redactQueryParamsInText(text: String): String {
    if (none { it.target == RedactionTarget.URL_QUERY_PARAM }) return text
    val redacted = StringBuilder()
    var copiedUpTo = 0
    for (name in QUERY_PARAM_NAME_IN_TEXT.findAll(text)) {
        if (name.range.first < copiedUpTo) continue
        val strategy = strategyFor(RedactionTarget.URL_QUERY_PARAM, name.groupValues[1].formUrlDecode()) ?: continue
        val value = checkNotNull(QUERY_PARAM_VALUE.matchAt(text, name.range.last + 1))
        redacted.appendRange(text, copiedUpTo, value.range.first).append(strategy.render(value.value))
        copiedUpTo = value.range.last + 1
    }
    return redacted.appendRange(text, copiedUpTo, text.length).toString()
}

private val QUERY_PARAM_NAME_IN_TEXT = Regex("""[?&]([^?&#=\s]*)=""")
private val QUERY_PARAM_VALUE = Regex("""[^&#\s]*""")
