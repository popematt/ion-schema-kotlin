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

import com.amazon.ion.Decimal
import com.amazon.ionelement.api.ListElement
import com.amazon.ionelement.api.TimestampElement
import com.amazon.ionelement.api.ionDecimal
import com.amazon.ionelement.api.ionListOf
import com.amazon.ionschema.InvalidSchemaException

/**
 * Implementation of Range<IonTimestamp> which mostly delegates to RangeBigDecimal.
 */
internal class RangeIonTimestamp private constructor (
    private val delegate: RangeBigDecimal
) : Range<TimestampElement> {

    constructor (ion: ListElement) : this(toRangeBigDecimal(ion))

    companion object {
        private fun toRangeBigDecimal(ion: ListElement): RangeBigDecimal {
            checkRange(ion)

            // convert to a decimal range
            val newRangeBoundaries = ion.values.map { ionElement ->
                val newValue = if (ionElement is TimestampElement) {
                    if (ionElement.timestampValue.localOffset == null) {
                        throw InvalidSchemaException(
                            "Timestamp range bound doesn't specify a local offset: $ionElement"
                        )
                    }
                    ionDecimal(Decimal.valueOf(ionElement.timestampValue.decimalMillis))
                } else {
                    ionElement
                }
                newValue.withAnnotations(ionElement.annotations)
            }
            val newRange = ionListOf(newRangeBoundaries, annotations = listOf("range"))
            return RangeBigDecimal(newRange)
        }
    }

    override fun contains(value: TimestampElement): Boolean {
        // ValidValues performs this same check and adds a Violation
        // instead of invoking this method;  this if is here purely
        // as a defensive safety check, and will ideally never be true
        return if (value.timestampValue.localOffset == null)
            false
        else
            delegate.contains(value.timestampValue.decimalMillis)
    }
}
