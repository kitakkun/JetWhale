package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** The directory in the package that holds the host version installed with it: its `release.json` and jar. */
class BundledHostDirectory(private val path: Path) {
    /** Reads the bundled version. Null when the directory has no readable metadata. */
    fun readHostVersion(): ChosenHostVersion? {
        val text = try {
            Files.readString(path.resolve(InstalledHostVersion.METADATA_FILE_NAME))
        } catch (_: IOException) {
            return null
        }
        val metadata: HostReleaseMetadata = (HostReleaseMetadata.decode(text) as? HostReleaseMetadataResult.Read)?.metadata
            ?: return null
        return ChosenHostVersion(
            version = metadata.version,
            metadata = metadata,
            jar = path.resolve(JAR_FILE_NAME),
            isBundled = true,
        )
    }

    companion object {
        /**
         * The bundled jar's name. It does not end in `.jar`, because jpackage puts every such file
         * under the app directory on the launcher's own class path.
         */
        const val JAR_FILE_NAME = "jetwhale-host.bundled"
    }
}
