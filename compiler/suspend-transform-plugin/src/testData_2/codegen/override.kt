// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

@JvmBlocking
@JvmAsync
interface IFoo {
    suspend fun run(n: Int): Bar
}

interface Foo : IFoo {
    @JvmBlocking
    @JvmAsync
    suspend fun run(): Bar
    suspend fun run(name:String): Tar = Tar()
    override suspend fun run(n: Int): Bar
}

class FooImpl : Foo {
    override suspend fun run(): Tar = Tar()
    override suspend fun run(name:String): Tar = Tar()
    override suspend fun run(n: Int): Bar = Tar()
}

open class Bar
class Tar : Bar()
