/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package com.amazon.ionschema.model

import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionelement.api.field
import com.amazon.ionelement.api.ionStructOf

/**
 * Why is there a [ConstraintSerdeMediator] and a [ConstraintValidatorMediator] instead of one big mediator?
 * In theory, the AST should be language-version agnostic, so we want to be able to have 1 [ConstraintValidatorMediator],
 * and many [ConstraintSerdeMediator]s.
 */
class ConstraintSerdeMediator(delegate: ConstraintMediatorDelegate<ConstraintSerdeRegistration<*>>) : ConstraintMediator<ConstraintSerdeRegistration<*>> by delegate {
    constructor(registrations: List<ConstraintSerdeRegistration<*>>) : this(ConstraintMediatorDelegate<ConstraintSerdeRegistration<*>>(registrations))

    /**
     * Writes a single [Constraint] to a [StructField]
     */
    inline fun <reified T : Constraint<T>> writeConstraint(constraint: Constraint<T>): StructField {
        val registration: ConstraintSerdeRegistration<T> = get(constraint.id)
        return field(constraint.id.constraintName, registration.write(constraint as T))
    }

    /**
     * Reads a single [StructField] to produce an [Constraint]
     */
    fun readConstraint(field: StructField): Constraint<*> {
        return getByName(field.name).read(field.value)
    }
}

class ConstraintSerdeRegistration<T : Constraint<T>>(
    override val id: ConstraintId<T>,
    val read: (AnyElement) -> T,
    val write: (T) -> IonElement
) : Registration<ConstraintSerdeRegistration<T>, T>

// Example of how the ConstraintMediator can be used to read ISL and construct the AST.
fun readType(ion: StructElement, serdeMediator: ConstraintSerdeMediator): Type.Definition {
    return Type.Definition(
        constraints = ion.fields
            .filter { it.name in serdeMediator } // Find the fields that are constraints
            .map { serdeMediator.readConstraint(it) } // Read the fields as AstConstraint
            .toSet(),
        userContent = ion.fields.filterNot { it.name in serdeMediator && it.name == "name" }
    )
}

// Example of how the ConstraintMediator can be used to write ISL from the AST.
fun writeType(type: Type.Definition, serdeMediator: ConstraintSerdeMediator): IonElement {
    val fields: Iterable<StructField> = type.constraints.map { serdeMediator.writeConstraint(it) } + type.userContent
    return ionStructOf(fields)
}
