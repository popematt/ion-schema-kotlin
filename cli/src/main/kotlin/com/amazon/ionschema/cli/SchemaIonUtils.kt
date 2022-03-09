package com.amazon.ionschema.cli

import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionelement.api.ionString
import com.amazon.ionelement.api.ionStructOf
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionschema.Type

fun findTypeReferences(ionElement: IonElement): List<AnyElement> {
    ionElement as AnyElement
    return when {
        ionElement.isInlineImport() -> listOf(ionElement)
        ionElement is SymbolElement -> listOf(ionElement)
        ionElement is StructElement -> {
            ionElement.fields.flatMap {
                when (it.name) {
                    "type",
                    "not",
                    "element" -> findTypeReferences(it.value)
                    "one_of",
                    "any_of",
                    "all_of",
                    "ordered_elements",
                    "fields" -> it.value.containerValues.flatMap { findTypeReferences(it) }
                    else -> emptyList()
                }
            }
        }
        else -> emptyList()
    }
}

infix fun StructElement.includesTypeImport(other: StructElement): Boolean {
    return this["id"].textValue == other["id"].textValue
        && (this.getOptional("type") ?: other["type"]).textValue == other["type"].textValue
        && this.getOptional("as")?.textValue == other.getOptional("as")?.textValue
}

infix fun StructElement.isEquivalentImportTo(other: StructElement): Boolean {
    return this["id"].textValue == other["id"].textValue
        && this.getOptional("type")?.textValue == other.getOptional("type")?.textValue
        && this.getOptional("as")?.textValue == other.getOptional("as")?.textValue
}

fun IonElement.isInlineImport(): Boolean =
    this is StructElement && this.fields.map { it.name }
        .let { fieldNames -> "id" in fieldNames && "type" in fieldNames }

fun createImportForType(type: Type): StructElement {
    val alias = type.name
    val name = type.isl.toIonElement().asStruct()["name"].textValue
    val importedFromSchemaId = type.schemaId!!
    return if (alias != name) {
        ionStructOf(
            "id" to ionString(importedFromSchemaId),
            "type" to ionSymbol(name),
            "as" to ionSymbol(alias)
        )
    } else {
        ionStructOf(
            "id" to ionString(importedFromSchemaId),
            "type" to ionSymbol(name)
        )
    }
}
