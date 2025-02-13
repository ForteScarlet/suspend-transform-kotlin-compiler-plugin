// FIR_DUMP
// DUMP_IR
// SOURCE
// FILE: Main.kt [MainKt#main]

import kotlinx.coroutines.runBlocking
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking

typealias MoneyValue = Long

class MyClass {
    @JvmBlocking
    @JvmAsync
    suspend fun errorReproduction(amount: MoneyValue) { println(amount) }
}
