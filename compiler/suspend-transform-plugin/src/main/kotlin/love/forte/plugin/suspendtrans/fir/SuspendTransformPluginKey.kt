package love.forte.plugin.suspendtrans.fir

import love.forte.plugin.suspendtrans.SuspendTransformBridgeFunDataFir
import love.forte.plugin.suspendtrans.SuspendTransformUserDataFir
import org.jetbrains.kotlin.GeneratedDeclarationKey

sealed class SuspendTransformGeneratedDeclarationKey() : GeneratedDeclarationKey()

data class SuspendTransformPluginKey(val data: SuspendTransformUserDataFir) :
    SuspendTransformGeneratedDeclarationKey() {
    override fun toString(): String {
        return "SuspendTransformPlugin(data=$data)"
    }
}

data class SuspendTransformBridgeFunctionKey(val data: SuspendTransformBridgeFunDataFir) :
    SuspendTransformGeneratedDeclarationKey()

/**
 * The v3 logic for generating suspend bridge functions: generate body in FIR stage.
 */
object SuspendTransformK2V3Key : SuspendTransformGeneratedDeclarationKey()
