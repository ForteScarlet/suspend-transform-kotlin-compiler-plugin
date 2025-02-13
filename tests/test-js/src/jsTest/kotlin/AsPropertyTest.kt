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
class AsPropertyTest {

    @Test
    fun testAsProperty() = runTest {
        val impl = AsPropertyImpl()
        assertIs<Promise<String>>(impl.asDynamic().valueAsync)
        assertEquals("Hello, World", impl.asDynamic().valueAsync.unsafeCast<Promise<String>>().await())
    }

}
