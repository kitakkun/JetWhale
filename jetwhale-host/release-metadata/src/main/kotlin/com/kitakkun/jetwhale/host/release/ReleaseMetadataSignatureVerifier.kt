package com.kitakkun.jetwhale.host.release

/**
 * Decides whether a release's metadata file came out of the release job, before anything in it is
 * trusted. The metadata pins each jar's SHA-256, so trusting the metadata extends to the jars.
 */
fun interface ReleaseMetadataSignatureVerifier {
    /**
     * @param signature The release's `jetwhale-host-<version>.json.sig` asset, or null when it has
     * none.
     */
    fun isTrusted(metadata: ByteArray, signature: ByteArray?): Boolean

    companion object {
        /**
         * The check JetWhale releases go through, in the launcher and in the host alike.
         *
         * Releases are not signed yet, so every metadata file is trusted and the jar hashes it pins
         * only catch corruption and truncated downloads. Once a release key exists, a verifier that
         * requires the signature and checks it against the embedded public keys goes here.
         */
        val JetWhaleReleases: ReleaseMetadataSignatureVerifier = ReleaseMetadataSignatureVerifier { _, _ -> true }
    }
}
