// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]

import kotlinx.coroutines.runBlocking
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

class PropFoo {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking(asProperty = true, suffix = "")
    @love.forte.plugin.suspendtrans.annotation.JvmAsync(asProperty = true)
    suspend fun prop(): String = ""
}


interface IProp {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking(asProperty = true, suffix = "")
    @love.forte.plugin.suspendtrans.annotation.JvmAsync(asProperty = true)
    suspend fun prop(): String
}

class PropImpl : IProp {
    @love.forte.plugin.suspendtrans.annotation.JvmBlocking(asProperty = true, suffix = "")
    @love.forte.plugin.suspendtrans.annotation.JvmAsync(asProperty = true)
    override suspend fun prop(): String = ""
}
