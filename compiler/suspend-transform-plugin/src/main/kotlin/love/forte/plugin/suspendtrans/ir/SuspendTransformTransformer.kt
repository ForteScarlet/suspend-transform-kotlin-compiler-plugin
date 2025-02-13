package love.forte.plugin.suspendtrans.ir

import love.forte.plugin.suspendtrans.*
import love.forte.plugin.suspendtrans.fir.SuspendTransformBridgeFunctionKey
import love.forte.plugin.suspendtrans.fir.SuspendTransformGeneratedDeclarationKey
import love.forte.plugin.suspendtrans.fir.SuspendTransformK2V3Key
import love.forte.plugin.suspendtrans.fir.SuspendTransformPluginKey
import love.forte.plugin.suspendtrans.utils.*
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.getSourceLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.isAnnotationWithEqualFqName
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.wasm.ir.source.location.SourceLocation

/**
 *
 * @author ForteScarlet
 */
class SuspendTransformTransformer(
    private val configuration: SuspendTransformConfiguration,
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoidWithContext() {

    // TODO What should be used in K2?
    private val reporter = kotlin.runCatching {
        // error: "This API is not supported for K2"
        pluginContext.createDiagnosticReporter(PLUGIN_REPORT_ID)
    }.getOrNull()


    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        resolveFunctionBodyByDescriptor(declaration, declaration.descriptor)

        return super.visitFunctionNew(declaration)
    }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        val getter = declaration.getter ?: return super.visitPropertyNew(declaration)
        resolveFunctionBodyByDescriptor(getter, declaration.descriptor, declaration)

        return super.visitPropertyNew(declaration)
    }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    private fun resolveFunctionBodyByDescriptor(
        declaration: IrFunction,
        descriptor: CallableDescriptor,
        property: IrProperty? = null
    ): IrFunction? {
        // K2
        val pluginKey = if (property != null) {
            // from property
            (property.origin as? IrDeclarationOrigin.GeneratedByPlugin)
                ?.pluginKey as? SuspendTransformGeneratedDeclarationKey
        } else {
            (declaration.origin as? IrDeclarationOrigin.GeneratedByPlugin)
                ?.pluginKey as? SuspendTransformGeneratedDeclarationKey
        }

        // K1 ?
        val userData = descriptor.getUserData(SuspendTransformUserDataKey)

        val generatedOriginFunction: IrFunction? = when {
            // K2 v3, 如果已经有body了，则不再需要生成，直接跳过。
            declaration.body != null -> return null

            pluginKey != null -> {
                when (pluginKey) {
                    // K2 v3, 通常是已经完成body生成的，跳过（应该到不了这一步，因为 body != null 已经在上面被检测过了
                    is SuspendTransformK2V3Key -> null

                    // K2 v2
                    is SuspendTransformBridgeFunctionKey -> {
                        val callableFunction =
                            pluginContext.referenceFunctions(pluginKey.data.transformer.transformFunctionInfo.toCallableId())
                                .firstOrNull()
                                ?: throw IllegalStateException("Transform function ${pluginKey.data.transformer.transformFunctionInfo} not found")

                        resolveBridgeFunctionBody(
                            pluginKey,
                            declaration,
                            callableFunction
                        )

                        null
//                            .also { generatedOriginFunction ->
//                            if (property != null) {
//                                // NO! BACKING! FIELD!
//                                property.backingField = null
//                            }
//
//                            if (generatedOriginFunction != null) {
//                                postProcessGenerateOriginFunction(
//                                    generatedOriginFunction,
//                                    pluginKey.data.transformer.originFunctionIncludeAnnotations
//                                )
//                            }
//                        }
                    }

                    is SuspendTransformPluginKey -> {
                        val callableFunction =
                            pluginContext.referenceFunctions(pluginKey.data.transformer.transformFunctionInfo.toCallableId())
                                .firstOrNull()
                                ?: throw IllegalStateException("Transform function ${pluginKey.data.transformer.transformFunctionInfo} not found")

                        resolveFunctionBody(
                            pluginKey,
                            declaration,
                            { f ->
                                pluginKey.data.originSymbol.checkSame(pluginKey.data.markerId, f)
                            },
                            callableFunction
                        ).also { generatedOriginFunction ->
                            if (property != null) {
                                // NO! BACKING! FIELD!
                                property.backingField = null
                            }

                            if (generatedOriginFunction != null) {
                                postProcessGenerateOriginFunction(
                                    generatedOriginFunction,
                                    pluginKey.data.transformer.originFunctionIncludeAnnotations
                                )
                            }
                        }
                    }
                }


            }

            userData != null -> {
                val callableFunction =
                    pluginContext.referenceFunctions(userData.transformer.transformFunctionInfo.toCallableId())
                        .firstOrNull()
                        ?: throw IllegalStateException("Transform function ${userData.transformer.transformFunctionInfo} not found")

                resolveFunctionBody(
                    userData,
                    declaration,
//                    { f -> userData.originFunctionSymbol.isSame(f).also { println("IsSame: ${userData.originFunctionSymbol} -> $f") } },
                    { f -> f.descriptor == userData.originFunction },
                    callableFunction
                )?.also { generatedOriginFunction ->
                    postProcessGenerateOriginFunction(
                        generatedOriginFunction,
                        userData.transformer.originFunctionIncludeAnnotations
                    )
                }
            }

            else -> return null
        }

        return generatedOriginFunction
    }

    private fun postProcessGenerateOriginFunction(
        function: IrFunction,
        originFunctionIncludeAnnotations: List<IncludeAnnotation>
    ) {
        function.annotations = buildList {
            val currentAnnotations = function.annotations
            fun hasAnnotation(name: FqName): Boolean =
                currentAnnotations.any { a -> a.isAnnotationWithEqualFqName(name) }
            addAll(currentAnnotations)

            originFunctionIncludeAnnotations.forEach { include ->
                val classId = include.classInfo.toClassId()
                val annotationClass = pluginContext.referenceClass(classId) ?: return@forEach
                if (!include.repeatable && hasAnnotation(classId.asSingleFqName())) {
                    return@forEach
                }

                add(pluginContext.createIrBuilder(function.symbol).irAnnotationConstructor(annotationClass))
            }
        }
    }

    /**
     * @return origin function
     */
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    private inline fun resolveFunctionBody(
        sourceKey: Any?,
        function: IrFunction,
        crossinline checkIsOriginFunction: (IrFunction) -> Boolean,
        transformTargetFunctionCall: IrSimpleFunctionSymbol,
    ): IrFunction? {
        if (function.body != null) {
            return null
        }


        val parent = function.parent
        if (parent is IrDeclarationContainer) {

//            val originFunctionsSequence = sequence {
//                var p: IrDeclarationContainer? = parent
//                while (p != null) {
//                    for (declaration in p.declarations) {
//                        if (declaration is IrFunction && checkIsOriginFunction(declaration)) {
//                            yield(declaration)
//                        }
//                    }
//                    val curr = p
//                    p = if (curr is IrDeclaration) {
//                        (curr.parent as? IrDeclarationContainer).takeIf { it != curr }
//                    } else {
//                        null
//                    }
//                }
//            }

            /*
            在当前类型中寻找它的 origin function
            当前这个函数可能是继承过来的，并没有标记转化注解。
            例如在 interface Foo 中产生的 runBlocking，在 FooImpl 中被 visit 到了。
            这时候一般来讲是找不到它的 origin function 的，跳过就行
            至于 more than 2, 目标函数预期中只应该有一个，多余2个的可能很小，但是也属于预期外的情况。

            2024/03/24:
            似乎在没有手动添加实现的子类里会出现这种 (size == 0) 情况，例如
            ```
            interface Foo {
                @JvmBlocking
                fun run()
            }

            internal class FooImpl : Foo {
                // 这里似乎会出现警告，但是这里实际上应该没有被标注注解才对。
                override fun run() { ... }
            }
            ```

             */


            val originFunctions = parent.declarations.asSequence()
                .filterIsInstance<IrFunction>()
                .filter { checkIsOriginFunction(it) }
                .take(2)
                .toList()

            if (originFunctions.size != 1) {
                val actualNum = if (originFunctions.isEmpty()) "0" else "more than ${originFunctions.size}"
                val message =
                    "Synthetic function ${function.name.asString()}" +
                            "(${
                                kotlin.runCatching { function.kotlinFqName.asString() }
                                    .getOrElse { function.toString() }
                            } " +
                            "in " +
                            "${
                                kotlin.runCatching { parent.kotlinFqName.asString() }
                                    .getOrElse { parent.toString() }
                            }) 's originFunctions.size should be 1, " +
                            "but $actualNum (findIn = ${(parent as? IrDeclaration)?.descriptor}, originFunctions = $originFunctions, sourceKey = $sourceKey)"

                if (reporter != null) {
                    // WARN? DEBUG? IGNORE?
                    reporter.report(
                        CompilerMessageSeverity.INFO,
                        message,
                        function.reportLocation()
                    )
                } else {
                    // TODO In K2?
                    System.err.println(message)
                }


                return null
            }

            val originFunction = originFunctions.first()

            reporter?.report(
                CompilerMessageSeverity.INFO,
                "Generate body for function " +
                        kotlin.runCatching { function.kotlinFqName.asString() }.getOrElse { function.name.asString() } +
                        " by origin function " +
                        kotlin.runCatching { originFunction.kotlinFqName.asString() }
                            .getOrElse { originFunction.name.asString() },
                originFunction.reportLocation() ?: function.reportLocation()
            )

            function.body = generateTransformBodyForFunctionLambda(
                pluginContext,
                function,
                originFunction,
                transformTargetFunctionCall
            )

            return originFunction
        }

        return null
    }

    /**
     * 直接为bridge fun生成body
     */
    private fun resolveBridgeFunctionBody(
        sourceKey: Any?,
        function: IrFunction,
        transformTargetFunctionCall: IrSimpleFunctionSymbol,
    ) {
        if (function.body == null) {
            // body: return $transform(block, scope?)
            function.body = generateTransformBodyForFunctionLambda(
                pluginContext,
                function,
                null,
                transformTargetFunctionCall
            )
        }
    }
}

