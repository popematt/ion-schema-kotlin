/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ionschema

import com.amazon.ion.IonValue
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionschema.util.CloseableIterator
import java.io.Closeable

/**
 * An Authority is responsible for resolving a particular class of
 * schema identifiers.
 *
 * The structure of a schema identifier string is defined by the
 * Authority responsible for the schema/type(s) being imported.
 *
 * **Runtime resolution of a schema over a network presents availability and security risks, and should thereby be avoided.**
 *
 * @see AuthorityFilesystem
 */
interface Authority {
    private companion object {
        private val ISS = IonSchemaSystemBuilder.standard().build()
    }

    fun sequenceFor(id: String): CloseableSequence<IonElement>? {
        return when (val itr = iteratorFor(ISS, id)) {
            EMPTY_ITERATOR -> null
            else -> CloseableIonElementSequence(itr)
        }
    }

    /**
     * Provides a CloseableIterator<IonElement> for the requested schema identifier.
     * If an error condition is encountered while attempting to resolve the schema
     * identifier, this method should throw an exception.  If no error conditions
     * were encountered, but the schema identifier can't be resolved, this method
     * should return [EMPTY_ITERATOR]. This is distinct from returning an iterator
     * with no elements.
     */
    fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> = TODO()
}

abstract class CloseableSequence<T> : Closeable {
    inline fun <R> use(block: (Sequence<T>) -> R): R {
        var exception: Throwable? = null
        try {
            return block(this.iterator().asSequence())
        } catch (e: Throwable) {
            exception = e
            throw e
        } finally {
            when (exception) {
                null -> close()
                else -> try {
                    close()
                } catch (closeException: Throwable) {
                    exception.addSuppressed(closeException)
                }
            }
        }
    }

    @PublishedApi
    internal abstract operator fun iterator(): Iterator<T>
}

fun <T> Sequence<T>.toCloseableSequence(): CloseableSequence<T> = CloseableDecoratorForSequence(this)
fun <T> Iterator<T>.toCloseableSequence(): CloseableSequence<T> = asSequence().toCloseableSequence()
fun <T> Iterable<T>.toCloseableSequence(): CloseableSequence<T> = asSequence().toCloseableSequence()

internal fun <T> Iterator<T>.asCloseableIterator(): CloseableIterator<T> {
    return object : CloseableIterator<T>, Iterator<T> by this {
        override fun close() = Unit
    }
}

internal class CloseableDecoratorForSequence<T>(seq: Sequence<T>) :
    CloseableSequence<T>(),
    Sequence<T> by seq,
    Closeable by NO_OP_CLOSEABLE {
    private companion object {
        val NO_OP_CLOSEABLE = Closeable { }
    }
}

internal class CloseableIonElementSequence(itr: CloseableIterator<IonValue>) :
    CloseableSequence<IonElement>(),
    Sequence<IonElement> by itr.asSequence().map({ it.toIonElement() }),
    Closeable by itr

/**
 * A singleton iterator which has nothing to iterate over.
 */
val EMPTY_ITERATOR = object : CloseableIterator<IonValue> {
    override fun hasNext() = false
    override fun next(): IonValue = throw NoSuchElementException()
    override fun close() { }
}
