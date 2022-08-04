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
import com.amazon.ionelement.api.field
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.CommonViolations
import com.amazon.ionschema.internal.Constraint

/**
 * Base class for constraint implementations.
 */
internal abstract class ConstraintBase(
    val constraintField: StructField,
) : Constraint {

    constructor(name: String, value: IonElement) : this(field(name, value))

    override val name = constraintField.name
    val ion = constraintField.value

    internal inline fun <reified T> validateAs(value: IonElement, issues: Violations, noinline customValidation: (T) -> Unit) =
        validateAs(T::class.java, value, issues, customValidation)

    internal fun <T> validateAs(expectedClass: Class<T>, value: IonElement, issues: Violations, customValidation: (T) -> Unit) {
        when {
            !expectedClass.isInstance(value) -> issues.add(CommonViolations.INVALID_TYPE(constraintField, value))
            value.isNull -> issues.add(CommonViolations.NULL_VALUE(constraintField))
            else ->
                @Suppress("UNCHECKED_CAST")
                customValidation(value as T)
        }
    }

    override fun toString() = ion.toString()
}
