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

/**
 * Interactions between the Ion Schema System and [Constraint]s are mediated through ConstraintMediators.
 *
 * A ConstraintMediator can hold any kind of data associated to [ConstraintId]s, but it is intended primarily for holding
 * things such as reader, writer, and validator functions where we need preserve some type information that would
 * normally be lost through type-erasure.
 */
interface ConstraintMediator<R : AnyRegistration<*>> {
    /** Checks whether the mediator contains any registration for the given constraint name. */
    operator fun contains(name: String): Boolean
    /** Gets the wild-card-typed registration for the given constraint name. */
    fun getByName(name: String): R
    /** Gets a strongly types registration for the given constraint ID. */
    operator fun <C : Constraint<C>, RC : Registration<out R, C>> get(id: ConstraintId<C>): RC
}

// This interface allows us to escape some recursive type bound problems.
// Instead of R: Registration<R>, we can use R: Registration<AnyRegistration<*>>
interface AnyRegistration<T : Constraint<T>> {
    val id: ConstraintId<T>
}

interface Registration<R : AnyRegistration<*>, C : Constraint<C>> : AnyRegistration<C>

class ConstraintMediatorDelegate<R : Registration<out AnyRegistration<*>, *>>(private val registrations: List<R>) : ConstraintMediator<R> {
    init {
        require(registrations.distinctBy { it.id } == registrations)
    }
    override operator fun contains(name: String): Boolean = registrations.any { it.id.constraintName == name }
    override fun getByName(name: String): R = registrations.single { it.id.constraintName == name }
    override fun <C : Constraint<C>, RC : Registration<out R, C>> get(id: ConstraintId<C>): RC {
        // Compiler thinks that this is an unchecked cast, but we know it's safe because the type bound of Registration
        // and ConstraintId must be the same, and we've already verified that the ID matches.
        return registrations.singleOrNull { it.id == id } as RC? ?: throw IllegalArgumentException("Constraint $id is not registered with mediator.")
    }
}
