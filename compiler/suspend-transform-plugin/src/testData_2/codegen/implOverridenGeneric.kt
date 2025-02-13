// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]

interface Foo
interface Bar : Foo


interface FooInterface1<T : Foo> {
    suspend fun data(): T
    suspend fun <A> data2(value: A): T
    suspend fun Int.data3(): T
}

class FooInterface1Impl<T : Bar> : FooInterface1<T> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun <A> data2(value: A): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): T = TODO()
}

interface FooInterface2<T : Foo> {
    suspend fun data(): T
    suspend fun data2(value: T): T
    suspend fun Int.data3(): T
}

class FooInterface2Impl<T : Foo> : FooInterface2<T> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data2(value: T): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): T = TODO()
}

interface FooInterface3<T : Foo> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun data(): T
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun <A> data2(value: A): T
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun Int.data3(): T
}

class FooInterface3Impl<T : Bar> : FooInterface3<T> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun <A> data2(value: A): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): T = TODO()
}

interface FooInterface4<T : Foo> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun data(): T
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun data2(value: T): T
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun Int.data3(): T
}

class FooInterface4Impl<T : Foo> : FooInterface4<T> {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data2(value: T): T = TODO()

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): T = TODO()
}