private fun IrFunction.reportLocation(): CompilerMessageSourceLocation? {
    return when (val sourceLocation =
//        getSourceLocation(runCatching { fileEntry }.getOrNull())) {
        getSourceLocation(declaration = symbol, file = file)) {
        is SourceLocation.Location -> {
            CompilerMessageLocation.create(
                path = sourceLocation.file,
                line = sourceLocation.line,
                column = sourceLocation.column,
                lineContent = null
            )
        }

        else -> null
    }
}


@Deprecated("see generateTransformBodyForFunctionLambda")
private fun generateTransformBodyForFunction(
    context: IrPluginContext,
    function: IrFunction,
    originFunction: IrFunction,
    transformTargetFunctionCall: IrSimpleFunctionSymbol,
): IrBody {
    // default params
    val originValueParameters = originFunction.valueParameters
    function.valueParameters.forEachIndexed { index, parameter ->
        val originFunctionValueParameter = originValueParameters[index]
        parameter.defaultValue = originFunctionValueParameter.defaultValue
    }

    return context.createIrBuilder(function.symbol).irBlockBody {
        val suspendLambda = context.createSuspendLambdaWithCoroutineScope(
            parent = originFunction.parent,
            // suspend () -> ?
            lambdaType = context.symbols.suspendFunctionN(0).typeWith(originFunction.returnType),
            originFunction = originFunction
        ).also { +it }

        +irReturn(irCall(transformTargetFunctionCall).apply {
            putValueArgument(0, irCall(suspendLambda.primaryConstructor!!).apply {
                for ((index, parameter) in function.paramsAndReceiversAsParamsList().withIndex()) {
                    putValueArgument(index, irGet(parameter))
                }
            })
            // argument: 1, if is CoroutineScope, and this is CoroutineScope.
            //println("transformTargetFunctionCall.owner: ${transformTargetFunctionCall.owner}")
            //println(transformTargetFunctionCall.owner.valueParameters)
            val owner = transformTargetFunctionCall.owner

            // CoroutineScope
            val ownerValueParameters = owner.valueParameters

            if (ownerValueParameters.size > 1) {
                for (index in 1..ownerValueParameters.lastIndex) {
                    val valueParameter = ownerValueParameters[index]
                    val type = valueParameter.type
                    tryResolveCoroutineScopeValueParameter(type, context, function, owner, this@irBlockBody, index)
                }
            }

        })
    }
}

