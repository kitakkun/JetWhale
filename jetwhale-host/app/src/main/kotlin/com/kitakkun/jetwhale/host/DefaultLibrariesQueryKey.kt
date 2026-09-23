package com.kitakkun.jetwhale.host

import com.kitakkun.jetwhale.host.model.LibrariesQueryKey
import com.mikepenz.aboutlibraries.Libs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryId
import soil.query.buildQueryKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultLibrariesQueryKey :
    LibrariesQueryKey by buildQueryKey(
        id = QueryId("DefaultLibrariesQueryKey"),
        fetch = {
            val licenses = checkNotNull(object {}.javaClass.getResource("/licenses.json")) {
                "licenses.json is missing from the host's resources"
            }
            Libs.Builder().withJson(licenses.readText()).build()
        },
    )
