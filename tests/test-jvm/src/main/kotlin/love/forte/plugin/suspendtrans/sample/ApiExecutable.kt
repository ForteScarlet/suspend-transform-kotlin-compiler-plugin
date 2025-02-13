package love.forte.plugin.suspendtrans.sample

import love.forte.plugin.suspendtrans.annotation.JvmAsync

interface Api<T : Any> {
    suspend fun run(): T
}

interface ApiResult<T : Any>

/**
 *
 * @author ForteScarlet
 */
interface ApiExecutable {
    @JvmAsync
    suspend fun <T : Any> execute(api: Api<T>): T

    @JvmAsync
    suspend fun <T : Any> execute2(api: Api<T>): ApiResult<T>

    @JvmAsync
    suspend fun <T : Any> execute3(api: Api<T>): ApiResult<Api<T>>
}
