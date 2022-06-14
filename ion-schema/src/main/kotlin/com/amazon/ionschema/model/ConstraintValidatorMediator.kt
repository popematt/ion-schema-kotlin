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
import com.amazon.ionschema.model.constraints.ByteLengthConstraint
import com.amazon.ionschema.model.constraints.ContainsConstraint

class ConstraintValidatorMediator private constructor(delegate: ConstraintMediatorDelegate<ConstraintValidatorRegistration<*>>) : ConstraintMediator<ConstraintValidatorRegistration<*>> by delegate {
    constructor(registrations: List<ConstraintValidatorRegistration<*>>) : this(ConstraintMediatorDelegate<ConstraintValidatorRegistration<*>>(registrations))

    /**
     * Validates an IonElement against a single constraint
     */
    inline fun <reified T : Constraint<T>> validateConstraint(constraint: Constraint<T>, data: IonElement): Boolean {
        return validateConstraint(constraint as T, data.asAnyElement())
    }

    fun <T : Constraint<T>> validateConstraint(constraint: T, data: AnyElement): Boolean {
        val registration: ConstraintValidatorRegistration<T> = get(constraint.id)
        return registration.validate(constraint, data)
    }
}

class ConstraintValidatorRegistration<T : Constraint<T>>(
    override val id: ConstraintId<T>,
    val validate: (T, AnyElement) -> Boolean
) : Registration<ConstraintValidatorRegistration<T>, T>

fun <T : Constraint<T>> validator(id: ConstraintId<T>, validate: (T, AnyElement) -> Boolean) =
    ConstraintValidatorRegistration(id, validate)

val validators = ConstraintValidatorMediator(
    listOf(
        ConstraintValidatorRegistration(ByteLengthConstraint.ID) { constraint, data -> data.bytesValue.size() in constraint.range },
        ConstraintValidatorRegistration(ContainsConstraint.ID) { constraint, data -> data.containerValues.containsAll(constraint.values) }
    )
)

// Example of how the ConstraintMediator can be used to validate data against an AST type.
fun validateType(type: Type.Definition, data: IonElement, mediator: ConstraintValidatorMediator): Boolean {
    // Note that for simplicity, this example uses a boolean return instead of collecting violations.
    return type.constraints.all { mediator.validateConstraint(it, data.asAnyElement()) }
}