/**
 * new
 *
 */
private fun generateTransformBodyForFunctionLambda(
    context: IrPluginContext,
    function: IrFunction,
    originFunction: IrFunction?,
    transformTargetFunctionCall: IrSimpleFunctionSymbol,
): IrBody {
    // 不为null，说明是直接生成。否则是bridge
    originFunction?.valueParameters?.also { originValueParameters ->
        function.valueParameters.forEachIndexed { index, parameter ->
            val originFunctionValueParameter = originValueParameters[index]
            parameter.defaultValue = originFunctionValueParameter.defaultValue
        }
    }

    return context.createIrBuilder(function.symbol).irBlockBody {

        val lambdaExpression: IrExpression = if (originFunction != null) {
            val suspendLambdaFunc = context.createSuspendLambdaFunctionWithCoroutineScope(
                originFunction = originFunction,
                function = function
            )

            val lambdaType = context.symbols.suspendFunctionN(0).typeWith(suspendLambdaFunc.returnType)

            IrFunctionExpressionImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                lambdaType,
                suspendLambdaFunc,
                IrStatementOrigin.LAMBDA
            )
        } else {
            // is bridge fun, use the first param `block`
            val blockParameter = function.valueParameters.first()
            irGet(blockParameter)
        }

        +irReturn(irCall(transformTargetFunctionCall).apply {
            putValueArgument(0, lambdaExpression)

            val transformFunctionOwner = transformTargetFunctionCall.owner

            // CoroutineScope
            val ownerValueParameters = transformFunctionOwner.valueParameters

            // argument: 1, if is CoroutineScope, and this is CoroutineScope.
            if (ownerValueParameters.size > 1) {
                for (index in 1..ownerValueParameters.lastIndex) {
                    val valueParameter = ownerValueParameters[index]
                    val type = valueParameter.type
                    tryResolveCoroutineScopeValueParameter(type, context, function, transformFunctionOwner, this@irBlockBody, index)
                }
            }

        })
    }
}

