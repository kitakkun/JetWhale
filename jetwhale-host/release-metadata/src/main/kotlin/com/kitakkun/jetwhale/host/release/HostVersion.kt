package com.kitakkun.jetwhale.host.release

/**
 * A host release version, ordered the way JetWhale tags are: on the three numbers, then by stage
 * (alpha, beta, rc, then a final release), then on the stage number as a number.
 *
 * `1.0.0-alpha9` < `1.0.0-alpha10` < `1.0.0-beta1` < `1.0.0-rc1` < `1.0.0` < `1.0.1-alpha1`
 *
 * Strict SemVer compares `alpha10` and `alpha9` as text and gets them backwards, so this order is
 * defined here once, for the host, the launcher and the release job. `alpha09` and `alpha9` are the
 * same version, since the early tags are zero-padded.
 */
data class HostVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage,
    val stageNumber: Int,
) : Comparable<HostVersion> {
    enum class Stage {
        Alpha,
        Beta,
        Rc,
        Final,
    }

    override fun compareTo(other: HostVersion): Int = compareValuesBy(
        this,
        other,
        HostVersion::major,
        HostVersion::minor,
        HostVersion::patch,
        HostVersion::stage,
        HostVersion::stageNumber,
    )

    companion object {
        private val TAG = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d+))?""")

        /**
         * Parses a release tag. Returns null for a `-SNAPSHOT` tag and for anything else that is not
         * `MAJOR.MINOR.PATCH` with an optional `-alphaN`, `-betaN` or `-rcN`, N from 1 to 199: such
         * a tag is never an update candidate. The Windows installer's version scheme relies on that
         * bound.
         */
        fun parse(tag: String): HostVersion? {
            val match = TAG.matchEntire(tag) ?: return null
            val (major, minor, patch, stageName, stageNumber) = match.destructured
            return HostVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                stage = when (stageName) {
                    "alpha" -> Stage.Alpha
                    "beta" -> Stage.Beta
                    "rc" -> Stage.Rc
                    else -> Stage.Final
                },
                stageNumber = if (stageName.isEmpty()) 0 else stageNumber.toIntOrNull()?.takeIf { it in 1..199 } ?: return null,
            )
        }
    }
}
