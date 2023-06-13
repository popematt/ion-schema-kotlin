package com.amazon.ionschema.cli.generator

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.model.ExperimentalIonSchemaModel
import com.amazon.ionschema.reader.IonSchemaReaderV2_0
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@OptIn(ExperimentalIonSchemaModel::class)
class GeneratorTest {
    val ION = IonSystemBuilder.standard().build()

    @Test
    fun testGenerator() {

        val reader = IonSchemaReaderV2_0()
        val converter = Converter(Converter.Options(
            schemaIdToModuleNamespaceStrategy = {
                (if (it.endsWith(".isl")) it.dropLast(4) else it)
                    .split("/", "\\")
                    .map { it.replace(Regex("[^\\w\\d_]"), "") }
            }
        ))


        val schemaDocument = reader.readSchemaOrThrow(ION.loader.load("""
            ${'$'}ion_schema_2_0
            type::{
              name: foo,
              one_of: [
                { ${'$'}codegen_name: bar, type: ${'$'}symbol },
                { type: ${'$'}decimal },
                string,
                int,
              ]
            }
            type::{
              name: Customer,
              type: struct,
              fields: closed::{
                firstName: { type: string, occurs: required },
                middleName: string,
                lastName: { type: string, occurs: required },
              },
            }
        """.trimIndent())).copy(id = "popematt/types.isl")

        val typeDomain = converter.toTypeDomain(listOf(schemaDocument))

        val kotlinGenerator = KotlinGenerator(
            typeDomain = typeDomain,
            options = KotlinGenerator.Options(
                kotlinVersion = KotlinVersion(1, 6, 0),
                outputDir = Path("./build/generated/"),
                rootPackage = "com.amazon.ionschema.codegen",
            )
        )

        val result = kotlinGenerator.generateTypeDomain()

        result.forEach { println(it) }
    }

}