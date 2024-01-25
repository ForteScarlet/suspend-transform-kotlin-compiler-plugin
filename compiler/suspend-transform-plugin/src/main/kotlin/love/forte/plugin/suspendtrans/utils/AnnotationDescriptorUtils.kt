package love.forte.plugin.suspendtrans.utils

import love.forte.plugin.suspendtrans.Transformer
import love.forte.plugin.suspendtrans.toJsPromiseAnnotationName
import love.forte.plugin.suspendtrans.toJvmAsyncAnnotationName
import love.forte.plugin.suspendtrans.toJvmBlockingAnnotationName
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe


fun IrBuilderWithScope.irAnnotationConstructor(
    clazz: IrClassSymbol,
): IrConstructorCall {
    return irCall(clazz.constructors.first()).let {
        irConstructorCall(it, it.symbol)
    }
}

fun Iterable<AnnotationDescriptor>.filterNotCompileAnnotations(): List<AnnotationDescriptor> = filterNot {
    val annotationFqNameUnsafe = it.annotationClass?.fqNameUnsafe ?: return@filterNot true
//
    annotationFqNameUnsafe == toJvmAsyncAnnotationName.toUnsafe()
            || annotationFqNameUnsafe == toJvmBlockingAnnotationName.toUnsafe()
            || annotationFqNameUnsafe == toJsPromiseAnnotationName.toUnsafe()
}

//fun Iterable<FirAnnotation>.filterNotCompileAnnotations(session: FirSession): List<FirAnnotation> = filterNot {
//    val annotationFqName = it.fqName(session) ?: return@filterNot true
//    val annotationFqNameUnsafe = it.annotationClass?.fqNameUnsafe ?: return@filterNot true

//    annotationFqName == toJvmAsyncAnnotationName.toUnsafe()
//            || annotationFqName == toJvmBlockingAnnotationName.toUnsafe()
//            || annotationFqName == toJsPromiseAnnotationName.toUnsafe()
//}

data class TransformAnnotationData(
    //val annotationDescriptor: AnnotationDescriptor,
    val baseName: String?,
    val suffix: String?,
    val rawAsProperty: Boolean?,
    val asProperty: Boolean,
    val functionName: String,
) {
    companion object {
        fun of(
            annotationDescriptor: AnnotationDescriptor,
            annotationBaseNamePropertyName: String = "baseName",
            annotationSuffixPropertyName: String = "suffix",
            annotationAsPropertyPropertyName: String = "asProperty",
            defaultBaseName: String,
            defaultSuffix: String,
            defaultAsProperty: Boolean,
        ): TransformAnnotationData {
            val baseName = annotationDescriptor.argumentValue(annotationBaseNamePropertyName)
                ?.accept(AbstractNullableAnnotationArgumentVoidDataVisitor.stringOnly, null)
                ?.takeIf { it.isNotEmpty() }

            val suffix = annotationDescriptor.argumentValue(annotationSuffixPropertyName)
                ?.accept(AbstractNullableAnnotationArgumentVoidDataVisitor.stringOnly, null)


            val rawAsProperty = annotationDescriptor.argumentValue(annotationAsPropertyPropertyName)
                ?.accept(AbstractNullableAnnotationArgumentVoidDataVisitor.booleanOnly, null)

            val functionName = "${baseName ?: defaultBaseName}${suffix ?: defaultSuffix}"

            return TransformAnnotationData(
                //annotationDescriptor,
                baseName,
                suffix,
                rawAsProperty,
                rawAsProperty ?: defaultAsProperty,
                functionName,
            )
        }

        fun of(
            firAnnotation: FirAnnotation,
            annotationBaseNamePropertyName: String = "baseName",
            annotationSuffixPropertyName: String = "suffix",
            annotationAsPropertyPropertyName: String = "asProperty",
            defaultBaseName: String,
            defaultSuffix: String,
            defaultAsProperty: Boolean,
        ): TransformAnnotationData {

            val baseName = firAnnotation.argumentMapping.mapping[Name.identifier(annotationBaseNamePropertyName)]
                ?.accept(AbstractFirAnnotationArgumentVoidDataVisitor.nullableString, null)
                ?.takeIf { it.isNotEmpty() }

            val suffix = firAnnotation.argumentMapping.mapping[Name.identifier(annotationSuffixPropertyName)]
                ?.accept(AbstractFirAnnotationArgumentVoidDataVisitor.nullableString, null)

            val rawAsProperty = firAnnotation.argumentMapping.mapping[Name.identifier(annotationAsPropertyPropertyName)]
                ?.accept(AbstractFirAnnotationArgumentVoidDataVisitor.nullableBoolean, null)

            val functionName = "${baseName ?: defaultBaseName}${suffix ?: defaultSuffix}"

            return TransformAnnotationData(
                baseName,
                suffix,
                rawAsProperty,
                rawAsProperty ?: defaultAsProperty,
                functionName,
            )
        }
    }


}


fun Transformer.resolveAnnotationData(
    functionDescriptor: FunctionDescriptor,
    containing: DeclarationDescriptor = functionDescriptor.containingDeclaration,
    defaultBaseName: String,
    annotationBaseNamePropertyName: String = this.markAnnotation.baseNameProperty,
    annotationSuffixPropertyName: String = this.markAnnotation.suffixProperty,
    annotationAsPropertyPropertyName: String = this.markAnnotation.asPropertyProperty,
): TransformAnnotationData? {
    val markAnnotationClassId = markAnnotation.classInfo.toClassId()
    val annotationFqn =
        markAnnotationClassId.asSingleFqName() // .packageFqName.child(markAnnotationClassId.shortClassName)

    val foundAnnotation = functionDescriptor.annotations.findAnnotation(annotationFqn)
        ?: containing.annotations.findAnnotation(annotationFqn)

    return foundAnnotation?.let {
        TransformAnnotationData.of(
            it,
            annotationBaseNamePropertyName,
            annotationSuffixPropertyName,
            annotationAsPropertyPropertyName,
            defaultBaseName,
            markAnnotation.defaultSuffix,
            markAnnotation.defaultAsProperty,
        )
    }
}

