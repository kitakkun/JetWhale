@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.kitakkun.jetwhale.agent.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName

private val ENDPOINT_SCOPE = FqName("com.kitakkun.jetwhale.agent.runtime.JetWhaleEndpointScope")

/**
 * Rewrites `buildMachineWss(port)` into `wss("<build machine address>", port)`.
 *
 * Registered only when an address was supplied, so reaching here means there is one to bake in.
 */
internal class BuildMachineIrGenerationExtension(
    private val address: String,
    private val messageCollector: MessageCollector,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val transformer = BuildMachineCallTransformer(address, pluginContext, messageCollector)
        moduleFragment.transformChildrenVoid(transformer)

        // Worth one line per module that uses it: this bakes a machine-specific address into the
        // output, which is not something to discover only by decompiling it later.
        if (transformer.rewritten > 0) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "JetWhale: baked the build machine address $address into ${transformer.rewritten} " +
                    "$BUILD_MACHINE_WSS_NAME call(s) in '${moduleFragment.name}'.",
            )
        }
    }
}

private class BuildMachineCallTransformer(
    private val address: String,
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {
    var rewritten: Int = 0
        private set

    override fun visitCall(expression: IrCall): IrExpression {
        // Recurse first, so a buildMachineWss nested inside another call's arguments is rewritten too.
        val call = super.visitCall(expression) as IrCall
        val callee = call.symbol.owner
        if (callee.name.asString() != BUILD_MACHINE_WSS_NAME) return call

        // Name alone proves nothing — anyone may declare a buildMachineWss. The declaring interface
        // is what identifies ours.
        val scope = callee.parentClassOrNull ?: return call
        if (scope.kotlinFqName != ENDPOINT_SCOPE) return call

        val wss = scope.findWss() ?: run {
            // The runtime on the classpath declares buildMachineWss but no matching wss. That is a
            // version mismatch between this plugin and jetwhale-agent-runtime, and rewriting into a
            // guess would be worse than refusing.
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "JetWhale: found $BUILD_MACHINE_WSS_NAME but no matching wss(String, Int) on " +
                    "${ENDPOINT_SCOPE.asString()}. The JetWhale Gradle plugin and " +
                    "jetwhale-agent-runtime versions do not match.",
            )
            return call
        }

        val builder = DeclarationIrBuilder(
            pluginContext,
            symbol = currentScope!!.scope.scopeOwnerSymbol,
            startOffset = call.startOffset,
            endOffset = call.endOffset,
        )
        rewritten++
        return builder.buildWssCall(original = call, wss = wss, address = address)
    }
}
