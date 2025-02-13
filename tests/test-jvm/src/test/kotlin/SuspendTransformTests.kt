/*
 *     Copyright (c) 2024. ForteScarlet.
 *
 *     Project    https://github.com/simple-robot/simpler-robot
 *     Email      ForteScarlet@163.com
 *
 *     This file is part of the Simple Robot Library.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     Lesser GNU General Public License for more details.
 *
 *     You should have received a copy of the Lesser GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.memberProperties
import kotlin.test.*


@JvmBlocking
@JvmAsync
interface STTrans1 {
    suspend fun run1()
    suspend fun run1(value: String): String
}

@JvmBlocking(suffix = "", asProperty = true)
@JvmAsync(suffix = "Ay", asProperty = true)
interface STPTrans1 {
    suspend fun run1(): Int
    suspend fun run2(): String
}

open class Foo
open class Bar : Foo()

@JvmBlocking(asProperty = true)
@JvmAsync(asProperty = true)
interface ITypedTrans1<T : Foo> {
    suspend fun value(): T
}

@JvmBlocking(asProperty = true)
@JvmAsync(asProperty = true)
interface TypedTrans1Impl<T : Bar> : ITypedTrans1<T> {
    override suspend fun value(): T
}

class STPTrans1Impl : STPTrans1 {
    override suspend fun run1(): Int = 1
    override suspend fun run2(): String = "run2"
}

/**
 *
 * @author ForteScarlet
 */
class SuspendTransformTests {

    @Test
    fun `interface suspend trans function test`() {
        with(STTrans1::class.java) {
            val blockingMethod = getMethod("run1Blocking")
            val asyncMethod = getMethod("run1Async")

            assertEquals(Void.TYPE, blockingMethod.returnType)
            assertEquals(CompletableFuture::class.java, asyncMethod.returnType)

            assertFalse(Modifier.isAbstract(blockingMethod.modifiers))
            assertFalse(Modifier.isAbstract(asyncMethod.modifiers))
        }

        with(STTrans1::class.java) {
            val blockingMethod = getMethod("run1Blocking", String::class.java)
            val asyncMethod = getMethod("run1Async", String::class.java)

            assertEquals(String::class.java, blockingMethod.returnType)
            assertEquals(CompletableFuture::class.java, asyncMethod.returnType)

            assertFalse(Modifier.isAbstract(blockingMethod.modifiers))
            assertFalse(Modifier.isAbstract(asyncMethod.modifiers))
        }
    }

    @Test
    fun `interface suspend trans property test`() {
        with(STPTrans1::class.memberProperties) {
            assertTrue(any { it.name == "run1" && it.returnType.classifier == Int::class })
            assertTrue(any { it.name == "run2" && it.returnType.classifier == String::class })
        }

        // run1
        with(STPTrans1::class.java) {
            val blockingPropertyMethod = getMethod("getRun1")
            val asyncPropertyMethod = getMethod("getRun1Ay")

            assertEquals(Int::class.javaPrimitiveType, blockingPropertyMethod.returnType)
            assertEquals(CompletableFuture::class.java, asyncPropertyMethod.returnType)

            assertFalse(Modifier.isAbstract(blockingPropertyMethod.modifiers))
            assertFalse(Modifier.isAbstract(asyncPropertyMethod.modifiers))
        }

        // run2
        with(STPTrans1::class.java) {
            val blockingPropertyMethod = getMethod("getRun2")
            val asyncPropertyMethod = getMethod("getRun2Ay")

            assertEquals(String::class.java, blockingPropertyMethod.returnType)
            assertEquals(CompletableFuture::class.java, asyncPropertyMethod.returnType)

            assertFalse(Modifier.isAbstract(blockingPropertyMethod.modifiers))
            assertFalse(Modifier.isAbstract(asyncPropertyMethod.modifiers))
        }

    }

    @Test
    fun `typed interface test`() {
        with(ITypedTrans1::class) {
            assertTrue(memberProperties.any {
                it.name == "valueBlocking" && with(it.returnType.classifier) {
                    this is KTypeParameter && this.upperBounds.any { b -> b.classifier == Foo::class }
                }
            })
            assertTrue(memberProperties.any {
                it.name == "valueAsync" && it.returnType.classifier == CompletableFuture::class
            })
        }
        with(TypedTrans1Impl::class) {
            assertTrue(memberProperties.any {
                it.name == "valueBlocking" && with(it.returnType.classifier) {
                    this is KTypeParameter && this.upperBounds.any { b -> b.classifier == Bar::class }
                }
            })
            assertTrue(memberProperties.any {
                it.name == "valueAsync" && it.returnType.classifier == CompletableFuture::class
            })
        }
    }

    @Test
    fun `invoke test`() {
        val stp = STPTrans1Impl()

        val run1BlockingMethod = STPTrans1::class.java.getMethod("getRun1")
        val run2BlockingMethod = STPTrans1::class.java.getMethod("getRun2")

        val run1AsyncMethod = STPTrans1::class.java.getMethod("getRun1Ay")
        val run2AsyncMethod = STPTrans1::class.java.getMethod("getRun2Ay")

        assertEquals(1, run1BlockingMethod.invoke(stp))
        assertEquals("run2", run2BlockingMethod.invoke(stp))

        val run1Async = run1AsyncMethod.invoke(stp)
        val run2Async = run2AsyncMethod.invoke(stp)

        assertIs<CompletableFuture<Int>>(run1Async)
        assertIs<CompletableFuture<String>>(run2Async)

        assertEquals(1, run1Async.get())
        assertEquals("run2", run2Async.get())
    }
}
