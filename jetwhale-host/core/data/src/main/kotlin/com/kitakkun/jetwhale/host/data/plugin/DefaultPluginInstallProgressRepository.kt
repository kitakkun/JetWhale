package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallProgressRepository : PluginInstallProgressRepository {
    private val mutableProgressFlow = MutableStateFlow<PluginInstallProgress?>(null)
    override val progressFlow: Flow<PluginInstallProgress?> = mutableProgressFlow

    override fun update(progress: PluginInstallProgress?) {
        mutableProgressFlow.value = progress
    }
}
