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
import com.amazon.ionelement.api.ListElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionschema.InvalidSchemaException
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Type
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.TypeReference

/**
 * Base class for logic constraint implementations.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#logic-constraints
 */
internal abstract class LogicConstraints(
    ion: StructField,
    schema: Schema
) : ConstraintBase(ion) {

    internal val types = ion.value.let {
        if (it is ListElement && !it.isNull) {
            it.values.map { t -> TypeReference.create(t, schema) }
        } else {
            throw InvalidSchemaException("Expected a list, found: $ion")
        }
    }

    internal fun validateTypes(value: IonElement, issues: Violations): List<Type> {
        val validTypes = mutableListOf<Type>()
        types.forEach {
            val checkpoint = issues.checkpoint()
            it().validate(value, issues)
            if (checkpoint.isValid()) {
                validTypes.add(it())
            }
        }
        return validTypes
    }
}

/**
 * Implements the all_of constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#all_of
 */
internal class AllOf(ion: StructField, schema: Schema) : LogicConstraints(ion, schema) {
    override fun validate(value: IonElement, issues: Violations) {
        val allOfViolation = Violation(constraintField, "all_types_not_matched")
        val count = validateTypes(value, allOfViolation).size
        if (count != types.size) {
            allOfViolation.message = "value matches $count types, expected ${types.size}"
            issues.add(allOfViolation)
        }
    }
}

/**
 * Implements the any_of constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#any_of
 */
internal class AnyOf(ion: StructField, schema: Schema) : LogicConstraints(ion, schema) {
    override fun validate(value: IonElement, issues: Violations) {
        val anyOfViolation = Violation(constraintField, "no_types_matched", "value matches none of the types")
        types.forEach {
            val checkpoint = anyOfViolation.checkpoint()
            it().validate(value, anyOfViolation)
            // We can exit at the first valid type we encounter
            if (checkpoint.isValid()) return
        }
        issues.add(anyOfViolation)
    }
}

/**
 * Implements the one_of constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#one_of
 */
internal class OneOf(ion: StructField, schema: Schema) : LogicConstraints(ion, schema) {
    override fun validate(value: IonElement, issues: Violations) {
        val oneOfViolation = Violation(constraintField)
        val validTypes = validateTypes(value, oneOfViolation)
        if (validTypes.size != 1) {
            if (validTypes.size == 0) {
                oneOfViolation.code = "no_types_matched"
                oneOfViolation.message = "value matches none of the types"
            }
            if (validTypes.size > 1) {
                oneOfViolation.code = "more_than_one_type_matched"
                oneOfViolation.message = "value matches %s types, expected 1".format(validTypes.size)

                validTypes.forEach {
                    val typeDef = (it as ConstraintBase).constraintField
                    oneOfViolation.add(
                        Violation(
                            typeDef, "type_matched",
                            "value matches type %s".format(typeDef)
                        )
                    )
                }
            }
            issues.add(oneOfViolation)
        }
    }
}

/**
 * Implements the not constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#not
 */
internal class Not(ion: StructField, schema: Schema) : ConstraintBase(ion) {
    private val type = TypeReference.create(ion.value, schema)

    override fun validate(value: IonElement, issues: Violations) {
        val child = Violation(constraintField, "type_matched", "value unexpectedly matches type")
        type().validate(value, child)
        if (child.isValid()) {
            issues.add(child)
        }
    }
}
