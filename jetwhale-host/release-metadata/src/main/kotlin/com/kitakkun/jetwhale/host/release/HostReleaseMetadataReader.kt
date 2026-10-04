package com.kitakkun.jetwhale.host.release

/**
 * Reads a release's metadata file once [signatureVerifier] trusts it. The launcher before each start
 * and the host before a download both read metadata through here, so a signature check added to the
 * verifier covers both.
 */
class HostReleaseMetadataReader(private val signatureVerifier: ReleaseMetadataSignatureVerifier) {
    fun read(metadata: ByteArray, signature: ByteArray?): HostReleaseMetadataResult {
        if (!signatureVerifier.isTrusted(metadata, signature)) return HostReleaseMetadataResult.Untrusted
        return HostReleaseMetadata.decode(metadata.decodeToString())
    }
}
