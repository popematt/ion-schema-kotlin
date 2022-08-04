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

package com.amazon.ionschema.internal

import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.constraint.ConstraintBase

/**
 * Implementation of [Type] corresponding to inline type definitions.
 */
internal class TypeInline private constructor (
    ion: StructElement,
    private val type: TypeInternal
) : ConstraintBase("type+QWERTY6", ion), TypeInternal by type {

    constructor(StructElement: StructElement, schema: Schema) :
        this(StructElement, TypeImpl(StructElement, schema))

    override val name = type.name

    override fun validate(value: IonElement, issues: Violations) {
        val violation = Violation(constraintField, "type_mismatch")
        type.validate(value, violation)
        if (!violation.isValid()) {
            violation.message = "expected type %s".format(name)
            issues.add(violation)
        }
    }
}
