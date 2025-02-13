package love.forte.plugin.suspendtrans.sample

import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

/**
 * 对“接受行为”的支持，由 [RequestEvent] 实现。
 *
 * @author ForteScarlet
 */
interface AcceptSupport {
    /**
     * 接受此请求。
     *
     * @throws Throwable 任何可能产生的错误。
     */
    @JvmBlocking
    @JvmAsync
    suspend fun accept()

    /**
     * 接受此请求。
     *
     * 对实现者：此函数具有默认实现以确保二进制兼容。
     *
     * @param options 用于当前接受行为的可选项。
     * 如果某选项实现不支持则会被忽略，支持的范围由实现者决定。
     * @since 4.0.0-RC3
     * @throws Throwable 任何可能产生的错误。
     */
    @JvmBlocking
    @JvmAsync
    suspend fun accept(vararg options: AcceptOption) {
        accept()
    }
}

/**
 * [AcceptSupport.accept] 的可选项。
 * [AcceptOption] 可以自由扩展，且如果遇到不支持的实现则会将其忽略。
 *
 * @see AcceptSupport.accept
 */
interface AcceptOption
