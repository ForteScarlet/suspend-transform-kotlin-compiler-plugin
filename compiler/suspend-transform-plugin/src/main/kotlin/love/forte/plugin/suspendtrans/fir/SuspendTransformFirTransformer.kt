package love.forte.plugin.suspendtrans.fir

import love.forte.plugin.suspendtrans.*
import love.forte.plugin.suspendtrans.utils.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.MutableCheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.processOverriddenFunctions
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.expressions.impl.buildSingleExpressionBlock
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.references.builder.buildExplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildImplicitThisReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SessionHolderImpl
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.ReturnTypeCalculatorForFullBodyResolve
import org.jetbrains.kotlin.fir.scopes.impl.FirClassDeclaredMemberScope
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.scopes.processAllFunctions
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildErrorTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.utils.keysToMap
import java.util.concurrent.ConcurrentHashMap

private data class CopiedTypeParameterPair(
    val original: FirTypeParameter,
    val copied: FirTypeParameter
)

private data class CopyAnnotations(
    val functionAnnotations: List<FirAnnotation>,
    val propertyAnnotations: List<FirAnnotation>,
    val toOriginalAnnotations: List<FirAnnotation>
)

/**
 *
 * @author ForteScarlet
 */
class SuspendTransformFirTransformer(
    session: FirSession,
    private val suspendTransformConfiguration: SuspendTransformConfiguration
) : FirDeclarationGenerationExtension(session) {

    private val transformerFunctionSymbolMap = ConcurrentHashMap<Transformer, FirNamedFunctionSymbol>()

    private lateinit var coroutineScopeSymbol: FirClassLikeSymbol<*>

    private data class FirCacheKey(
        val classSymbol: FirClassSymbol<*>,
        val memberScope: FirClassDeclaredMemberScope?
    )

    private data class SyntheticFunData(
        val funName: Name,
        val annotationData: TransformAnnotationData,
        val transformer: Transformer,
        val transformerFunctionSymbol: FirNamedFunctionSymbol,
    )

    private fun initScopeSymbol() {

        val classId = ClassId(
            FqName.fromSegments(listOf("kotlinx", "coroutines")),
            Name.identifier("CoroutineScope")
        )

        coroutineScopeSymbol = session.symbolProvider.getClassLikeSymbolByClassId(classId)
            ?: session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(classId)
                    ?: error("Cannot resolve `kotlinx.coroutines.CoroutineScope` symbol.")
    }

    private fun initTransformerFunctionSymbolMap() {
        // 尝试找到所有配置的 bridge function, 例如 `runBlocking` 等
        val symbolProvider = session.symbolProvider
        val dependenciesSymbolProvider = session.dependenciesSymbolProvider

        suspendTransformConfiguration.transformers
            .forEach { (platform, transformerList) ->
                for (transformer in transformerList) {
                    val transformFunctionInfo = transformer.transformFunctionInfo
                    val packageName = transformFunctionInfo.packageName
                    val functionName = transformFunctionInfo.functionName

                    @Suppress("DEPRECATION")
                    val className = transformFunctionInfo.className
                    if (className != null) {
                        error(
                            "Not support `className` (`$className`) in transformer function info: " +
                                    "top level function supported only."
                        )
                    }

                    // TODO 校验funcs?

                    val functionNameIdentifier = Name.identifier(functionName)

                    val transformerFunctionSymbols =
                        symbolProvider.getTopLevelFunctionSymbols(
                            packageName.fqn,
                            functionNameIdentifier
                        ).ifEmpty {
                            dependenciesSymbolProvider.getTopLevelFunctionSymbols(
                                packageName.fqn,
                                functionNameIdentifier
                            )
                        }

                    if (transformerFunctionSymbols.isNotEmpty()) {
                        if (transformerFunctionSymbols.size == 1) {
                            transformerFunctionSymbolMap[transformer] = transformerFunctionSymbols.first()
                        } else {
                            error("Found multiple transformer function symbols for transformer: $transformer")
                        }
                    } else {
                        // 有时候在不同平台中寻找，可能会找不到，例如在jvm中找不到js的函数
                        // error("Cannot find transformer function symbol $packageName.$functionName (${session.moduleData.platform}) for transformer: $transformer")
                    }
                }
            }
    }

    //    private val cache: FirCache<Pair<FirClassSymbol<*>, FirClassDeclaredMemberScope?>, Map<Name, Map<FirNamedFunctionSymbol, FunData>>?, Nothing?> =
    private val cache: FirCache<FirCacheKey, Map<Name, Map<FirNamedFunctionSymbol, SyntheticFunData>>?, Nothing?> =
        session.firCachesFactory.createCache { (symbol, scope), c ->
            initScopeSymbol()
            initTransformerFunctionSymbolMap()

            createCache(symbol, scope)
        }


    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        val names = mutableSetOf<Name>()

        cache.getValue(FirCacheKey(classSymbol, context.declaredScope))?.forEach { (_, map) ->
            map.values.forEach { names.add(it.funName) }
        }

        return names
    }

    private val scope = ScopeSession()
    private val holder = SessionHolderImpl(session, scope)
    private val checkContext = MutableCheckerContext(
        holder,
        ReturnTypeCalculatorForFullBodyResolve.Default,
    )

    private fun List<FirTypeParameter>.mapToNewTypeParameters(
        funSymbol: FirBasedSymbol<*>,
        originalTypeParameterCache: MutableList<CopiedTypeParameterPair>
    ): List<FirTypeParameter> {
        return map {
            buildTypeParameterCopy(it) {
                containingDeclarationSymbol = funSymbol // it.containingDeclarationSymbol
//                             symbol = it.symbol // FirTypeParameterSymbol()
                symbol = FirTypeParameterSymbol()
            }.also { new ->
                originalTypeParameterCache.add(CopiedTypeParameterPair(it, new))
            }
        }
    }

    private fun List<FirValueParameter>.mapToNewValueParameters(
        originalTypeParameterCache: MutableList<CopiedTypeParameterPair>
    ): List<FirValueParameter> {
        return map { vp ->
            buildValueParameterCopy(vp) {
                symbol = FirValueParameterSymbol(vp.symbol.name)

                val copiedConeType = vp.returnTypeRef.coneTypeOrNull
                    ?.copyWithTypeParameters(originalTypeParameterCache)

                if (copiedConeType != null) {
                    returnTypeRef = returnTypeRef.withReplacedConeType(copiedConeType)
                }
            }
        }
    }

    private fun FirReceiverParameter.copyToNew(originalTypeParameterCache: MutableList<CopiedTypeParameterPair>): FirReceiverParameter? {
        return typeRef.coneTypeOrNull
            ?.copyWithTypeParameters(originalTypeParameterCache)
            ?.let { foundCopied ->
                buildReceiverParameterCopy(this) {
                    typeRef = typeRef.withReplacedConeType(foundCopied)
                }
            }
    }


    private fun ConeKotlinType.copyConeType(originalTypeParameterCache: MutableList<CopiedTypeParameterPair>): ConeKotlinType? {
        return copyWithTypeParameters(originalTypeParameterCache)
    }

    private fun ConeKotlinType.copyConeTypeOrSelf(originalTypeParameterCache: MutableList<CopiedTypeParameterPair>): ConeKotlinType {
        return copyConeType(originalTypeParameterCache) ?: this
    }

    private fun FirSimpleFunctionBuilder.copyParameters() {
        val originalTypeParameterCache = mutableListOf<CopiedTypeParameterPair>()

        val newTypeParameters = typeParameters.mapToNewTypeParameters(symbol, originalTypeParameterCache)
        typeParameters.clear()
        typeParameters.addAll(newTypeParameters)

        val newValueParameters = valueParameters.mapToNewValueParameters(originalTypeParameterCache)
        valueParameters.clear()
        valueParameters.addAll(newValueParameters)

        receiverParameter?.copyToNew(originalTypeParameterCache)?.also {
            this.receiverParameter = it
        }

        val coneTypeOrNull = returnTypeRef.coneTypeOrNull
        if (coneTypeOrNull != null) {
            returnTypeRef = returnTypeRef
                .withReplacedConeType(coneTypeOrNull.copyConeTypeOrSelf(originalTypeParameterCache))
        }
    }

    private fun FirPropertyAccessorBuilder.copyParameters(
        originalTypeParameterCache: MutableList<CopiedTypeParameterPair> = mutableListOf(),
        copyReturnType: Boolean = true
    ) {
        val newTypeParameters = typeParameters.mapToNewTypeParameters(symbol, originalTypeParameterCache)
        typeParameters.clear()
        typeParameters.addAll(newTypeParameters)

        val newValueParameters = valueParameters.mapToNewValueParameters(originalTypeParameterCache)
        valueParameters.clear()
        valueParameters.addAll(newValueParameters)

        if (copyReturnType) {
            val coneTypeOrNull = returnTypeRef.coneTypeOrNull
            if (coneTypeOrNull != null) {
                returnTypeRef = returnTypeRef
                    .withReplacedConeType(coneTypeOrNull.copyConeTypeOrSelf(originalTypeParameterCache))
            }
        }
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        val funcMap = cache.getValue(FirCacheKey(owner, context.declaredScope))
            ?.get(callableId.callableName)
            ?: return emptyList()

        val funList = arrayListOf<FirNamedFunctionSymbol>()

        funcMap.forEach { (func, funData) ->
            generateSyntheticFunctions(
                callableId,
                owner,
                func,
                funData,
                funList,
            )
        }

        return funList
    }


    @OptIn(SymbolInternals::class)
    private fun generateSyntheticFunctionBody(
        originFunc: FirSimpleFunction,
        originFunSymbol: FirNamedFunctionSymbol,
        owner: FirClassSymbol<*>,
        thisContextReceivers: MutableList<FirContextReceiver>,
        thisReceiverParameter: FirReceiverParameter?,
        newFunSymbol: FirBasedSymbol<*>,
        thisValueParameters: MutableList<FirValueParameter>,
        bridgeFunSymbol: FirNamedFunctionSymbol,
        newFunTarget: FirFunctionTarget,
        transformer: Transformer
    ): FirBlock = buildBlock {
        this.source = originFunc.body?.source

        // lambda: suspend () -> T
        val lambdaTarget = FirFunctionTarget(null, isLambda = true)
        val lambda = buildAnonymousFunction {
            this.resolvePhase = FirResolvePhase.BODY_RESOLVE
            this.isLambda = true
            this.moduleData = originFunSymbol.moduleData
            this.origin = FirDeclarationOrigin.Synthetic.FakeFunction
            this.returnTypeRef = originFunSymbol.resolvedReturnTypeRef
            this.hasExplicitParameterList = false
            this.status = FirResolvedDeclarationStatusImpl.DEFAULT_STATUS_FOR_SUSPEND_FUNCTION_EXPRESSION
            this.symbol = FirAnonymousFunctionSymbol()
            this.body = buildSingleExpressionBlock(
                buildReturnExpression {
                    target = lambdaTarget
                    result = buildFunctionCall {
                        // Call original fun
                        this.coneTypeOrNull = originFunSymbol.resolvedReturnTypeRef.coneType
                        this.source = originFunSymbol.source
                        this.calleeReference = buildResolvedNamedReference {
                            this.source = originFunSymbol.source
                            this.name = originFunSymbol.name
                            this.resolvedSymbol = originFunSymbol
                        }

                        val originValueParameters = originFunc.valueParameters

                        this.dispatchReceiver = buildThisReceiverExpression {
                            coneTypeOrNull = originFunSymbol.dispatchReceiverType
                            source = originFunSymbol.source
                            calleeReference = buildImplicitThisReference {
                                boundSymbol = owner
                            }
                        }

                        this.contextReceiverArguments.addAll(thisContextReceivers.map { receiver ->
                            buildThisReceiverExpression {
                                coneTypeOrNull = receiver.typeRef.coneTypeOrNull
                                source = receiver.source
                                calleeReference = buildExplicitThisReference {
                                    source = receiver.source
                                    labelName = receiver.labelName?.asString()
                                }
                            }
                        })

                        // TODO What is explicitReceiver?
                        this.extensionReceiver = thisReceiverParameter?.let { thisReceiverParameter ->
                            buildThisReceiverExpression {
                                coneTypeOrNull = thisReceiverParameter.typeRef.coneTypeOrNull
                                source = thisReceiverParameter.source
                                calleeReference = buildImplicitThisReference {
                                    boundSymbol = newFunSymbol
                                }
                            }
                        }

                        if (thisValueParameters.isNotEmpty()) {
                            this.argumentList = buildResolvedArgumentList(
                                null,
                                mapping = linkedMapOf<FirExpression, FirValueParameter>().apply {
                                    thisValueParameters.forEachIndexed { index, thisParam ->
                                        val qualifiedAccess = thisParam.toQualifiedAccess()
                                        put(qualifiedAccess, originValueParameters[index])
                                    }
                                }
                            )
                        }
                    }
                }
            )

            this.typeRef = buildResolvedTypeRef {
                this.coneType = StandardClassIds.SuspendFunctionN(0)
                    .createConeType(session, arrayOf(originFunSymbol.resolvedReturnType))
            }
        }
        lambdaTarget.bind(lambda)

        val returnType = resolveReturnType(transformer, originFunc.returnTypeRef)

        this.statements.add(
            buildReturnExpression {
                this.target = newFunTarget
                this.result = buildFunctionCall {
                    this.coneTypeOrNull = returnType.coneType
                    this.source = originFunc.body?.source
                    this.calleeReference = buildResolvedNamedReference {
                        this.source = bridgeFunSymbol.source
                        this.name = bridgeFunSymbol.name
                        this.resolvedSymbol = bridgeFunSymbol
                    }

                    // this.dispatchReceiver = buildThisReceiverExpression {
                    //     coneTypeOrNull = originFunSymbol.dispatchReceiverType
                    //     source = originFunSymbol.source
                    //     calleeReference = buildImplicitThisReference {
                    //         boundSymbol = owner
                    //     }
                    // }

                    this.argumentList = buildResolvedArgumentList(
                        null,
                        mapping = linkedMapOf<FirExpression, FirValueParameter>().apply {
                            put(
                                buildAnonymousFunctionExpression {
                                    source = null
                                    anonymousFunction = lambda
                                    isTrailingLambda = false
                                },
                                //                                            funData.bridgeFunData.lambdaParameter
                                bridgeFunSymbol.valueParameterSymbols.first().fir
                            )

                            // scope, if exists
                            val valueParameterSymbols =
                                bridgeFunSymbol.valueParameterSymbols

                            if (valueParameterSymbols.size > 1) {
                                // 支持:
                                //  CoroutineScope? -> this as? CoroutineScope
                                //  CoroutineScope -> this or throw error
                                //  CoroutineScope (optional) -> this or ignore
                                //  TODO Any -> this
                                //  TODO 后面的所有参数处理

//                                val listIterator = valueParameterSymbols.listIterator(1)
//                                listIterator.forEach { otherParameter ->
//
//                                }

                                val secondParameter = valueParameterSymbols[1]

                                val secondParameterType = secondParameter.resolvedReturnType
                                val secondParameterTypeNotNullable =
                                    secondParameterType.makeConeTypeDefinitelyNotNullOrNotNull(session.typeContext)

                                fun ConeKotlinType.isCoroutineScope(): Boolean {
                                    return isSubtypeOf(
                                        coroutineScopeSymbol.toLookupTag().constructClassType(),
                                        session
                                    )
                                }

                                val isCoroutineScope = secondParameterTypeNotNullable.isCoroutineScope()

                                if (isCoroutineScope) {
                                    if (secondParameterType.isMarkedNullable) {
                                        // this as? CoroutineScope
                                        val secondParameterTypeNotNullable =
                                            secondParameterType.makeConeTypeDefinitelyNotNullOrNotNull(
                                                session.typeContext
                                            )

                                        put(
                                            buildTypeOperatorCall {
                                                source = originFunSymbol.source
                                                coneTypeOrNull = secondParameterTypeNotNullable
                                                argumentList = buildResolvedArgumentList(
                                                    null,
                                                    mapping = linkedMapOf<FirExpression, FirValueParameter>().apply {
                                                        put(
                                                            buildThisReceiverExpression {
                                                                coneTypeOrNull = originFunSymbol.dispatchReceiverType
                                                                source = originFunSymbol.source
                                                                calleeReference = buildImplicitThisReference {
                                                                    boundSymbol = owner
                                                                }
                                                            },
                                                            secondParameter.fir
                                                        )
                                                    }
                                                )
                                                operation = FirOperation.SAFE_AS
                                                conversionTypeRef =
                                                    secondParameterTypeNotNullable.toFirResolvedTypeRef()
                                            },
                                            secondParameter.fir
                                        )

                                    } else {
                                        // if this is CoroutineScope, put, or throw error?

                                        var ownerIsCoroutineScopeOrParameterIsOptional = secondParameter.hasDefaultValue

                                        for (superType in owner.getSuperTypes(session, recursive = false)) {
                                            if (superType.isCoroutineScope()) {
                                                put(
                                                    buildThisReceiverExpression {
                                                        coneTypeOrNull = originFunSymbol.dispatchReceiverType
                                                        source = originFunSymbol.source
                                                        calleeReference = buildImplicitThisReference {
                                                            boundSymbol = owner
                                                        }
                                                    },
                                                    secondParameter.fir
                                                )
                                                ownerIsCoroutineScopeOrParameterIsOptional = true
                                                break
                                            }
                                        }

                                        // or throw error?
                                        if (!ownerIsCoroutineScopeOrParameterIsOptional) {
                                            error(
                                                "Owner is not a CoroutineScope, " +
                                                        "and the transformer function requires a `CoroutineScope` parameter."
                                            )
                                        }
                                    }

                                }
                            }

                        }
                    )
                }
            }
        )
    }

    @OptIn(SymbolInternals::class)
    private fun generateSyntheticFunctions(
        callableId: CallableId,
        owner: FirClassSymbol<*>,
        originFunSymbol: FirNamedFunctionSymbol,
        funData: SyntheticFunData,
        results: MutableList<FirNamedFunctionSymbol>,
    ) {
        val realBridgeFunSymbol = funData.transformerFunctionSymbol

        val annotationData = funData.annotationData
        if (!annotationData.asProperty) {
            // Check the overridden for isOverride based on source function (func) 's overridden
            val isOverride =
                checkSyntheticFunctionIsOverrideBasedOnSourceFunction(funData, originFunSymbol, checkContext)

            // generate
            val originFunc = originFunSymbol.fir

            val (functionAnnotations, _, includeToOriginal) = copyAnnotations(originFunc, funData)

            val newFunSymbol = FirNamedFunctionSymbol(callableId)

//            val key = SuspendTransformPluginKey(
//                data = SuspendTransformUserDataFir(
//                    markerId = UUID.randomUUID().toString(),
//                    originSymbol = originFunc.symbol.asOriginSymbol(
//                        targetMarkerAnnotation,
//                        typeParameters = originFunc.typeParameters,
//                        valueParameters = originFunc.valueParameters,
//                        originFunc.returnTypeRef.coneTypeOrNull?.classId,
//                        session,
//                    ),
//                    asProperty = false,
//                    transformer = funData.transformer
//                )
//            )
            val key = SuspendTransformK2V3Key

            val newFunTarget = FirFunctionTarget(null, isLambda = false)
            val newFun = buildSimpleFunctionCopy(originFunc) {
                name = callableId.callableName
                symbol = newFunSymbol
                status = originFunc.status.copy(
                    isSuspend = false,
                    modality = originFunc.syntheticModifier,
                    // Use OPEN and `override` is unnecessary. .. ... Maybe?
                    isOverride = isOverride || isOverridable(
                        session,
                        callableId.callableName,
                        originFunc,
                        owner,
                        isProperty = false,
                    ),
                )

                // Copy the typeParameters.
                // Otherwise, in functions like the following, an error will occur
                // suspend fun <A> data(value: A): T = ...
                // Functions for which function-scoped generalizations (`<A>`) exist.
                // In the generated IR, data and dataBlocking will share an `A`, generating the error.
                // The error: Duplicate IR node
                //     [IR VALIDATION] JvmIrValidationBeforeLoweringPhase: Duplicate IR node: TYPE_PARAMETER name:A index:0 variance: superTypes:[kotlin.Any?] reified:false of FUN GENERATED[...]
                copyParameters()

                // resolve returnType (with wrapped) after copyParameters
                returnTypeRef = resolveReturnType(funData.transformer, returnTypeRef)

                val thisReceiverParameter = this.receiverParameter
                val thisContextReceivers = this.contextReceivers
                val thisValueParameters = this.valueParameters

                annotations.clear()
                annotations.addAll(functionAnnotations)

                body = generateSyntheticFunctionBody(
                    originFunc,
                    originFunSymbol,
                    owner,
                    thisContextReceivers,
                    thisReceiverParameter,
                    newFunSymbol,
                    thisValueParameters,
                    realBridgeFunSymbol,
                    newFunTarget,
                    funData.transformer
                )

                origin = key.origin
            }

            newFunTarget.bind(newFun)
            results.add(newFun.symbol)

            // 在原函数上附加的annotations
            originFunc.includeAnnotations(includeToOriginal)
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        val owner = context?.owner ?: return emptyList()
        val funcMap = cache.getValue(FirCacheKey(owner, context.declaredScope))
            ?.get(callableId.callableName)
            ?: return emptyList()

        val propList = mutableListOf<FirPropertySymbol>()

        for ((originalFunSymbol, funData) in funcMap) {
            val annotationData = funData.annotationData

            if (!annotationData.asProperty) {
                continue
            }

            val isOverride =
                checkSyntheticFunctionIsOverrideBasedOnSourceFunction(funData, originalFunSymbol, checkContext)

            // generate
            val original = originalFunSymbol.fir

            val (functionAnnotations, propertyAnnotations, includeToOriginal) =
                copyAnnotations(original, funData)

            val pSymbol = FirPropertySymbol(callableId)

//                val pKey = SuspendTransformPluginKey(
//                    data = SuspendTransformUserDataFir(
//                        markerId = uniqueFunHash,
//                        originSymbol = original.symbol.asOriginSymbol(
//                            targetMarkerAnnotation,
//                            typeParameters = original.typeParameters,
//                            valueParameters = original.valueParameters,
//                            original.returnTypeRef.coneTypeOrNull?.classId,
//                            session
//                        ),
//                        asProperty = true,
//                        transformer = funData.transformer
//                    )
//                )
            val pKey = SuspendTransformK2V3Key

            val originalReturnType = original.returnTypeRef

            val originalTypeParameterCache: MutableList<CopiedTypeParameterPair> = mutableListOf()
            val copiedReturnType = originalReturnType.withReplacedConeType(
                originalReturnType.coneTypeOrNull?.copyConeTypeOrSelf(originalTypeParameterCache)
            )

            // copy完了再resolve，这样里面包的type parameter就不会有问题了（如果有type parameter的话）
            val resolvedReturnType = resolveReturnType(funData.transformer, copiedReturnType)

            val newFunTarget = FirFunctionTarget(null, isLambda = false)

            val p1 = buildProperty {
                symbol = pSymbol
                name = callableId.callableName
                source = original.source
                resolvePhase = original.resolvePhase
                moduleData = original.moduleData
                origin = pKey.origin
                attributes = original.attributes.copy()
                status = original.status.copy(
                    isSuspend = false,
                    isFun = false,
                    isInner = false,
                    modality = original.syntheticModifier,
                    isOverride = isOverride || isOverridable(
                        session,
                        callableId.callableName,
                        original,
                        owner,
                        isProperty = true
                    ),
                )

                isVar = false
                isLocal = false
                // Copy return type
                returnTypeRef = resolvedReturnType
                deprecationsProvider = UnresolvedDeprecationProvider //original.deprecationsProvider
                containerSource = original.containerSource
                dispatchReceiverType = original.dispatchReceiverType
                contextReceivers.addAll(original.contextReceivers)
                // annotations
                annotations.addAll(propertyAnnotations)
                typeParameters.addAll(original.typeParameters)
                resolvePhase = FirResolvePhase.BODY_RESOLVE
                backingField = null
                bodyResolveState = FirPropertyBodyResolveState.NOTHING_RESOLVED

                getter = buildPropertyAccessor {
                    propertySymbol = pSymbol
                    val propertyAccessorSymbol = FirPropertyAccessorSymbol()
                    symbol = propertyAccessorSymbol
                    isGetter = true
                    resolvePhase = FirResolvePhase.BODY_RESOLVE
                    moduleData = original.moduleData

                    // annotations
                    annotations.addAll(functionAnnotations)

                    returnTypeRef = resolvedReturnType
                    origin = pKey.origin

                    status = original.status.copy(
                        isSuspend = false,
                        isFun = false,
                        isInner = false,
                        modality = original.syntheticModifier,
                        isOverride = false, // funData.isOverride,
                        //                            visibility = this@buildProperty.status
                    )

                    valueParameters.addAll(original.valueParameters)
                    typeParameters.addAll(original.typeParameters)
                    contextReceivers.addAll(original.contextReceivers)

                    copyParameters(originalTypeParameterCache, false)

                    val thisContextReceivers = this.contextReceivers
                    val thisValueParameters = this.valueParameters

                    body = generateSyntheticFunctionBody(
                        original,
                        originalFunSymbol,
                        owner,
                        thisContextReceivers,
                        null,
                        propertyAccessorSymbol,
                        thisValueParameters,
                        funData.transformerFunctionSymbol,
                        newFunTarget,
                        funData.transformer
                    )
                }.also { getter ->
                    newFunTarget.bind(getter)
                }
            }

            propList.add(p1.symbol)

//                if (targetMarkerAnnotation != null) {
//                    original.appendTargetMarker(uniqueFunHash)
//                }

            // 在原函数上附加的annotations
            original.includeAnnotations(includeToOriginal)

        }

        return propList
    }


    private fun checkSyntheticFunctionIsOverrideBasedOnSourceFunction(
        syntheticFunData: SyntheticFunData,
        func: FirNamedFunctionSymbol,
        checkContext: CheckerContext
    ): Boolean {
        // Check the overridden for isOverride based on source function (func) 's overridden
        var isOverride = false
        val annoData = syntheticFunData.annotationData
        val markAnnotation = syntheticFunData.transformer.markAnnotation

        if (func.isOverride && !isOverride) {
            func.processOverriddenFunctions(
                checkContext
            ) processOverridden@{ overriddenFunction ->
                if (!isOverride) {
                    // check parameters and receivers
                    val symbolReceiver = overriddenFunction.receiverParameter
                    val originReceiver = func.receiverParameter

                    // origin receiver should be the same as symbol receiver
                    if (originReceiver?.typeRef != symbolReceiver?.typeRef) {
                        return@processOverridden
                    }

                    // all value parameters should be a subtype of symbol's value parameters
                    val symbolParameterSymbols = overriddenFunction.valueParameterSymbols
                    val originParameterSymbols = func.valueParameterSymbols

                    if (symbolParameterSymbols.size != originParameterSymbols.size) {
                        return@processOverridden
                    }

                    for ((index, symbolParameter) in symbolParameterSymbols.withIndex()) {
                        val originParameter = originParameterSymbols[index]
                        if (
                            originParameter.resolvedReturnType != symbolParameter.resolvedReturnType
                        ) {
                            return@processOverridden
                        }
                    }

                    val overriddenAnnotation = firAnnotation(
                        overriddenFunction, markAnnotation, overriddenFunction.getContainingClassSymbol()
                    ) ?: return@processOverridden

                    val overriddenAnnoData = overriddenAnnotation.toTransformAnnotationData(
                        markAnnotation, overriddenFunction.name.asString()
                    )

                    // Same functionName, same asProperty, the generated synthetic function will be same too.
                    if (
                        overriddenAnnoData.functionName == annoData.functionName
                        && overriddenAnnoData.asProperty == annoData.asProperty
                    ) {
                        isOverride = true
                    }
                }
            }
        }
        return isOverride
    }

    private val annotationPredicates = DeclarationPredicate.create {
        var predicate: DeclarationPredicate? = null
        for (value in suspendTransformConfiguration.transformers.values) {
            for (transformer in value) {
                val afq = transformer.markAnnotation.fqName
                predicate = if (predicate == null) {
                    annotated(afq)
                } else {
                    predicate or annotated(afq)
                }
            }
        }

        predicate ?: annotated()
    }

    /**
     * NB: The predict needs to be *registered* in order to parse the [@XSerializable] type
     * otherwise, the annotation remains unresolved
     */
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(annotationPredicates)
    }

    private fun createCache(
        classSymbol: FirClassSymbol<*>,
        declaredScope: FirClassDeclaredMemberScope?
    ): Map<Name, Map<FirNamedFunctionSymbol, SyntheticFunData>>? {
        if (declaredScope == null) return null

        fun check(targetPlatform: TargetPlatform): Boolean {
            val platform = classSymbol.moduleData.platform

            return when {
                platform.isJvm() && targetPlatform == TargetPlatform.JVM -> true
                platform.isJs() && targetPlatform == TargetPlatform.JS -> true
                platform.isWasm() && targetPlatform == TargetPlatform.WASM -> true
                platform.isNative() && targetPlatform == TargetPlatform.NATIVE -> true
                platform.isCommon() && targetPlatform == TargetPlatform.COMMON -> true
                else -> false
            }
        }

        // Key -> synthetic fun name
        // Value Map ->
        //      Key: -> origin fun symbol
        //      Values -> FunData
        val map = ConcurrentHashMap<Name, MutableMap<FirNamedFunctionSymbol, SyntheticFunData>>()
//        val transformerFunctionSymbolMap = ConcurrentHashMap<Transformer, FirNamedFunctionSymbol>()

        val platformTransformers = suspendTransformConfiguration.transformers
            .filter { (platform, _) -> check(platform) }

        declaredScope.processAllFunctions { func ->
            if (!func.isSuspend) return@processAllFunctions

            val functionName = func.name.asString()

            platformTransformers
                .forEach { (_, transformerList) ->
                    for (transformer in transformerList) {
                        val markAnnotation = transformer.markAnnotation

                        val anno = firAnnotation(func, markAnnotation, classSymbol)
                            ?: continue


                        val transformerFunctionSymbol = transformerFunctionSymbolMap[transformer]
                            ?: error("Cannot find transformer function symbol for transformer: $transformer")

                        // 读不到注解的参数？
                        // 必须使用 anno.getXxxArgument(Name(argument name)),
                        // 使用 argumentMapping.mapping 获取不到结果
//                        println("RAW AnnoData: ${anno.argumentMapping.mapping}")
                        val annoData = anno.toTransformAnnotationData(markAnnotation, functionName)

                        val syntheticFunNameString = annoData.functionName

                        val syntheticFunName = Name.identifier(syntheticFunNameString)

                        map.computeIfAbsent(syntheticFunName) {
                            ConcurrentHashMap()
                        }[func] = SyntheticFunData(
                            syntheticFunName,
                            annoData,
                            transformer,
                            transformerFunctionSymbol
                        )
                    }
                }
        }

        return map
    }

    private fun FirAnnotation.toTransformAnnotationData(
        markAnnotation: MarkAnnotation,
        sourceFunctionName: String
    ) = TransformAnnotationData.of(
        session,
        firAnnotation = this,
        annotationBaseNamePropertyName = markAnnotation.baseNameProperty,
        annotationSuffixPropertyName = markAnnotation.suffixProperty,
        annotationAsPropertyPropertyName = markAnnotation.asPropertyProperty,
        defaultBaseName = sourceFunctionName,
        defaultSuffix = markAnnotation.defaultSuffix,
        defaultAsProperty = markAnnotation.defaultAsProperty,
    )

    private fun firAnnotation(
        func: FirNamedFunctionSymbol,
        markAnnotation: MarkAnnotation,
        classSymbol: FirBasedSymbol<*>?
    ) = func.resolvedAnnotationsWithArguments.getAnnotationsByClassId(
        markAnnotation.classId,
        session
    ).firstOrNull()
        ?: classSymbol?.resolvedAnnotationsWithArguments?.getAnnotationsByClassId(
            markAnnotation.classId,
            session
        )?.firstOrNull()

    private fun resolveReturnType(
        transformer: Transformer,
        returnTypeRef: FirTypeRef
    ): FirTypeRef {
        val resultConeType = resolveReturnConeType(transformer, returnTypeRef)

        return if (resultConeType is ConeErrorType) {
            buildErrorTypeRef {
                diagnostic = resultConeType.diagnostic
                coneType = resultConeType
            }
        } else {
            buildResolvedTypeRef {
                coneType = resultConeType
            }
        }
    }

    private fun resolveReturnConeType(
        transformer: Transformer,
        returnTypeRef: FirTypeRef
    ): ConeKotlinType {
        val transformer = transformer
        val returnType = transformer.transformReturnType
            ?: return returnTypeRef.coneType // OrNull // original.symbol.resolvedReturnType

        var typeArguments: Array<ConeTypeProjection> = emptyArray()

        if (transformer.transformReturnTypeGeneric) {
            typeArguments = arrayOf(ConeKotlinTypeProjectionOut(returnTypeRef.coneType))
        }

        val resultConeType = returnType.toClassId().createConeType(
            session = session,
            typeArguments,
            nullable = returnType.nullable
        )

        return resultConeType
    }

    /**
     * @return function annotations `to` property annotations.
     */
    private fun copyAnnotations(
        original: FirSimpleFunction, syntheticFunData: SyntheticFunData,
    ): CopyAnnotations {
        val transformer = syntheticFunData.transformer

        val originalAnnotationClassIdMap = original.annotations.keysToMap { it.toAnnotationClassId(session) }

        val (copyFunction, copyProperty, excludes, includes) = CopyAnnotationsData(
            transformer.copyAnnotationsToSyntheticFunction,
            transformer.copyAnnotationsToSyntheticProperty,
            transformer.copyAnnotationExcludes.map { it.toClassId() },
            transformer.syntheticFunctionIncludeAnnotations.map { it.toInfo() }
        )

        val functionAnnotationList = buildList<FirAnnotation> {
            if (copyFunction) {
                val notCompileAnnotationsCopied = originalAnnotationClassIdMap.filterNot { (_, annotationClassId) ->
                    if (annotationClassId == null) return@filterNot true
                    excludes.any { ex -> annotationClassId == ex }
                }.keys

                /*
                 * Create a new annotation based the annotation from the original function.
                 * It will be crashed with `IllegalArgumentException: Failed requirement`
                 * when using the `notCompileAnnotationsCopied` directly
                 * if there have some arguments with type `KClass`,
                 * e.g. `annotation class OneAnnotation(val target: KClass<*>)` or `kotlin.OptIn`.
                 *
                 * See https://github.com/ForteScarlet/kotlin-suspend-transform-compiler-plugin/issues/56
                 */
                val copied = notCompileAnnotationsCopied.map { a ->
                    buildAnnotation {
                        annotationTypeRef = buildResolvedTypeRef {
                            coneType = a.resolvedType
                        }
                        this.typeArguments.addAll(a.typeArguments)
                        this.argumentMapping = buildAnnotationArgumentMapping {
                            this.source = a.source
                            this.mapping.putAll(a.argumentMapping.mapping)
                        }
                    }
                }

                addAll(copied)
            }

            // add includes
            includes.forEach { include ->
                val classId = include.classId
                val includeAnnotation = buildAnnotation {
                    argumentMapping = buildAnnotationArgumentMapping()
                    annotationTypeRef = buildResolvedTypeRef {
                        coneType = classId.createConeType(session)
                    }
                }
                add(includeAnnotation)
            }
        }

        val propertyAnnotationList = buildList<FirAnnotation> {
            if (copyProperty) {
                val notCompileAnnotationsCopied = originalAnnotationClassIdMap.filterNot { (_, annotationClassId) ->
                    if (annotationClassId == null) return@filterNot true
                    excludes.any { ex -> annotationClassId == ex }
                }.keys

                addAll(notCompileAnnotationsCopied)
            }

            // add includes
            includes
                .filter { it.includeProperty }
                .forEach { include ->
                    val classId = include.classId
                    val includeAnnotation = buildAnnotation {
                        argumentMapping = buildAnnotationArgumentMapping()
                        annotationTypeRef = buildResolvedTypeRef {
                            coneType = classId.createConeType(session)
                        }
                    }
                    add(includeAnnotation)
                }
        }

        // original annotations

        val infos = transformer.originFunctionIncludeAnnotations.map { it.toInfo() }

        val includeToOriginals: List<FirAnnotation> = infos
            .mapNotNull { (classId, repeatable, _) ->
                if (!repeatable) {
                    // 不能是已经存在的
                    if (originalAnnotationClassIdMap.values.any { it == classId }) {
                        return@mapNotNull null
                    }
                }

                buildAnnotation {
                    argumentMapping = buildAnnotationArgumentMapping()
                    annotationTypeRef = buildResolvedTypeRef {
                        coneType = classId.createConeType(session)
                    }
                }
            }

        return CopyAnnotations(functionAnnotationList, propertyAnnotationList, includeToOriginals)
    }

    private fun isOverridable(
        session: FirSession,
        functionName: Name,
        thisReceiverTypeRef: FirTypeRef?,
        thisValueTypeRefs: List<FirTypeRef?>,
        owner: FirClassSymbol<*>,
        isProperty: Boolean,
    ): Boolean {

        if (isProperty) {
            // value symbols must be empty.
            check(thisValueTypeRefs.isEmpty()) { "property's value parameters must be empty." }

            return owner.getSuperTypes(session)
                .asSequence()
                .mapNotNull {
                    it.toRegularClassSymbol(session)
                }
                .flatMap {
                    it.declarationSymbols.filterIsInstance<FirPropertySymbol>()
                }
                .filter { !it.isFinal }
                .filter { it.callableId.callableName == functionName }
                // overridable receiver parameter.
                .filter {
                    thisReceiverTypeRef sameAs it.receiverParameter?.typeRef
                }
                .any()
        } else {
            return owner.getSuperTypes(session)
                .asSequence()
                .mapNotNull {
                    it.toRegularClassSymbol(session)
                }
                .flatMap {
                    it.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>()
                }
                // not final, overridable
                .filter { !it.isFinal }
                // same name
                .filter { it.callableId.callableName == functionName }
                // overridable receiver parameter.
                .filter {
                    thisReceiverTypeRef sameAs it.receiverParameter?.typeRef
                }
                // overridable value parameters
                .filter {
                    val valuePs = it.valueParameterSymbols
                        .map { vps ->
                            vps.resolvedReturnTypeRef
                        }

                    if (valuePs.size != thisValueTypeRefs.size) return@filter false

                    for (i in valuePs.indices) {
                        val valueP = valuePs[i]
                        val thisP = thisValueTypeRefs[i]

                        if (thisP notSameAs valueP) {
                            return@filter false
                        }
                    }

                    true
                }
                .any()
        }
    }

    private fun isOverridable(
        session: FirSession,
        functionName: Name,
        originFunc: FirSimpleFunction,
        owner: FirClassSymbol<*>,
        isProperty: Boolean = false,
    ): Boolean {
        // 寻找 owner 中所有的 open/abstract的,
        // parameters 的类型跟 originFunc 的 parameters 匹配的

        val thisReceiverTypeRef: FirTypeRef? = originFunc.receiverParameter?.typeRef

        val thisValueTypeRefs: List<FirTypeRef?> = originFunc.valueParameters.map {
            it.symbol.resolvedReturnTypeRef
        }

        return isOverridable(session, functionName, thisReceiverTypeRef, thisValueTypeRefs, owner, isProperty)
    }

    /**
     * Check is an overridable same type for a function's parameters.
     *
     */
    private infix fun FirTypeRef?.sameAs(otherSuper: FirTypeRef?): Boolean {
        if (this == otherSuper) return true
        val thisConeType = this?.coneTypeOrNull
        val otherConeType = otherSuper?.coneTypeOrNull

        if (thisConeType == otherConeType) return true

        if (thisConeType == null || otherConeType == null) {
            // One this null, other is not null
            return false
        }

        return thisConeType sameAs otherConeType
    }

    private infix fun ConeKotlinType.sameAs(otherSuper: ConeKotlinType): Boolean {
        return this == otherSuper
        // 有什么便捷的方法来处理 ConeTypeParameterType ？
    }

    private infix fun FirTypeRef?.notSameAs(otherSuper: FirTypeRef?): Boolean = !(this sameAs otherSuper)


    private fun ConeKotlinType.copyWithTypeParameters(
        parameters: List<CopiedTypeParameterPair>,
    ): ConeKotlinType? {
        fun findCopied(target: ConeKotlinType) = parameters.find { (original, _) ->
            original.toConeType() == target
        }?.copied

        val copiedThis = findCopied(this)
        if (copiedThis != null) {
            return copiedThis.toConeType()
        }

        when (this) {
            is ConeDynamicType -> {
                //println("Dynamic type: $this")
            }

            is ConeFlexibleType -> {
                //println("Flexible type: $this")
            }

            is ConeClassLikeType -> {
                if (typeArguments.isNotEmpty()) {
                    fun mapProjection(projection: ConeTypeProjection): ConeTypeProjection? {
                        val findCopiedDirectly = projection.type?.let { type -> findCopied(type) }
                        if (findCopiedDirectly != null) {
                            return findCopiedDirectly.toConeType()
                        }

                        return when (projection) {
                            // is ConeFlexibleType -> { }

                            is ConeClassLikeType -> {
                                projection.copyWithTypeParameters(parameters)
                            }

                            is ConeCapturedType -> {
//                                val lowerType = projection.lowerType?.let { lowerType ->
//                                    findCopied(lowerType)
//                                }?.toConeType()

                                val lowerType = projection.lowerType?.copyWithTypeParameters(parameters)

                                if (lowerType == null) {
                                    projection.copy(lowerType = lowerType)
                                } else {
                                    null
                                }
                            }

                            is ConeDefinitelyNotNullType -> {
                                findCopied(projection.original)
                                    ?.toConeType()
                                    ?.let { projection.copy(it) }
                            }
                            // is ConeIntegerConstantOperatorType -> TODO()
                            // is ConeIntegerLiteralConstantType -> TODO()
                            is ConeIntersectionType -> {
                                val upperBoundForApproximation = projection.upperBoundForApproximation
                                    ?.copyWithTypeParameters(parameters)
//                                val upperBoundForApproximation =
//                                    projection.upperBoundForApproximation
//                                        ?.let { findCopied(it) }
//                                        ?.toConeType()

                                var anyIntersectedTypes = false

                                val intersectedTypes = projection.intersectedTypes.map { ktype ->
                                    findCopied(ktype)?.toConeType()
//                                    ktype.copyWithTypeParameters(parameters)
                                        ?.also { anyIntersectedTypes = true }
                                        ?: ktype
                                }

                                if (upperBoundForApproximation != null || anyIntersectedTypes) {
                                    ConeIntersectionType(
                                        intersectedTypes,
                                        upperBoundForApproximation
                                    )
                                } else {
                                    null
                                }
                            }
                            // is ConeLookupTagBasedType -> TODO()
                            // is ConeStubTypeForTypeVariableInSubtyping -> TODO()
                            // is ConeTypeVariableType -> TODO()
                            is ConeKotlinTypeConflictingProjection -> {
//                                findCopied(projection.type)
//                                    ?.toConeType()
//                                    ?.let { projection.copy(it) }

                                projection.type.copyWithTypeParameters(parameters)
                                    ?.let { projection.copy(it) }
                            }

                            is ConeKotlinTypeProjectionIn -> {
//                                findCopied(projection.type)
//                                    ?.toConeType()
//                                    ?.let { projection.copy(it) }

                                projection.type.copyWithTypeParameters(parameters)
                                    ?.let { projection.copy(it) }
                            }

                            is ConeKotlinTypeProjectionOut -> {
//                                findCopied(projection.type)
//                                    ?.toConeType()
//                                    ?.let { projection.copy(it) }

                                projection.type.copyWithTypeParameters(parameters)
                                    ?.let { projection.copy(it) }
                            }

                            is ConeTypeParameterType -> {
//                                findCopied(projection)?.toConeType()
                                projection.copyWithTypeParameters(parameters)
                            }

                            ConeStarProjection -> ConeStarProjection

                            // Other unknowns, e.g., ClassLike
                            else -> null
                        }
                    }

                    val typeArguments: Array<ConeTypeProjection> = typeArguments.map { projection ->
                        mapProjection(projection) ?: projection
                    }.toTypedArray()

                    return classId?.createConeType(
                        session = session,
                        typeArguments = typeArguments,
                        nullable = isMarkedNullable
                    )
                    // typeArguments.forEach { projection ->
                    //     projection.type?.copyWithTypeParameters(parameters)
                    // }
                }

                return null
            }

            is ConeTypeParameterType -> {
                return findCopied(this)?.toConeType() ?: this
//                return parameters.find { (original, _) ->
//                    original.toConeType() == this
//                }?.copied?.toConeType() ?: this
            }

            else -> {
                // ?
            }
        }

        return null
    }
}


private val MarkAnnotation.classId: ClassId
    get() {
        return ClassId(classInfo.packageName.fqn, classInfo.className.fqn, classInfo.local)
    }


private val MarkAnnotation.fqName: FqName
    get() {
        return FqName(classInfo.packageName + "." + classInfo.className)
    }

private val FirSimpleFunction.syntheticModifier: Modality?
    get() = when {
        status.isOverride -> Modality.OPEN
        modality == Modality.ABSTRACT -> Modality.OPEN
        else -> status.modality
    }
