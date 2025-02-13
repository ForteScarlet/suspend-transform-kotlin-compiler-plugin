@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

import love.forte.plugin.suspendtrans.annotation.JsPromise

interface AsPropertyInterface {
    @JsExport.Ignore
    @JsPromise(asProperty = true)
    suspend fun value(): String
}

class AsPropertyImpl : AsPropertyInterface {
    @JsExport.Ignore
    @JsPromise(asProperty = true)
    override suspend fun value(): String = "Hello, World"
}
