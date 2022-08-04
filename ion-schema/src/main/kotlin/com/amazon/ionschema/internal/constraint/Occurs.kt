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
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Violation
import com.amazon.ionschema.ViolationChild
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.TypeReference
import com.amazon.ionschema.internal.constraint.Occurs.Companion.toRange
import com.amazon.ionschema.internal.util.*
import com.amazon.ionschema.internal.util.Range
import com.amazon.ionschema.internal.util.RangeFactory
import com.amazon.ionschema.internal.util.RangeIntNonNegative
import com.amazon.ionschema.internal.util.RangeType

/**
 * Implements the occurs constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#occurs
 */
internal class Occurs(
    variablyOccurringTypeDefinition: IonElement,
    schema: Schema,
    defaultRange: DefaultOccurs,
) : ConstraintBase(field("occurs", getOccursElement(variablyOccurringTypeDefinition, defaultRange))) {

    internal val range: Range<Int> = toRange(ion)
    private val typeReference = TypeReference.create(variablyOccurringTypeDefinition, schema, isField = true)
    private var attempts = 0
    internal var validCount = 0

    enum class DefaultOccurs(val range: Range<Int>, val ion: IonElement) {
        OPTIONAL(
            RangeFactory.rangeOf<Int>(
                loadSingleElement("range::[0, 1]"),
                RangeType.INT_NON_NEGATIVE
            ),
            ionSymbol("optional")
        ),
        REQUIRED(
            RangeFactory.rangeOf<Int>(
                loadSingleElement("range::[1, 1]"),
                RangeType.INT_NON_NEGATIVE
            ),
            ionSymbol("required")
        );
    }

    companion object {
        internal fun toRange(ion: IonElement): Range<Int> {
            if (!ion.isNull) {
                return if (ion is SymbolElement) {
                    when (ion) {
                        DefaultOccurs.OPTIONAL.ion -> DefaultOccurs.OPTIONAL.range
                        DefaultOccurs.REQUIRED.ion -> DefaultOccurs.REQUIRED.range
                        else -> throw InvalidSchemaException("Invalid ion constraint '$ion'")
                    }
                } else {
                    val range = RangeFactory.rangeOf<Int>(ion, RangeType.INT_NON_NEGATIVE)
                    if (range.contains(0) && !range.contains(1)) {
                        throw InvalidSchemaException("Occurs must allow at least one value ($ion)")
                    }
                    range
                }
            }
            throw InvalidSchemaException("Invalid occurs constraint '$ion'")
        }

        fun getOccursElement(ionElement: IonElement, defaultRange: DefaultOccurs): IonElement {
            var occurs: IonElement? = null
            if (ionElement is StructElement && !ionElement.isNull) {
                occurs = ionElement.getOrNull("occurs")?.value
            }
            return occurs ?: defaultRange.ion
        }
    }

    override fun validate(value: IonElement, issues: Violations) {
        attempts++

        typeReference().validate(value, issues)
        validCount = attempts - issues.violations.size
        (issues as ViolationChild).addValue(value)
    }

    fun validateAttempts(issues: Violations) {
        if (!range.contains(attempts)) {
            issues.add(
                Violation(
                    constraintField, "occurs_mismatch",
                    "expected %s occurrences, found %s".format(range, attempts)
                )
            )
        }
    }

    fun validateValidCount(issues: Violations) {
        if (!isValidCountWithinRange()) {
            issues.add(
                Violation(
                    constraintField, "occurs_mismatch",
                    "expected %s occurrences, found %s".format(range, validCount)
                )
            )
        }
    }

    internal fun isValidCountWithinRange() = range.contains(validCount)

    internal fun attemptsSatisfyOccurrences() = range.contains(attempts)
    internal fun canConsumeMore() = !(range as RangeIntNonNegative).isAtMax(attempts)
}

/**
 * This class should only be used during load/validation of a type definition.
 * The real Occurs constraint implementation is instantiated and used for validation
 * by the Fields and OrderedElements constraints.
 */
internal open class OccursNoop(
    ion: StructField
) : ConstraintBase(ion) {

    init {
        toRange(ion.value)
    }

    override fun validate(value: IonElement, issues: Violations) {
        // no-op
    }
}
