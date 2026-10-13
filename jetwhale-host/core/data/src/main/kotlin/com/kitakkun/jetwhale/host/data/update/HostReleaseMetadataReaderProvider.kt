package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface HostReleaseMetadataReaderProvider {
    @Provides
    fun provideHostReleaseMetadataReader(): HostReleaseMetadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases)
}
