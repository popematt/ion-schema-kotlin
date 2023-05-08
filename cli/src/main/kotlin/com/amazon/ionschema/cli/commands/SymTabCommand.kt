package com.amazon.ionschema.cli.commands

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.amazon.ionelement.api.*
import com.amazon.ionschema.cli.PreOrder
import com.amazon.ionschema.cli.recursivelyVisit

class SymTabCommand : UsesType(
    help = "Generates an Ion Shared Symbol Table based on a schema.",
    epilog = """
        ```
        Example usage:
            Create a shared symbol table for a new type:
                ion-schema-cli sym-tab --type '{ fields: { foo: int, bar: string } }'
            Create a shared symbol table for a type from a schema:
                ion-schema-cli sym-tab -a file-system --base-dir ~/my_schemas/ --schema 'Customers.isl' --type 'online_customer'
        ```
    """.trimIndent()
) {

    override val ion = IonSystemBuilder.standard().build()


    override fun run() {

        val symbols = symbolsForTypeDef(type.isl.toIonElement().asStruct())

        val symbolTable = ionStructOf(
            "name" to ionString(type.name),
            "version" to ionInt(1),
            "imports" to emptyIonList(),
            "symbols" to ionListOf(symbols.map { ionString(it) }),
            annotations = listOf("\$ion_shared_symbol_table")
        )
        echo(StringBuilder().also { buf ->
            IonTextWriterBuilder.pretty().build(buf).use { symbolTable.writeTo(it) }
        }.toString())
    }

    private fun symbolsForTypeDef(typeDef: StructElement): List<String> {
        return typeDef.fields.mapNotNull { constraint ->
            when (constraint.name) {
                "valid_values" -> {
                    val symbolText = mutableSetOf<String>()
                    // TODO: Annotations on nested values
                    constraint.value.asList().values.forEach {
                        when (it.type) {
                            ElementType.SYMBOL -> it.symbolValueOrNull?.let { symbolText.add(it) }
                            ElementType.STRUCT,
                            ElementType.LIST,
                            ElementType.SEXP -> {
                                it.asContainer().values.forEach {
                                    it.recursivelyVisit(PreOrder) {
                                        if (it.type == ElementType.SYMBOL)
                                            it.symbolValueOrNull?.let { s -> symbolText.add(s) }
                                        symbolText.addAll(it.annotations)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    symbolText
                }
                "contains" -> {
                    val symbolText = mutableSetOf<String>()
                    constraint.value.asList().values.mapNotNull { elem ->
                        elem.recursivelyVisit(PreOrder) {
                            if (it.type == ElementType.SYMBOL)
                                it.symbolValueOrNull?.let { s -> symbolText.add(s) }
                            symbolText.addAll(it.annotations)
                        }
                    }
                    symbolText
                }
                "fields" -> {
                    constraint.value.asStruct().fields.let { fields ->
                        fields.map { it.name } + fields.mapNotNull {
                            it.value.takeIf { it.type == ElementType.STRUCT }?.asStructOrNull()
                                ?.let(::symbolsForTypeDef)
                        }.flatten()
                    }
                }
                "any_of", "one_of", "all_of", "ordered_elements" -> {
                    constraint.value.asList().values.mapNotNull {
                        if (it.type == ElementType.STRUCT) {
                            symbolsForTypeDef(it.asStruct())
                        } else {
                            null
                        }
                    }.flatten()
                }
                "type", "not", "element" -> {
                    if (constraint.value.type == ElementType.STRUCT) {
                        symbolsForTypeDef(constraint.value.asStruct())
                    } else {
                        null
                    }
                }
                "annotations" -> when (constraint.value.type) {
                    ElementType.LIST -> constraint.value.asList().values.map { it.symbolValue }
                    ElementType.STRUCT -> symbolsForTypeDef(constraint.value.asStruct())
                    else -> null
                }
                else -> null
            }
        }.flatten()
    }
}