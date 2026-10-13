package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Objects

/**
 * A host release version, ordered the way JetWhale tags are: on the three numbers, then by stage
 * (alpha, beta, rc, then a final release), then on the stage number as a number.
 *
 * `1.0.0-alpha9` < `1.0.0-alpha10` < `1.0.0-beta1` < `1.0.0-rc1` < `1.0.0` < `1.0.1-alpha1`
 *
 * Strict SemVer compares `alpha10` and `alpha9` as text and gets them backwards, so this order is
 * defined here once, for the host, the launcher and the release job. `alpha09` and `alpha9` are the
 * same version, since the early tags are zero-padded. In JSON a version is its [name].
 *
 * @property name The text the version was parsed from, as its tag, its assets and its version
 * directory spell it. Two spellings of one version are equal, and each keeps its own name.
 */
@Serializable(with = HostVersionSerializer::class)
class HostVersion private constructor(
    val name: String,
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

    override fun equals(other: Any?): Boolean = other is HostVersion && compareTo(other) == 0

    override fun hashCode(): Int = Objects.hash(major, minor, patch, stage, stageNumber)

    override fun toString(): String = name

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
                name = tag,
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

object HostVersionSerializer : KSerializer<HostVersion> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.kitakkun.jetwhale.host.release.HostVersion", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: HostVersion) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): HostVersion {
        val name = decoder.decodeString()
        return HostVersion.parse(name) ?: throw SerializationException("$name is not a release version")
    }
}
