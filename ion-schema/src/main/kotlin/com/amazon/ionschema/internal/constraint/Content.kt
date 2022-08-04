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
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionschema.InvalidSchemaException
import com.amazon.ionschema.Violations

/**
 * Implements the content constraint.
 *
 * This implementation exists solely to verify that the constraint
 * definition is valid.  Validation logic for this constraint is
 * performed by the [Fields] constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#content
 */
internal class Content(ion: StructField) : ConstraintBase(ion) {

    companion object {
        private val THE_ONLY_ALLOWED_VALUE = ionSymbol("closed")
    }

    init {
        if (ion.value != THE_ONLY_ALLOWED_VALUE) {
            throw InvalidSchemaException("Invalid content constraint: $ion")
        }
    }

    override fun validate(value: IonElement, issues: Violations) {
        // no-op, validation logic for this constraint is performed by the fields constraint
    }
}
