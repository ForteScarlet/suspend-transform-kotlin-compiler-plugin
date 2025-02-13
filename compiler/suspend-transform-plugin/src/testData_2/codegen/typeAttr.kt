// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

open class Bar
class Tar : Bar()

class Api<T : Any>

interface Foo<out T : Bar> {
    @JvmBlocking
    @JvmAsync
    suspend fun value(): T

    @JvmBlocking
    @JvmAsync
    suspend fun <R : Any> run(api: Api<R>): R
}

class FooImpl : Foo<Tar> {
    override suspend fun value(): Tar {
        return Tar()
    }

    override suspend fun <R : Any> run(api: Api<R>): R {
        return TODO()
    }
}

