package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import kotlinx.coroutines.Job

/** Every coroutine with its suspension stack, where the platform and the app make that possible. */
internal expect fun dumpCoroutines(): CoroutineDump

/** The stacks of the coroutine whose `Job` is [job], known to the host as [id], where the platform and the app make that possible. */
internal expect fun describeCoroutine(id: String, job: Job): CoroutineDetail
