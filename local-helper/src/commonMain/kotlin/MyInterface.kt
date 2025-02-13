import kotlinx.coroutines.CoroutineScope
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 *
 * @author ForteScarlet
 */
@JsExport
interface MyInterface {

    @JsExport.Ignore
    suspend fun run(times: Int): Int

    @Suppress("NOTHING_TO_INLINE")
    private inline fun __suspendTransform__run_0_runBlocking(noinline block: suspend () -> Int): Int {
        return runBlocking(block, this as? CoroutineScope)
    }

    fun runBlocking(times: Int): Int = __suspendTransform__run_0_runBlocking({ run(times) })
}

@JsExport
@OptIn(ExperimentalJsExport::class)
interface MyInterface2 : MyInterface {
    @JsExport.Ignore
    suspend fun <T> run2(times: T): T

    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> __suspendTransform__run2_0_runBlocking(noinline block: suspend () -> T): T {
        return runBlocking(block, this as? CoroutineScope)
    }

    fun <T> run2Blocking(value: T): T = __suspendTransform__run2_0_runBlocking({ run2(value) })

}

fun <T> runBlocking(block: suspend () -> T, scope: CoroutineScope?): T = TODO()
