package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.AppAppearanceRepository
import com.kitakkun.jetwhale.host.model.AppLanguageMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultAppLanguageMutationKey(
    private val appAppearanceRepository: AppAppearanceRepository,
) : AppLanguageMutationKey by buildMutationKey(
    id = MutationId("DefaultAppLanguageMutationKey"),
    mutate = { appAppearanceRepository.updateAppLanguage(it) },
)
