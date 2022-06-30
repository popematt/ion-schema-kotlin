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

package com.amazon.ionschema.util

import java.io.Closeable

/**
 * An Iterator that has the opportunity to free up any resources
 * when [close()] is called, after it is no longer needed.
 */
interface CloseableIterator<T> : Iterator<T>, Closeable

fun <T: Any> CloseableIterator<T>.asIterable(): Iterable<T> {
    return object: Iterable<T> {
        override fun iterator(): Iterator<T> = object: Iterator<T> by this@asIterable {
            override fun hasNext(): Boolean {
                return this@asIterable.hasNext().also { hasNext -> if (!hasNext) close() }
            }
        }
    }
}
fun <T: Any> Iterable<T>.asCloseableIterator(): CloseableIterator<T> {
    return object: CloseableIterator<T>, Iterator<T> by this@asCloseableIterator.iterator() {
        override fun close() = Unit
    }
}

