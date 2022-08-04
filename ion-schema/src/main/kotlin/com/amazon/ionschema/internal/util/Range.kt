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

package com.amazon.ionschema.internal.util

import com.amazon.ionelement.api.*
import com.amazon.ionschema.InvalidSchemaException

/**
 * Enum representing the support types of ranges.
 */
internal enum class RangeType {
    INT,
    INT_NON_NEGATIVE,
    ION_NUMBER,
    ION_TIMESTAMP,
    ION_TIMESTAMP_PRECISION,
}

/**
 * Interface for all range implementations.
 */
internal interface Range<in T> {
    fun contains(value: T): Boolean
}

/**
 * Factory method for instantiating Range<T> objects.
 */
internal class RangeFactory {
    companion object {
        fun <T> rangeOf(ion: IonElement, rangeType: RangeType): Range<T> {
            if (ion.isNull) {
                throw InvalidSchemaException("Invalid range $ion")
            }

            val listElement = when (ion) {
                !is ListElement -> {
                    val range = ionListOf(ion, ion, annotations = listOf("range"))
                    range
                }
                else -> ion
            }

            checkRange(listElement)

            @Suppress("UNCHECKED_CAST")
            return when (rangeType) {
                RangeType.INT -> RangeInt(listElement)
                RangeType.INT_NON_NEGATIVE -> RangeIntNonNegative(listElement)
                RangeType.ION_NUMBER -> RangeIonNumber(listElement)
                RangeType.ION_TIMESTAMP -> RangeIonTimestamp(listElement)
                RangeType.ION_TIMESTAMP_PRECISION -> RangeIonTimestampPrecision(listElement)
            } as Range<T>
        }
    }
}

internal fun checkRange(ion: ListElement) {
    when {
        "range" !in ion.annotations ->
            throw InvalidSchemaException("Invalid range, missing 'range' annotation:  $ion")
        ion.size != 2 ->
            throw InvalidSchemaException("Invalid range, size of list must be 2:  $ion")
        ion[0].isNull || ion[1].isNull || (isRangeMin(ion[0]) && isRangeMax(ion[1])) ->
            throw InvalidSchemaException("Invalid range $ion")
    }
}

internal fun isRangeMin(ion: IonElement) = (ion as? SymbolElement)?.textValue == "min"
internal fun isRangeMax(ion: IonElement) = (ion as? SymbolElement)?.textValue == "max"

internal fun toInt(ion: IonElement) = (ion as? IntElement)?.longValue?.toInt()
