package com.amazon.ionschema

import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import org.junit.Test

class TestyMcTestface {

    @Test
    fun testSomething() = testTypeAndInputs(
        type = """
            type::{ valid_values: [null.string, null.null, null.int] }
        """,
        inputs = arrayOf(
            "null.string",
            "null.null",
            "null.int",
            "1"
        )
    )


    //Atomic-violation types
    //
    //Need a better name, but the concept is that this is a type definition that should appear as a leaf node in the violations tree.
    //
    //Eg. for this type
    //
    //type::atomic_violation::{
    //  name: uuid,
    //  one_of: [
    //    { type: string, regex: "[0-9a-f]{8}(-[0-9a-f]{4}){4}[0-9a-f]{8}" },
    //    { type: blob, byte_length: 16 }
    //  ]
    //}
    @Test
    fun testAtomicViolation() = testTypeAndInputs(
        schema = """
            type::atomic_violation::{
                name: uuid,
                type: string, 
                regex: "[0-9a-f]{8}(-[0-9a-f]{4}){4}[0-9a-f]{8}" 
            }
        """,
        type = """
            type::{
              name: foo,
              fields: {
                a: uuid
              }
            }
        """.trimIndent(),
        inputs = arrayOf(
            """ { a: "00001111-2222-3333-4444-555566667777" } """,
            "{ a: 1 }",
            """ {a:"hello world"} """,
            "{ a: null.string }"
        )
    )


    fun testTypeAndInputs(schema: String? = null, type: String, vararg inputs: String) {
        val iss = IonSchemaSystemBuilder.standard().build()
        val ion = iss.ionSystem

        try {
            val schema = if (schema == null) iss.newSchema() else iss.newSchema(schema)
            val type = ion.singleValue(type).let {
                when (it) {
                    is IonSymbol -> schema.getType(it.stringValue())!!
                    is IonStruct -> schema.newType(it)
                    else -> TODO()
                }
            }

            inputs.forEach {
                try {
                    val violations = type.validate(ion.singleValue(it)).toString()
                    println("$it --> $violations")
                } catch (e: Exception) {
                    println("$it --> $e")
                }
            }
        } catch (e: Exception) {
            println("$type --> $e")
        }
    }

}
