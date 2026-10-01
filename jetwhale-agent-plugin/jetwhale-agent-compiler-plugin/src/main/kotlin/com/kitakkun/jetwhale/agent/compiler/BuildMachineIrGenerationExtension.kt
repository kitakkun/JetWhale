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
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

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
        if (transformer.rewrittenCallCount > 0) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "JetWhale: baked the build machine address $address into ${transformer.rewrittenCallCount} " +
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
    var rewrittenCallCount: Int = 0
        private set

    override fun visitCall(expression: IrCall): IrExpression {
        val isOurs = expression.symbol.owner.isBuildMachineWss()
        val transformed = super.visitCall(expression)
        if (!isOurs) return transformed

        val call = transformed as? IrCall ?: run {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "JetWhale: a $BUILD_MACHINE_WSS_NAME call was replaced by another compiler plugin " +
                    "before this one ran, so no address could be baked into it. It will contribute no " +
                    "endpoint at runtime — write the address out with wss() if you need that candidate.",
            )
            return transformed
        }
        val callee = call.symbol.owner

        val scope = callee.parentClassOrNull ?: return call
        val wss = scope.findWss() ?: run {
            messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "JetWhale: found $BUILD_MACHINE_WSS_NAME but no matching wss(String, Int) on " +
                    "${ENDPOINT_SCOPE_FQ_NAME.asString()}. jetwhale-agent-compiler-plugin and " +
                    "jetwhale-agent-runtime are at versions that do not agree; align them.",
            )
            return call
        }

        val builder = DeclarationIrBuilder(
            pluginContext,
            symbol = currentScope!!.scope.scopeOwnerSymbol,
            startOffset = call.startOffset,
            endOffset = call.endOffset,
        )
        rewrittenCallCount++
        return builder.buildWssCall(original = call, wss = wss, address = address)
    }
}
