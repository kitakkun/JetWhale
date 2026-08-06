@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.kitakkun.jetwhale.agent.compiler

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.FqName

/**
 * Every compiler-API call this plugin makes that JetBrains has moved, or has said they will.
 *
 * The plugin API is `@ExperimentalCompilerApi`: it breaks across minors, routinely. Keeping the
 * volatile calls here means a Kotlin bump that breaks the build breaks it in *one file*, and the
 * escalation path — a per-version compat interface, should one file stop being enough — starts from
 * a surface that is already enumerated rather than scattered through the transformer.
 *
 * Each entry carries [CompatSensitive] saying what moved and when, so dropping support for a Kotlin
 * version tells you exactly which workarounds become dead: `grep 'since = "2.3'`.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
internal annotation class CompatSensitive(
    /** Kotlin version whose change this accommodates. */
    val since: String,
    /** What upstream did. */
    val what: String,
)

/**
 * Rewrites a `buildMachineWss(port)` call into `wss(address, port)`.
 *
 * Both are members of the same interface, so the receiver sits in the same slot on either side and
 * only the host literal is new.
 */
@CompatSensitive(
    since = "2.2.0",
    what = "KT-68003 collapsed dispatchReceiver/extensionReceiver/valueArguments into one flat " +
        "`arguments` list indexed by IrParameterKind. Kotlin 2.4 then removed the old accessors " +
        "outright. Indexing `arguments` is the form that compiles on 2.3 and 2.4 alike — the " +
        "removed accessors must not come back, and `dispatchReceiver`, which survives 2.4 as a " +
        "convenience over arguments[0], is soft-deprecated and deliberately unused here.",
)
internal fun IrBuilderWithScope.buildWssCall(
    original: IrCall,
    wss: IrSimpleFunction,
    address: String,
): IrCall = irCall(wss.symbol).apply {
    // deepCopyWithSymbols because an IrElement cannot have two parents; aliasing the original's
    // expressions here fails IR validation later, far from the cause.
    arguments[BUILD_MACHINE_RECEIVER] = original.arguments[BUILD_MACHINE_RECEIVER]?.deepCopyWithSymbols()
    arguments[WSS_HOST] = irString(address)
    arguments[WSS_PORT] = original.arguments[BUILD_MACHINE_PORT]?.deepCopyWithSymbols()
}

/**
 * `wss(host, port)` on [this], matched by shape so a future overload cannot be picked up by name.
 */
@CompatSensitive(
    since = "2.2.0",
    what = "`IrFunction.parameters` replaced valueParameters/extensionReceiverParameter; its size " +
        "counts the dispatch receiver, which is why the expected arity is 3 rather than 2.",
)
internal fun IrClass.findWss(): IrSimpleFunction? = functions
    .firstOrNull { it.name.asString() == WSS_NAME && it.parameters.size == WSS_ARITY }

/**
 * Whether [this] is `JetWhaleEndpointScope.buildMachineWss`, or any override of it.
 *
 * Overrides matter more than they look: inside `with(someImplementation) { }` the receiver's static
 * type is the implementation, so the call resolves to its override and the interface member is never
 * the callee. Matching only the interface silently skipped those, which is the worst shape of bug
 * here — the untransformed call falls back to contributing no candidate, so nothing complains.
 */
@CompatSensitive(
    since = "2.2.0",
    what = "`overriddenSymbols` is walked directly rather than via the `allOverridden()` helper, " +
        "whose overloads and defaults have moved between minors while the property has not.",
)
internal fun IrSimpleFunction.overridesEndpointScope(): Boolean {
    if (parentClassOrNull?.kotlinFqName == ENDPOINT_SCOPE_FQ_NAME) return true
    return overriddenSymbols.any { it.owner.overridesEndpointScope() }
}

internal val ENDPOINT_SCOPE_FQ_NAME: FqName = FqName("com.kitakkun.jetwhale.agent.runtime.JetWhaleEndpointScope")

internal const val BUILD_MACHINE_WSS_NAME: String = "buildMachineWss"
private const val WSS_NAME = "wss"

// Slot layout, by IrParameterKind. Both functions are interface members, so slot 0 is the dispatch
// receiver on either side; the rest are Regular.
private const val BUILD_MACHINE_RECEIVER = 0
private const val BUILD_MACHINE_PORT = 1
private const val WSS_HOST = 1
private const val WSS_PORT = 2
private const val WSS_ARITY = 3
