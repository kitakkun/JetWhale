package com.kitakkun.jetwhale.host.model

/** Where the host looks up its releases: GitHub's releases API, or a local source in a test build. */
@JvmInline
value class HostReleaseSource(val releasesUrl: String)
