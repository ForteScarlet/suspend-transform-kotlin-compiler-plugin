import kotlinx.coroutines.await
import kotlin.js.Promise

suspend fun main() {
    println(ForteScarlet().stringToInt("1"))
    println(ForteScarlet().asDynamic().stringToIntAsync("1"))
    println(ForteScarlet().asDynamic().stringToIntAsync("1").unsafeCast<Promise<Int>>().await())

}
