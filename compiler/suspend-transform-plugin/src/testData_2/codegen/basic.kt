// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]

import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

class BasicFoo {
    @JvmAsync
    suspend fun foo(): String = "foo"
}

class BasicBar {
    @JvmAsync
    suspend fun bar(): String = "bar"
    @JvmAsync
    suspend fun bar2(i: Int): String = "bar2"
}

interface InterfaceBar {
    @JvmAsync
    suspend fun bar(): String
    @JvmAsync
    suspend fun bar2(i: Int): String

    fun asyncBase(i: Int): ResultValue<out String>
}

class ResultValue<T>
