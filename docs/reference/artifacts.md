# Artifacts & Compatibility

Everything is published to Maven Central under the group **`com.kitakkun.jetwhale`**, all at the
same version as the host release they belong to.

## Artifacts

| Artifact | Where it goes |
|----------|---------------|
| `jetwhale-agent-runtime` | The app being debugged. Brings `jetwhale-agent-sdk` and `jetwhale-protocol-core` with it, so you rarely need to name those. |
| `jetwhale-agent-sdk` | Only when a module writes agent plugins without depending on the runtime. |
| `jetwhale-protocol-core` | The shared module of a plugin pair, for `JetWhaleEvent` / `JetWhaleRequest`. |
| `jetwhale-annotations` | `@McpDescription` and the opt-in markers `@ExperimentalJetWhaleApi` / `@InternalJetWhaleApi`. Reaches both SDKs transitively, so it rarely needs declaring. |
| `jetwhale-host-sdk` | A host plugin module, as `compileOnly` — see [Developing Plugins](/guide/developing-plugins). |
| `jetwhale-host-ui` | The host's theme and component library, for a host plugin module as `compileOnly` — see [Theming and components](/guide/developing-plugins#theming-and-components-jetwhale-host-ui). |
| `jetwhale-host-gradle-plugin` | Applied as the `com.kitakkun.jetwhale.host` Gradle plugin id. |
| `jetwhale-agent-gradle-plugin` | Applied as the `com.kitakkun.jetwhale.agent` Gradle plugin id — see [Baking in the build machine's address](/guide/connecting#baking-in-the-build-machine-s-address). |
| `jetwhale-agent-compiler-plugin` | The Kotlin compiler plugin the above points at. Never named directly. |
| `jetwhale-qa-agent` | Run, not depended on — see [QA Agent](/guide/qa-agent). |
| `jetwhale-network-inspector`, `-agent`, `-agent-ktor`, `-agent-okhttp`, `-protocol` | [Network Inspector](/guide/network-inspector). |
| `jetwhale-nav3-navigator`, `jetwhale-nav3-agent`, `jetwhale-nav3-protocol` | [Nav3 Navigator](/guide/nav3-navigator). |
| `jetwhale-compose-semantics-inspector`, `-agent`, `-protocol` | [Compose Semantics Inspector](/guide/compose-semantics-inspector). |
| `jetwhale-storage-inspector`, `-agent`, `-agent-datastore`, `-protocol` | [Storage Inspector](/guide/storage-inspector). |
| `jetwhale-debug-actions`, `-agent`, `-agent-compose`, `-protocol` | [Debug Actions](/guide/debug-actions). |
| `jetwhale-device-mirror` | [Device Mirror](/guide/device-mirror); host-only, no app artifact. |

In each plugin row, the first artifact (the one without an `-agent` or `-protocol` suffix) is the
**host** plugin jar. You install it into the host rather than into your app; see
[Host Settings → Plugins](/guide/host-settings#plugins).

## Kotlin Multiplatform targets

The multiplatform artifacts ship `jvm`, `android`, `js(IR)`, `wasmJs`, `iosArm64`,
`iosSimulatorArm64`, `macosArm64`, `mingwX64`, `linuxX64` and `linuxArm64`. There is **no `iosX64`
and no `macosX64`**, so an Intel Mac and the Intel iOS simulator cannot resolve them, and there are
no watchOS or tvOS targets.

## Kotlin compatibility

JetWhale artifacts are built with a recent Kotlin release (currently **2.4.10**), and your app needs
**Kotlin 2.3 or newer** to use them. With an older Kotlin, the build fails with metadata-version
errors: upgrade your app's Kotlin plugin, or pick an older JetWhale release built with a matching
Kotlin.

If you cannot upgrade, `-Xskip-metadata-version-check` is an unofficial escape hatch:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
```

It only silences the metadata check and does not guarantee compatibility. It is verified to compile
and run against the current release with Kotlin 2.0–2.2, but it is unsupported by JetBrains and may
break with future releases, especially around `inline` functions. Prefer upgrading Kotlin.

`buildMachineWss` relies on a Kotlin compiler plugin with a narrower range; see
[Baking in the build machine's address](/guide/connecting#baking-in-the-build-machine-s-address).
