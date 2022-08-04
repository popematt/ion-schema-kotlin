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
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.TypeReference

/**
 * Implements the type constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#type
 */
internal class Type(
    ion: StructField,
    schema: Schema
) : ConstraintBase(ion) {

    private val typeReference = TypeReference.create(ion.value, schema)

    override fun validate(value: IonElement, issues: Violations) = typeReference().validate(value, issues)
}
