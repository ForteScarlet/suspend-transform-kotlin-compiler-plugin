// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]

import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import love.forte.plugin.suspendtrans.annotation.JvmAsync

interface FooInterface1 {
    suspend fun data(): String
    suspend fun data2(value: Int): String
    suspend fun Int.data3(): String
}

class FooInterface1Impl : FooInterface1 {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): String = ""

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data2(value: Int): String = ""

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): String = ""
}

interface FooInterface2 {
    suspend fun data(): String
    fun dataBlocking(): String = ""

    suspend fun data2(value: Int): String
    fun data2Blocking(value: Int): String = ""

    suspend fun Int.data3(): String
    fun Int.data3Blocking(): String = ""
}

class FooInterface2Impl : FooInterface2 {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): String = ""

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data2(value: Int): String = ""

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun Int.data3(): String = ""
}


interface FooInterface3 {
    suspend fun data(): String
    val dataBlocking: String get() = ""
}

class FooInterface3Impl : FooInterface3 {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking(asProperty = true)
    override suspend fun data(): String = ""
}

interface FooInterface4 {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun data(): String

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    suspend fun data2(value: Int): String
}

class FooInterface4Impl : FooInterface4 {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data(): String = ""

    @love.forte.plugin.suspendtrans.annotation.JvmBlocking
    override suspend fun data2(value: Int): String = ""
}

