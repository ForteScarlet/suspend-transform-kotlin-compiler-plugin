// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

interface Api<T : Any> {
    suspend fun run(): T
}

interface ApiResult<T : Any>

interface ApiExecutable {
    @JvmAsync
    suspend fun <T : Any> execute(api: Api<T>): ApiResult<Api<T>>
}
