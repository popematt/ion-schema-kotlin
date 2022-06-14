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

import com.amazon.ionelement.api.StructField
import com.amazon.ionschema.model.codegen.Builder

sealed class Type {

    /**
     * Represents a named reference to another type. If the [typeId] exists in the scope of the enclosing type (i.e. the
     * type was imported in the header, declared elsewhere in the same schema document, or is a built-in type), then
     * [schemaId] can be null. For inline imports, [schemaId] must not be null.
     */
    @Builder
    data class Reference @JvmOverloads constructor(val typeId: String, val schemaId: String? = null) : Type()

    /**
     * Represents a type as defined by a set of constraints, with optional user-provided content (i.e. "open content").
     */
    // Can't generate a builder for this class because resolving the KSP type reference for Collection<Constraint<*>>
    // causes infinite recursion in KotlinPoet/KSP
    //@GeneratedBuilder
    data class Definition @JvmOverloads constructor(
        val constraints: Collection<Constraint<*>>,
        // @Builder.Default("emptyList()")
        val userContent: List<StructField> = emptyList()
    ) : Type()
}

/**
 * Gets all instances of a particular constraint, cast to the correct type for the given [constraintId].
 */
operator fun <T : Constraint<T>> Type.Definition.get(constraintId: ConstraintId<T>): List<T> {
    // Compiler thinks that this is an unchecked cast, but we know it's safe because the type bound of AstConstraint
    // and ConstraintId must be the same, and we've already verified that the ID matches.
    return constraints.filter { it.id == constraintId }.map { it as T }
}