private val coroutineScopeTypeName = "kotlinx.coroutines.CoroutineScope".fqn
private val coroutineScopeTypeClassId = ClassId.topLevel("kotlinx.coroutines.CoroutineScope".fqn)
private val coroutineScopeTypeNameUnsafe = coroutineScopeTypeName.toUnsafe()

/**
 * 解析类型为 CoroutineScope 的参数。
 * 如果当前参数类型为 CoroutineScope:
 * - 如果当前 receiver 即为 CoroutineScope 类型，将其填充
 * - 如果当前 receiver 不是 CoroutineScope 类型，但是此参数可以为 null，
 *   则使用 safe-cast 将 receiver 转化为 CoroutineScope ( `dispatcher as? CoroutineScope` )
 * - 其他情况忽略此参数（适用于此参数有默认值的情况）
 */
private fun IrCall.tryResolveCoroutineScopeValueParameter(
    type: IrType,
    context: IrPluginContext,
    function: IrFunction,
    owner: IrSimpleFunction,
    builderWithScope: IrBuilderWithScope,
    index: Int
) {
    if (!type.isClassType(coroutineScopeTypeNameUnsafe)) {
        return
    }

    function.dispatchReceiverParameter?.also { dispatchReceiverParameter ->
        context.referenceClass(coroutineScopeTypeClassId)?.also { coroutineScopeRef ->
            if (dispatchReceiverParameter.type.isSubtypeOfClass(coroutineScopeRef)) {
                // put 'this' to the arg
                putValueArgument(index, builderWithScope.irGet(dispatchReceiverParameter))
            } else {
                val scopeType = coroutineScopeRef.defaultType

                val scopeParameter = owner.valueParameters.getOrNull(1)

                if (scopeParameter?.type?.isNullable() == true) {
                    val irSafeAs = IrTypeOperatorCallImpl(
                        startOffset,
                        endOffset,
                        scopeType,
                        IrTypeOperator.SAFE_CAST,
                        scopeType,
                        builderWithScope.irGet(dispatchReceiverParameter)
                    )

                    putValueArgument(index, irSafeAs)
                }
//                                irAs(irGet(dispatchReceiverParameter), coroutineScopeRef.defaultType)
            }
        }
    }
}
