package love.forte.plugin.suspendtrans

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.ExpectForActualMatchingData
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.expectForActual
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

object SuspendTransformUserDataKey : CallableDescriptor.UserDataKey<SuspendTransformUserData>

data class SuspendTransformUserData(
    val originFunction: SimpleFunctionDescriptor,
    val asProperty: Boolean,
    val transformer: Transformer
) {
    val originFunctionSymbol = originFunction.toOriginFunctionSymbol()
}

fun SimpleFunctionDescriptor.toOriginFunctionSymbol(): OriginFunctionSymbol {
    val fqName = fqNameSafe

    val typeParameters = typeParameters.map {
        OriginTypeParameter(
            name = it.name,
            varianceOrdinal = it.variance.ordinal,
            isReified = it.isReified,
            it.upperBounds.map { bound ->
                kotlin.runCatching { bound.constructor.declarationDescriptor?.name }.getOrNull()
            }
        )
    }

    val valueParameters = valueParameters.map {
        OriginValueParameter(
            name = it.name,
            typeFqName = kotlin.runCatching { it.type.getKotlinTypeFqName(false) }.getOrNull()
        )
    }


    val returnType = kotlin.runCatching { returnType?.getKotlinTypeFqName(false) }.getOrNull()

    return OriginFunctionSymbol(
        name = fqName,
        typeParameters = typeParameters,
        valueParameters = valueParameters,
        returnType = returnType
    )
}

fun OriginFunctionSymbol.isSame(irFunction: IrFunction): Boolean {
    // function name
    if (!irFunction.hasEqualFqName(name)) return false

    // return type
    if (irFunction.returnType.classFqName?.asString() != returnType) return false

    // typeParameters
    val irFunctionTypeParameters = irFunction.typeParameters
    if (irFunction.typeParameters.size != typeParameters.size) return false

    for ((index, typeParameter) in irFunctionTypeParameters.withIndex()) {
        val targetTypeParameter = typeParameters[index]
        if (!(typeParameter isSameAs targetTypeParameter)) return false
    }


    // valueParameters
    val irFunctionValueParameters = irFunction.valueParameters
    if (irFunction.valueParameters.size != valueParameters.size) return false

    for ((index, valueParameter) in irFunctionValueParameters.withIndex()) {
        val targetValueParameter = valueParameters[index]
        if (!(valueParameter isSameAs targetValueParameter)) return false
    }

    return true
}

private infix fun IrTypeParameter.isSameAs(typeParameter: OriginTypeParameter): Boolean {
    if (name != typeParameter.name) return false
    if (variance.ordinal != typeParameter.varianceOrdinal) return false
    if (isReified != typeParameter.isReified) return false
    val superTypes = superTypes
    if (superTypes.size != typeParameter.upperBoundNames.size) return false

    for ((index, superType) in superTypes.withIndex()) {
        val typeBoundName = typeParameter.upperBoundNames[index]
        if (superType.classFqName?.shortName() != typeBoundName) return false
    }

    return true
}

private infix fun IrValueParameter.isSameAs(valueParameter: OriginValueParameter): Boolean {
    if (name != valueParameter.name) return false
    return type.classFqName?.asString() == valueParameter.typeFqName
}

data class OriginFunctionSymbol(
    val name: FqName,
    val typeParameters: List<OriginTypeParameter>,
    val valueParameters: List<OriginValueParameter>,
    val returnType: String?,
)

data class OriginTypeParameter(
    val name: Name,
    val varianceOrdinal: Int,
    val isReified: Boolean,
    val upperBoundNames: List<Name?>
)

data class OriginValueParameter(
    val name: Name,
    val typeFqName: String?
)

////

data class SuspendTransformUserDataFir(
    val originSymbol: OriginSymbol,
    val markerId: String,
    val asProperty: Boolean,
    val transformer: Transformer
)

data class SuspendTransformBridgeFunDataFir(
    val asProperty: Boolean,
    val transformer: Transformer
)

fun FirNamedFunctionSymbol.asOriginSymbol(
    targetMarker: ClassId?,
    typeParameters: List<FirTypeParameter>,
    valueParameters: List<FirValueParameter>,
    returnType: ClassId?,
    session: FirSession,
): OriginSymbol {
    return OriginSymbol(
        targetMarker,
        symbol = this,
        callableId = this.callableId,
        typeParameters = typeParameters.map { it.toTypeParameter() },
        valueParameters = valueParameters.mapIndexed { index, p -> p.toValueParameter(session, index) },
        returnType
    )
}

data class OriginSymbol(
    val targetMarker: ClassId?,
    val symbol: FirNamedFunctionSymbol,
    val callableId: CallableId,
    val typeParameters: List<TypeParameter>,
    val valueParameters: List<ValueParameter>,
    val returnType: ClassId?
)

data class TypeParameter(
    val name: Name,
    val varianceOrdinal: Int,
    val isReified: Boolean,
    val bounds: List<ClassId?>,
    val type: ConeTypeParameterType,
)

