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

package com.amazon.ionschema

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.loadSingleElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TypeTest {
    private val ION = IonSystemBuilder.standard().build()

    private val typeIsl = "type::{name: a, type: string, open_content: hi}"

    private val type = IonSchemaSystemBuilder.standard()
        .build()
        .newSchema()
        .newType(typeIsl)

    @Test
    fun name() = assertEquals("a", type.name)

    @Test
    fun isValid_true() = assertTrue(type.isValid(ION.singleValue("\"hello\"")))

    @Test
    fun isValid_false() = assertFalse(type.isValid(ION.singleValue("1")))

    @Test
    fun validate_success() {
        val violations = type.validate(ION.singleValue("\"hello\""))
        assertNotNull(violations)
        assertTrue(violations.isValid())
        assertFalse(violations.iterator().hasNext())
        assertThrows<NoSuchElementException> {
            violations.iterator().next()
        }
    }

    @Test
    fun validate_violations() {
        val violations = type.validate(ION.singleValue("1"))
        assertNotNull(violations)
        assertFalse(violations.isValid())
        assertTrue(violations.iterator().hasNext())

        val iter = violations.iterator()
        val violation = iter.next()
        println(violations)
        assertEquals("type_mismatch", violation.code)
        assertEquals("type", violation.constraint?.name)
        assertEquals(loadSingleElement("string"), violation.constraint?.value)
    }

    @Test
    fun isl_type() {
        assertEquals(loadSingleElement(typeIsl), type.isl)
    }

    @Test
    fun isl_newType() {
        val schema = IonSchemaSystemBuilder.standard().build().newSchema()
        val isl = "type::{name: b, type: struct, open_content: hi}"
        val newType = schema.newType(isl)
        assertEquals(loadSingleElement(isl), newType.isl)
    }

    @Test
    fun isl_plusType() {
        val schema = IonSchemaSystemBuilder.standard().build().newSchema()
        val isl = "type::{name: b, type: struct, open_content: hi}"
        val newSchema = schema.plusType(schema.newType(isl))
        assertEquals(loadSingleElement(isl), newSchema.getType("b")!!.isl)
    }
}
