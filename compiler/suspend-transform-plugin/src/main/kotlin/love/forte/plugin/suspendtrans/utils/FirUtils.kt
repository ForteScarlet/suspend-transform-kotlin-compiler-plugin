package love.forte.plugin.suspendtrans.utils

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation

fun FirDeclaration.includeAnnotations(includes: List<FirAnnotation>) {
    replaceAnnotations(annotations + includes)
}
