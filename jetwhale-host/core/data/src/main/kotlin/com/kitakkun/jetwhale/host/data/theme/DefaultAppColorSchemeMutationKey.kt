package com.kitakkun.jetwhale.host.data.theme

import com.kitakkun.jetwhale.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.host.model.AppColorSchemeMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultAppColorSchemeMutationKey(
    private val appearanceDataStore: AppAppearanceRepository,
) : AppColorSchemeMutationKey by buildMutationKey(
    id = MutationId("DefaultAppColorSchemeMutationKey"),
    mutate = { appearanceDataStore.setPreferredColorSchemeId(it) }
)
