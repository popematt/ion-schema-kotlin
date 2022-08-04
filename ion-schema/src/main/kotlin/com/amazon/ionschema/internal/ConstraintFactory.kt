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

import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionschema.Schema

/**
 * Factory for [Constraint] objects.
 */
internal interface ConstraintFactory {
    /**
     * If [name] is a recognized constraint name, returns `true`, otherwise `false`.
     */
    fun isConstraint(name: String): Boolean

    /**
     * Instantiates a new [Constraint] as defined by [ion].
     * @param[ion] IonElement identifying the constraint to construct as well as its configuration
     * @param[schema] passed to constraints that require a schema object
     */
    fun constraintFor(field: StructField, container: StructElement, schema: Schema): Constraint
}
