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
import com.amazon.ionschema.internal.Constraint

/**
 * Implements the fields constraint.
 *
 * [Content] and [Occurs] constraints in the context of a struct are also
 * handled by this class.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#fields
 */
internal class Fields(
    ion: StructField,
    container: StructElement,
    private val schema: Schema
) : ConstraintBase(ion), Constraint {

    private val structElement: StructElement
    private val contentConstraintIon: SymbolElement?
    private val contentClosed: Boolean

    init {
        if (ion.value.isNull || ion.value !is StructElement || ion.value.asStruct().fields.isEmpty()) {
            throw InvalidSchemaException(
                "fields must be a struct that defines at least one field ($ion)"
            )
        }
        structElement = ion.value.asStruct()

        // Validates the fields
        structElement.fields.associateBy(
            { it.name },
            { Occurs(it.value, schema, Occurs.DefaultOccurs.OPTIONAL) }
        )

        contentConstraintIon = container.getOptional("content") as? SymbolElement
        contentClosed = contentConstraintIon?.textValue.equals("closed")
    }

    override fun validate(value: IonElement, issues: Violations) {
        validateAs<StructElement>(value, issues) { v ->
            val fieldIssues = Violation(constraintField, "fields_mismatch", "one or more fields don't match expectations")
            val fieldConstraints = structElement.fields.associateBy(
                { it.name },
                {
                    Pair(
                        Occurs(it.value, schema, Occurs.DefaultOccurs.OPTIONAL),
                        ViolationChild(fieldName = it.name)
                    )
                }
            )
            var closedContentIssues: Violation? = null

            v.fields.iterator().forEach {
                val pair = fieldConstraints[it.name]
                if (pair != null) {
                    pair.first.validate(it.value, pair.second)
                } else if (contentClosed) {
                    if (closedContentIssues == null) {
                        closedContentIssues = Violation(
                            field("content", contentConstraintIon!!),
                            "unexpected_content", "found one or more unexpected fields"
                        )
                        issues.add(closedContentIssues!!)
                    }
                    closedContentIssues!!.add(ViolationChild(it.name, value = it.value))
                }
            }

            fieldConstraints.values.forEach { pair ->
                pair.first.validateAttempts(pair.second)
                if (!pair.second.isValid()) {
                    fieldIssues.add(pair.second)
                }
            }
            if (!fieldIssues.isValid()) {
                issues.add(fieldIssues)
            }
        }
    }
}
