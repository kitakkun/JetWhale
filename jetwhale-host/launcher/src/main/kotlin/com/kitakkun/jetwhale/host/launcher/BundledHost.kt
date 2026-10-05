package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The host version installed with the package. The package vouches for it, so it is not verified
 * again; it is also the floor, never deleted and never set aside.
 */
class BundledHost(val start: HostStart) {
    companion object {
        /**
         * The bundled jar's name. It does not end in `.jar`, because jpackage puts every such file
         * under the app directory on the launcher's own class path.
         */
        const val JAR_FILE_NAME = "jetwhale-host.bundled"

        /**
         * Reads the bundled version from [directory], which holds its `release.json` and its jar.
         * Null when the directory has no readable metadata.
         */
        fun read(directory: Path): BundledHost? {
            val text = try {
                Files.readString(directory.resolve(InstalledHostVersion.METADATA_FILE_NAME))
            } catch (_: IOException) {
                return null
            }
            val metadata: HostReleaseMetadata = (HostReleaseMetadata.decode(text) as? HostReleaseMetadataResult.Read)?.metadata
                ?: return null
            return BundledHost(
                HostStart(
                    version = metadata.version,
                    metadata = metadata,
                    jar = directory.resolve(JAR_FILE_NAME),
                    isBundled = true,
                ),
            )
        }
    }
}