private fun FirTypeParameter.toTypeParameter(): TypeParameter {
    return TypeParameter(
        name,
        variance.ordinal,
        isReified,
        bounds.map { it.coneTypeOrNull?.classId },
        toConeType(),
    )
}


data class ValueParameter(
    val fir: FirValueParameter,
    val name: Name,
    val index: Int,
    val coneType: ConeKotlinType?,
    val type: ClassId?,
    val expectForActual: ExpectForActualMatchingData?
)

@OptIn(SymbolInternals::class)
private fun FirValueParameter.toValueParameter(session: FirSession, index: Int): ValueParameter {
//    LocalLoggerHelper.println("returnTypeRef = $returnTypeRef")
//    LocalLoggerHelper.println("symbol.resolvedReturnTypeRef = ${symbol.resolvedReturnTypeRef}")
//    LocalLoggerHelper.println("symbol.resolvedReturnTypeRef.coneType = ${symbol.resolvedReturnTypeRef.coneType}")
//    LocalLoggerHelper.println("symbol.resolvedReturnTypeRef.coneType.isTypealiasExpansion = ${symbol.resolvedReturnTypeRef.coneType.isTypealiasExpansion}")
//    LocalLoggerHelper.println(
//        "symbol.resolvedReturnTypeRef.coneType.fullyExpandedType(session) = ${
//            symbol.resolvedReturnTypeRef.coneType.fullyExpandedType(
//                session
//            )
//        }"
//    )
//
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toClassLikeSymbol(session)?.isActual: ${
//            returnTypeRef.coneType.toClassLikeSymbol(
//                session
//            )?.isActual
//        }"
//    )
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toClassLikeSymbol(session)?.isExpect: ${
//            returnTypeRef.coneType.toClassLikeSymbol(
//                session
//            )?.isExpect
//        }"
//    )
//
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toRegularClassSymbol(session): ${
//            returnTypeRef.coneType.toRegularClassSymbol(
//                session
//            )
//        }"
//    )
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toClassLikeSymbol(session): ${
//            returnTypeRef.coneType.toClassLikeSymbol(
//                session
//            )
//        }"
//    )
//
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.expectForActual: " +
//                "${returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.expectForActual}"
//    )
//
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.memberExpectForActual: " +
//                "${returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.memberExpectForActual}"
//    )
//
//    LocalLoggerHelper.println(
//        "returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.fullyExpandedClass.defaultType: " +
//                "${
//                    returnTypeRef.coneType.toRegularClassSymbol(session)?.fir?.fullyExpandedClass(session)
//                        ?.defaultType()
//                }"
//    )

    return ValueParameter(
        this,
        name,
        index,
        returnTypeRef.coneTypeOrNull,
        returnTypeRef.coneTypeOrNull?.classId,
        returnTypeRef.toClassLikeSymbol(session)?.expectForActual
    )
}


fun OriginSymbol.checkSame(markerId: String, declaration: IrFunction): Boolean {
    if (targetMarker != null) {
        val anno = declaration.annotations.firstOrNull { it.symbol.owner.parentAsClass.classId == targetMarker }
        if (anno != null) {
            val valueArgument = anno.getValueArgument(Name.identifier("value")) as? IrConst
            if (markerId == valueArgument?.value) {
                return true
            }
        }
        // 如果匹配不成功，继续原本的逻辑
    }

    // callableId
    if (callableId != declaration.callableId) return false
    // return type
    if (declaration.returnType.classFqName != returnType?.asSingleFqName()) return false
    // typeParameters
    val declarationTypeParameters = declaration.typeParameters
    if (typeParameters.size != declarationTypeParameters.size) return false
    for ((index, typeParameter) in declarationTypeParameters.withIndex()) {
        val targetTypeParameter = typeParameters[index]
        if (!(typeParameter isSameAs targetTypeParameter)) return false
    }

    // valueParameters
    val declarationValueParameters = declaration.valueParameters
    if (valueParameters.size != declarationValueParameters.size) return false
    for ((index, valueParameter) in declarationValueParameters.withIndex()) {
        val targetValueParameter = valueParameters[index]
        if (!(valueParameter isSameAs targetValueParameter)) return false
    }

    return true
}

private infix fun IrTypeParameter.isSameAs(typeParameter: TypeParameter): Boolean {
    if (name != typeParameter.name) return false
    if (variance.ordinal != typeParameter.varianceOrdinal) return false
    if (isReified != typeParameter.isReified) return false
    val superTypes = superTypes
    if (superTypes.size != typeParameter.bounds.size) return false

    for ((index, superType) in superTypes.withIndex()) {
        val typeBound = typeParameter.bounds[index]
        if (superType.classFqName != typeBound?.asSingleFqName()) return false
    }

    return true
}

private infix fun IrValueParameter.isSameAs(valueParameter: ValueParameter): Boolean {
    if (index != valueParameter.index) return false
    return type.classFqName == valueParameter.type?.asSingleFqName()
}
