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

import com.amazon.ionelement.api.ElementType
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.constraint.ConstraintBase
import com.amazon.ionschema.internal.util.DatagramElement

/**
 * Instantiated to represent individual Core Types as defined by the
 * Ion Schema Specification.
 */
internal class TypeCore(
    nameSymbol: SymbolElement
) : TypeInternal, ConstraintBase("type", nameSymbol), TypeBuiltin {

    private val ionType = when (nameSymbol.textValue.toUpperCase()) {
        "DOCUMENT" -> null
        else -> ElementType.valueOf(nameSymbol.textValue.toUpperCase())
    }

    private val ionTypeName = nameSymbol.withAnnotations().textValue

    override val name = ionTypeName

    override val schemaId: String? = null

    override val isl = nameSymbol

    override fun getBaseType() = this

    override fun isValidForBaseType(value: IonElement) = if (value is DatagramElement) {
        ionType == null
    } else {
        ionType == value.type
    }

    override fun validate(value: IonElement, issues: Violations) {
        if (!isValidForBaseType(value)) {
            issues.add(
                Violation(
                    constraintField, "type_mismatch",
                    "expected type %s, found %s".format(
                        ionTypeName,
                        value.schemaTypeName()
                    )
                )
            )
        } else if (value.isNull) {
            issues.add(CommonViolations.NULL_VALUE(constraintField))
        }
    }
}

internal fun IonElement.schemaTypeName() = when (this) {
    is DatagramElement -> "document"
    else -> type.toString().toLowerCase()
}
