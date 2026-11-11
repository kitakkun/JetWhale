package com.kitakkun.jetwhale.plugins.mirror.host

/** Mirror's commands for previews and docs screenshots, which draw the controls but never run a command. */
internal val previewMcpCommands = MirrorMcpCommands(NoMirrorDevices)

private object NoMirrorDevices : MirrorDevices {
    override val selectedId: String? = null

    override suspend fun refresh(): List<MirrorDevice> = emptyList()

    override fun resolve(deviceId: String?): MirrorDevice = error("A preview has no devices")

    override suspend fun saveScreenshot(device: MirrorDevice): Capture = error("A preview has no devices")

    override suspend fun startRecording(device: MirrorDevice) = Unit

    override suspend fun startRecordings(deviceIds: List<String>?): List<RecordingResult> = emptyList()

    override suspend fun stopRecording(deviceId: String?): Capture = error("A preview has no devices")

    override suspend fun stopRecordings(deviceIds: List<String>?): List<RecordingResult> = emptyList()

    override fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> = emptyList()
}
