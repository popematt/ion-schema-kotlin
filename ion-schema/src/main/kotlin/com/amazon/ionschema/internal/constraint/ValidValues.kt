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

package com.amazon.ionschema.internal.constraint

import com.amazon.ionelement.api.*
import com.amazon.ionschema.InvalidSchemaException
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.util.*
import com.amazon.ionschema.internal.util.Range
import com.amazon.ionschema.internal.util.RangeFactory
import com.amazon.ionschema.internal.util.RangeType

/**
 * Implements the valid_values constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#valid_values
 */
internal class ValidValues(
    field: StructField
) : ConstraintBase(field) {

    // store either the ranges that are built or the ion value to be used for validation
    private val validValues = (
        if (isValidRange(ion)) {
            // convert range::[x,y] to [range::[x,y]] for simplicity in verifying and storing valid_values
            setOf(ion)
        } else if (ion is ListElement && !ion.isNull) {
            ion.values.onEach { checkValue(it) }.toSet()
        } else {
            throw InvalidSchemaException("Invalid valid_values constraint: $ion")
        }
        ).map { buildRange(it) }

    private fun isValidRange(ion: IonElement) = ion is ListElement && !ion.isNull && "range" in ion.annotations

    // build range value from given ion value if valid range or return ion value itself
    private fun buildRange(ion: IonElement) =
        if (ion is ListElement && isValidRange(ion)) {
            if (ion[0] is TimestampElement || ion[1] is TimestampElement) {
                @Suppress("UNCHECKED_CAST")
                RangeFactory.rangeOf<TimestampElement>(ion, RangeType.ION_TIMESTAMP) as Range<IonElement>
            } else {
                RangeFactory.rangeOf<IonElement>(ion, RangeType.ION_NUMBER)
            }
        } else {
            ion
        }

    private fun checkValue(ion: IonElement) =
        if (isValidRange(ion)) {
            ion
        } else if (ion.annotations.isNotEmpty()) {
            throw InvalidSchemaException("Annotations ($ion) are not allowed in valid_values")
        } else {
            ion
        }

    override fun validate(value: IonElement, issues: Violations) {
        val v = value.withoutAnnotations()
        val anyMatch = validValues.any { possibility ->
            when (possibility) {
                is IonElement -> possibility == v
                is RangeIonTimestamp -> when {
                    v !is TimestampElement -> false
                    v.timestampValue.localOffset == null -> false
                    else -> possibility.contains(v)
                }
                is RangeIonNumber -> possibility.contains(v)
                else -> TODO("This is unreachable!")
            }
        }

        if (!anyMatch) {
            issues.add(Violation(constraintField, "invalid_value", "invalid value $v for ${this.validValues.joinToString { it.javaClass.simpleName }}"))
        }
    }
}
