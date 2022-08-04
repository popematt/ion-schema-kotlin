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

import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.util.RangeFactory
import com.amazon.ionschema.internal.util.RangeType

/**
 * Base class for constraints that validate an int value
 * against a non-negative int range.
 */
internal abstract class ConstraintBaseIntRange<T : IonElement>(
    private val expectedClass: Class<T>,
    ion: StructField
) : ConstraintBase(ion) {

    internal val range = RangeFactory.rangeOf<Int>(ion.value, RangeType.INT_NON_NEGATIVE)

    internal abstract val violationCode: String
    internal abstract val violationMessage: String

    override fun validate(value: IonElement, issues: Violations) {
        validateAs(expectedClass, value, issues) { v ->
            val intValue = getIntValue(v)
            if (!range.contains(intValue)) {
                issues.add(Violation(constraintField, violationCode, violationMessage.format(intValue, range)))
            }
        }
    }

    abstract fun getIntValue(value: T): Int
}
