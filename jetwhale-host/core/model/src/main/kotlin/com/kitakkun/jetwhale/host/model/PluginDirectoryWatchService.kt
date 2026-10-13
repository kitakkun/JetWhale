package com.kitakkun.jetwhale.host.model

/**
 * Notices jars added to, replaced in or removed from the plugins directory while the host runs, and
 * hands each settled change to [PluginTrustService.onPluginJarsChanged]. Without it the directory is
 * read only at startup.
 */
interface PluginDirectoryWatchService {
    /** Starts watching from the directory's current contents; those are not reported as changes. */
    fun start()

    fun stop()
}
