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

import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionelement.api.TextElement
import com.amazon.ionschema.InvalidSchemaException
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Violations

/**
 * Provides a factory method that translates an ISL type reference into a function
 * that returns a Type instance.
 *
 * Types that can't be resolved yet are instantiated as [TypeReferenceDeferred] objects
 * that are resolved by [SchemaImpl.resolveDeferredTypeReferences] prior to asserting
 * that the schema is valid.
 */
internal class TypeReference private constructor() {
    companion object {
        fun create(ion: IonElement, schema: Schema, isField: Boolean = false): () -> TypeInternal {
            if (ion.isNull) {
                throw InvalidSchemaException("Unable to resolve type reference '$ion'")
            }

            return when (ion) {
                is StructElement -> handleStruct(ion, schema, isField)
                is SymbolElement -> handleSymbol(ion, schema)
                else -> throw InvalidSchemaException("Unable to resolve type reference '$ion'")
            }
        }

        private fun handleStruct(ion: StructElement, schema: Schema, isField: Boolean): () -> TypeInternal {
            val id = ion.getOptional("id") as? TextElement
            val type = when {
                id != null -> {
                    // import
                    val newSchema = schema.getSchemaSystem().loadSchema(id.textValue)
                    val typeName = ion.get("type") as SymbolElement
                    newSchema.getType(typeName.textValue) as? TypeInternal
                }
                isField -> TypeImpl(ion, schema)
                ion.size == 1 && ion.containsField("type") -> {
                    // elide inline types defined as "{ type: X }" to TypeImpl;
                    // this avoids creating a nested, redundant validation structure
                    TypeImpl(ion, schema)
                }
                else -> TypeInline(ion, schema)
            }

            type ?: throw InvalidSchemaException("Unable to resolve type reference '$ion'")

            val theType = handleNullable(ion, schema, type)
            return { theType }
        }

        private fun handleSymbol(ion: SymbolElement, schema: Schema): () -> TypeInternal {
            val t = schema.getType(ion.textValue)
            return if (t != null) {
                val type = t as? TypeBuiltin ?: TypeNamed(ion, t as TypeInternal)
                val theType = handleNullable(ion, schema, type);
                { theType }
            } else {
                // type can't be resolved yet;  ask the schema to try again later
                val deferredType = TypeReferenceDeferred(ion, schema)
                (schema as SchemaImpl).addDeferredType(deferredType);
                { deferredType.resolve() }
            }
        }

        private fun handleNullable(ion: IonElement, schema: Schema, type: TypeInternal): TypeInternal =
            if ("nullable" in ion.annotations) {
                TypeNullable(ion, type, schema)
            } else {
                type
            }
    }
}

/**
 * Represents a type reference that can't be resolved yet.
 */
internal class TypeReferenceDeferred(
    nameSymbol: SymbolElement,
    private val schema: Schema
) : TypeInternal {

    private var type: TypeInternal? = null
    override val name: String = nameSymbol.textValue
    override val schemaId: String? = (schema as? SchemaImpl)?.schemaId
    override val isl = nameSymbol

    fun attemptToResolve(): Boolean {
        type = schema.getType(name) as? TypeInternal
        return type != null
    }

    fun resolve(): TypeInternal = type!!

    override fun getBaseType(): TypeBuiltin = throw UnsupportedOperationException()

    override fun isValidForBaseType(value: IonElement): Boolean = throw UnsupportedOperationException()

    override fun validate(value: IonElement, issues: Violations) = throw UnsupportedOperationException()
}
