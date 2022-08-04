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
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionelement.api.loadAllElements
import com.amazon.ionschema.Import
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Type
import com.amazon.ionschema.internal.util.DatagramElement
import com.amazon.ionschema.internal.util.ionDatagramOf

/**
 * Provides instances of [Type] for all of the Core Types and Ion Types
 * defined by the Ion Schema Specification.
 */
internal class SchemaCore(
    private val schemaSystem: IonSchemaSystem
) : Schema {

    private val typeMap: Map<String, Type>

    override val isl: DatagramElement

    init {
        typeMap = loadAllElements(CORE_TYPES + ION_TYPES)
            .asSequence()
            .map { (it as StructElement).fields.first().value as SymbolElement }
            .associateBy({ it.textValue }, { newType(it) })
            .toMutableMap()

        loadAllElements(ADDITIONAL_TYPE_DEFS)
            .asSequence()
            .map { (it as StructElement).fields.first() }
            .forEach {
                typeMap.put(it.name, TypeBuiltinImpl(it.value as StructElement, this))
            }

        isl = ionDatagramOf(emptyList())
    }

    private fun newType(name: SymbolElement): Type =
        if (name.textValue.startsWith("\$")) {
            TypeIon(name)
        } else {
            TypeCore(name)
        }

    override fun getImport(id: String) = null

    override fun getImports() = emptyList<Import>().iterator()

    override fun getType(name: String): Type? = typeMap[name]

    override fun getTypes() = typeMap.values.iterator()

    override fun getDeclaredType(name: String): Type? = getType(name)

    override fun getDeclaredTypes(): Iterator<Type> = getTypes()

    override fun getSchemaSystem() = schemaSystem

    override fun newType(isl: String) = throw UnsupportedOperationException()
    override fun newType(isl: StructElement) = throw UnsupportedOperationException()
    override fun plusType(type: Type) = throw UnsupportedOperationException()
}

private const val CORE_TYPES =
    """
        { type: blob }
        { type: bool }
        { type: clob }
        { type: decimal }
        { type: document }
        { type: float }
        { type: int }
        { type: string }
        { type: symbol }
        { type: timestamp }
        { type: list }
        { type: sexp }
        { type: struct }
    """

private const val ION_TYPES =
    """
        { type: ${'$'}blob }
        { type: ${'$'}bool }
        { type: ${'$'}clob }
        { type: ${'$'}decimal }
        { type: ${'$'}float }
        { type: ${'$'}int }
        { type: ${'$'}null }
        { type: ${'$'}string }
        { type: ${'$'}symbol }
        { type: ${'$'}timestamp }
        { type: ${'$'}list }
        { type: ${'$'}sexp }
        { type: ${'$'}struct }
    """

private const val ADDITIONAL_TYPE_DEFS =
    """
        { lob:    type::{ one_of: [ blob, clob ] } }

        { number: type::{ one_of: [ decimal, float, int ] } }

        { text:   type::{ one_of: [ string, symbol ] } }

        { any:    type::{ one_of: [ blob, bool, clob, decimal, document,
                                    float, int, string, symbol, timestamp,
                                    list, sexp, struct ] } }

        { '${'$'}lob':    type::{ one_of: [ '${'$'}blob', '${'$'}clob' ] } }

        { '${'$'}number': type::{ one_of: [ '${'$'}decimal', '${'$'}float', '${'$'}int' ] } }

        { '${'$'}text':   type::{ one_of: [ '${'$'}string', '${'$'}symbol' ] } }

        { '${'$'}any':    type::{ one_of: [ '${'$'}blob',
                                            '${'$'}bool',
                                            '${'$'}clob',
                                            '${'$'}decimal',
                                            '${'$'}float',
                                            '${'$'}int',
                                            '${'$'}null',
                                            '${'$'}string',
                                            '${'$'}symbol',
                                            '${'$'}timestamp',
                                            '${'$'}list',
                                            '${'$'}sexp',
                                            '${'$'}struct',
                                            document,
                                          ] } }

        { nothing:        type::{ not: ${'$'}any } }
    """
