package com.amazon.ionschema.migrationtool

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.internal.TypeImpl
import com.amazon.ionschema.migratortool.fixTransitiveImports
import com.amazon.ionschema.migratortool.fixTransitiveImports2
import org.junit.Test

class TransitiveImportFixerTest {

    val ION = IonSystemBuilder.standard().build()

    val ISL = IonSchemaSystemBuilder.standard()
        .allowTransitiveImports()
        .withIonSystem(ION)
        .withAuthority(AuthorityFilesystem("ion-schema-tests"))
        .build()

    @Test
    fun printIR() {
        val schema = ISL.loadSchema("schema/import/all_import.isl")

        val type = schema.getType("inline_123") as TypeImpl

        type.constraints
            .map { it.also { println("${it::class}, ${it.name}, $it") } }
            .filterIsInstance<com.amazon.ionschema.internal.constraint.Type>()
            .forEach {
                val typeRef = it.typeReference.invoke()
                println(typeRef)
                println("${typeRef.schemaId} ${typeRef.name}")
                println(typeRef.isl)
            }
    }

    @Test
    fun someTest() {
        ISL.fixTransitiveImports("schema/import/all_import.isl")
    }

    @Test
    fun someTest2() {
        ISL.fixTransitiveImports2("schema/import/all_import.isl")
    }
}
