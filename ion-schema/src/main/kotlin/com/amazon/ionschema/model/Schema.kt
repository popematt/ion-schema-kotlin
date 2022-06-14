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

import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionschema.model.codegen.Builder

/**
 * Represents a complete Ion Schema document. Note—the model does not include a particular ISL version because the AST
 * is intended to be a version-agnostic representation of ISL.
 */
@Builder
data class Schema(
    val id: String? = null,
    val header: Header,
    val content: List<SchemaContent>,
    val footer: Footer,
) {
    companion object
    val types get() = content.filterIsInstance<SchemaContent.NamedType>()

    sealed class SchemaContent {
        /**
         * Represents a top-level type declaration in a schema document.
         */
        data class NamedType(val name: String, val definition: Type.Definition) : SchemaContent() {
            // @GeneratedBuilder
            @JvmOverloads constructor(
                name: String,
                constraints: Collection<Constraint<*>>,
                userContent: List<StructField> = emptyList()
            ) : this(
                name, Type.Definition(constraints, userContent)
            )
        }

        /**
         * Represents top-level user content (i.e. "open content") in a schema document.
         */
        data class UserContent(private val element: IonElement) : SchemaContent(), IonElement by element
    }
}
