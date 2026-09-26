package com.kitakkun.jetwhale.host.data.window

import com.kitakkun.jetwhale.host.model.SaveSidebarWidthMutationKey
import com.kitakkun.jetwhale.host.model.WindowStateRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<SaveSidebarWidthMutationKey>())
@Inject
class DefaultSaveSidebarWidthMutationKey(
    private val windowStateRepository: WindowStateRepository,
) : SaveSidebarWidthMutationKey,
    MutationKey<Unit, Float> by buildMutationKey(
        id = MutationId("saveSidebarWidth"),
        mutate = { widthDp: Float -> windowStateRepository.saveSidebarWidth(widthDp) },
    )
