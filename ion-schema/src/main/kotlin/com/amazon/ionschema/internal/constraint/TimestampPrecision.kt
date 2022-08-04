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
import com.amazon.ionelement.api.TimestampElement
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.util.IonTimestampPrecision
import com.amazon.ionschema.internal.util.RangeFactory
import com.amazon.ionschema.internal.util.RangeType

/**
 * Implements the timestamp_precision constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#timestamp_precision
 */
internal class TimestampPrecision(
    ion: StructField
) : ConstraintBase(ion) {

    private val range = RangeFactory.rangeOf<TimestampElement>(ion.value, RangeType.ION_TIMESTAMP_PRECISION)

    override fun validate(value: IonElement, issues: Violations) {
        validateAs<TimestampElement>(value, issues) { v ->
            if (!range.contains(v)) {
                val actualPrecision = IonTimestampPrecision.toInt(v)
                issues.add(
                    Violation(
                        constraintField, "invalid_timestamp_precision",
                        "invalid timestamp precision %s, expected %s".format(actualPrecision, ion)
                    )
                )
            }
        }
    }
}
