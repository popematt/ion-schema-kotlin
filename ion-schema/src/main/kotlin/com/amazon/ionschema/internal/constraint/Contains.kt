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

/**
 * Implements the contains constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#contains
 */
internal class Contains(
    ion: StructField
) : ConstraintBase(ion) {

    private val expectedElements = if (ion.value !is ListElement) {
        throw InvalidSchemaException("Expected annotations as a list, found: $ion")
    } else {
        ion.value.asList().values
    }

    override fun validate(value: IonElement, issues: Violations) {
        validateAs<ContainerElement>(value, issues) { v ->
            val expectedValues = expectedElements.toMutableSet()
            v.values.forEach {
                expectedValues.remove(it)
            }
            if (!expectedValues.isEmpty()) {
                issues.add(
                    Violation(
                        constraintField, "missing_values",
                        "missing value(s): " + expectedValues.joinToString { it.toString() }
                    )
                )
            }
        }
    }
}
