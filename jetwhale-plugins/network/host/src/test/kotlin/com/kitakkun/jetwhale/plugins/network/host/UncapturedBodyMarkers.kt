package com.kitakkun.jetwhale.plugins.network.host

/** One of each marker recorded as a TEXT body in place of a body that was not captured. */
internal val UNCAPTURED_BODY_MARKERS = listOf(
    "<streaming request body>",
    "<streaming response body>",
    "<websocket upgrade>",
    "<Content-Encoding: gzip body>",
    "<image/png body over the 2097152-byte maxImageBytes limit>",
    "<body withheld: it names a redacted field but could not be parsed to redact it>",
    "<application/json>",
    "<multipart/form-data; boundary=5d3a-1f7c9e2b>",
)

/** Real bodies that are a single XML or HTML element, the same `<...>` shape as a marker. */
internal val SINGLE_ELEMENT_BODIES = listOf("<br>", "<root/>", """<a href="x"/>""", """<img src="a/b.png">""")
