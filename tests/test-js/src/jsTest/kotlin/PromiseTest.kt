import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 *
 * @author ForteScarlet
 */
class PromiseTest {

    @Test
    fun testPromise() = runTest {
        assertEquals(1, ForteScarlet().stringToInt("1"))
        assertIs<Promise<Int>>(ForteScarlet().asDynamic().stringToIntAsync("1"))
        assertEquals(1, ForteScarlet().asDynamic().stringToIntAsync("1").unsafeCast<Promise<Int>>().await())
    }

}
