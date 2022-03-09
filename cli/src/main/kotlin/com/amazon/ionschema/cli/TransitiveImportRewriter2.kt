package com.amazon.ionschema.cli

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.IonElementLoaderOptions
import com.amazon.ionelement.api.IonLocation
import com.amazon.ionelement.api.IonTextLocation
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.TextElement
import com.amazon.ionelement.api.ionString
import com.amazon.ionelement.api.ionStructOf
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionelement.api.loadAllElements
import com.amazon.ionelement.api.location
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.Exception

/**
 * TODO:
 *  - documentation
 *  - refactor into readable chunks
 *  - proper test cases
 */
object TransitiveImportRewriter2 {

    // Logic:
    // Pass 1:
    //    For each authority
    //       Walk file system from a base path
    //           Only if it's an aggregating export schema, rewrite it in temp directory, otherwise copy to temp directory
    // Pass 2:
    //    For each authority
    //       Walk file system from a base path
    //           Rewrite schema in temp directory, otherwise copy to temp directory
    // Read all schemas in the new base path to make sure that they all load correctly

    internal const val printStatusUpdates = true

    private val ION: IonSystem = IonSystemBuilder.standard().build()

    fun fixTransitiveImports(basePath: String) {
        val newBasePath = basePath + "_rewrite_" + Instant.now().truncatedTo(ChronoUnit.SECONDS)

        println("Pass 0: Validate original schemas")
        val prevalidation = validateAll(basePath, withTransitiveImports = true)
        if (!prevalidation) throw Exception("One or more schemas is invalid.")

        println("Pass 1: Rewrite aggregating schemas")
        rewriteSchemasForFileSystemAuthority(basePath, newBasePath + "_pass1") {
            iss, it -> iss.rewriteAggregatingSchema(it)
        }

        println("Pass 2: Rewrite all other schemas")
        rewriteSchemasForFileSystemAuthority(newBasePath  + "_pass1", newBasePath + "_pass2") {
                iss, it -> iss.rewriteStandardSchema(it)
        }


        println("Pass 3: Validate new schemas")
        val success = validateAll(newBasePath  + "_pass2")

        if (success) {
            File(newBasePath + "_pass2").copyRecursively(File(newBasePath))
            File(newBasePath + "_pass1").deleteRecursively()
            File(newBasePath + "_pass2").deleteRecursively()
        } else {
            throw Exception("Rewriter output is invalid.")
        }
    }

    private fun rewriteSchemasForFileSystemAuthority(basePath: String, newBasePath: String, transform: (IonSchemaSystem, SchemaPatcher) -> Unit) {
        val iss = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports(true)
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        val success = walkFileSystemAuthority(basePath).map { (_, file) ->
            RewriteHelpers2.rewriteSchema(file, basePath, newBasePath) { transform(iss, it) }
        }.all { it }

        if (!success) throw Exception("One of more schemas failed.")
    }

    private fun walkFileSystemAuthority(basePath: String) = File(basePath).walk()
        .filter { it.isFile }
        .filter { it.path.endsWith(".isl") }
        .map { file -> file.path.substring(basePath.length + 1) to file }


    private fun validateAll(basePath: String, withTransitiveImports: Boolean = false): Boolean {
        val iss = IonSchemaSystemBuilder.standard()
            .withIonSystem(ION)
            .allowTransitiveImports(withTransitiveImports)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        var success = true
        walkFileSystemAuthority(basePath).forEach { (schemaId, _) ->
            if (printStatusUpdates) print("Validating $schemaId ...")
            runCatching {
                iss.loadSchema(schemaId)
                if (printStatusUpdates) println("PASS")
            }.onFailure {
                success = false
                if (printStatusUpdates) println("FAIL")
                else println("Validating $schemaId ...FAIL")
                println(it)
            }
        }
        return success
    }

    internal const val ION_SCHEMA_1_0 = "\$ion_schema_1_0"

    internal fun IonSchemaSystem.rewriteAggregatingSchema(schemaPatcher: SchemaPatcher) {
        val schema = this.newSchema(schemaPatcher.original)
        if (!isExportOnlySchema(schema)) return

        val exports = schema.getTypes().asSequence()
            .filter { builtInTypesSchema.getType(it.name) == null && it.schemaId != null }
            .map {
                val alias = it.name
                val name = it.isl.toIonElement().asStruct()["name"].textValue
                val importedFromSchemaId = it.schemaId!!
                ionStructOf(
                    // alias and name could be the same value if there was no import alias
                    // in which case it is being re-exported as the same name.
                    "name" to ionSymbol(alias),
                    "type" to ionStructOf(
                        "id" to ionString(importedFromSchemaId),
                        "type" to ionSymbol(name)
                    ),
                    annotations = listOf("type")
                )
            }
            .sortedBy { it["name"].textValue }
            .joinToString("\n")

        schemaPatcher.replaceAll("""
            |// Schema '${schemaPatcher.schemaId}'
            |// 
            |// This schema declares types that are intended for public consumption.
            |//
            |// The purpose of this schema is to decouple consumers of the schema from the implementation details (ie. specific locations)
            |// of each type that it provides, and to indicate to consumers, which types they SHOULD use. Consumers of this type CAN bypass
            |// this schema and import other types directly, but they SHOULD NOT without having a really, really good reason to do so.
            |// 
            |// Consumers of this schema should not bypass this schema unless directed to do so by the owner(s)/author(s) of this schema.
            |// 
            |// The type
            |//     type::{name:foobar,type:{id:"bar.isl",type:foo}}
            |// is analogous to ecmascript
            |//     export { foo as foobar } from 'bar.isl'
            |// 
            |$ION_SCHEMA_1_0
            |
            |$exports
            |
            """.trimMargin())
    }

    private fun IonSchemaSystem.rewriteStandardSchema(schemaPatcher: SchemaPatcher) {

        val schema = this.newSchema(schemaPatcher.original)

        if (isExportOnlySchema(schema)) {
            return
        }

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        val newHeaderImports = calculateNewHeaderImports(schema)

        val oldInlineImportsToNewInlineImportsMap = calculateNewInlineImportMapping(schema)

        // If no header and no changes to inline imports, do nothing?
        if (headerImports.isEmpty() && oldInlineImportsToNewInlineImportsMap.isEmpty()) {
            return
        }

        val schemaIslString = schemaPatcher.original

        val schemaIonElements = loadAllElements(schemaIslString, IonElementLoaderOptions(includeLocationMeta = true))


        val newlineLocations = schemaIslString.mapIndexedNotNull { i, c -> if (c == '\n') i else null }
        val lineStartLocations = (listOf(0) + newlineLocations.map { it + 1 })
        fun IonLocation?.toIndex(): Int = with(this as IonTextLocation) {
            lineStartLocations[line.toInt() - 1] + charOffset.toInt() - 1
        }

        val newSchemaString = schemaPatcher

        if (newHeaderImports != headerImports) {
            schemaIonElements.singleOrNull { "schema_header" in it.annotations }
                ?.let {
                    val imports = it.asStruct().getOptional("imports")?.asList() ?: return@let
                    if (imports.values.isEmpty()) {
                        return@let
                    } else if (newHeaderImports.isEmpty()) {
                        val importListStartLocation = schemaIslString.indexOf('[', startIndex = imports.metas.location.toIndex())
                        val importListEndLocation = schemaIslString.indexOf(']', startIndex = imports.values.last().metas.location.toIndex())
                        newSchemaString.patch(importListStartLocation, importListEndLocation, "[]")
                    } else {

                        // Get key locations
                        val importListContentStartLocation = schemaIslString.indexOf('[', startIndex = imports.metas.location.toIndex()) + 1
                        val firstImportStartLocation = schemaIslString.indexOf('{', startIndex = imports.values.first().metas.location.toIndex())
                        val lastImportEndLocation = schemaIslString.indexOf('}', startIndex = imports.values.last().metas.location.toIndex())

                        val importDelimitingWhitespace = schemaIslString.substring(importListContentStartLocation until firstImportStartLocation)

                        val replacementText = newHeaderImports.joinToString(separator = ",$importDelimitingWhitespace")
                        newSchemaString.patch(firstImportStartLocation, lastImportEndLocation, replacementText)
                    }
                }
        }

        val inlineImportPatchingVisitor = visitor@{ it: AnyElement ->
            if (it is StructElement && oldInlineImportsToNewInlineImportsMap.containsKey(it) && oldInlineImportsToNewInlineImportsMap[it] != it) {
                val start = schemaIslString.indexOf('{', startIndex = it.metas.location.toIndex())
                val end = schemaIslString.indexOf("}", startIndex = start)
                val replacementText = oldInlineImportsToNewInlineImportsMap[it].toString()
                newSchemaString.patch(start, end, replacementText)
            }
        }
        schemaIonElements.forEach { it.recursivelyVisit(PreOrder, inlineImportPatchingVisitor) }
    }

    private fun IonSchemaSystem.calculateNewHeaderImports(schema: Schema): List<StructElement> {

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        if (headerImports.isEmpty()) {
            return emptyList()
        }

        // find all type references in the schema
        val actualImportedTypes = schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findTypeReferences(it.toIonElement()) }
            .asSequence()
            .filterIsInstance<TextElement>()
            .distinctBy { it.textValue }
            .filter { schema.getDeclaredType(it.textValue) == null }
            .filter { builtInTypesSchema.getDeclaredType(it.textValue) == null }
            .map { schema.getType(it.textValue) }
            .map { createImportForType(it!!) }
            .toList()

        return reconcileHeaderImports(headerImports, actualImportedTypes)
    }

    private fun IonSchemaSystem.calculateNewInlineImportMapping(schema: Schema): Map<StructElement, StructElement> {
        return schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findTypeReferences(it.toIonElement()) }
            .filterIsInstance<StructElement>() // Inline imports are structs, all other references are symbols
            .associateWith { createImportForType(loadSchema(it["id"].textValue).getType(it["type"].textValue)!!) }
            .filter { (k, v) -> k isEquivalentImportTo v } // remove any identity transforms
    }

    /**
     * Has at least one import, and has no declared types
     */
    private fun isExportOnlySchema(schema: Schema): Boolean {
        return schema.getDeclaredTypes().asSequence().toList().isEmpty()
            && schema.getImports().hasNext()
    }

    private fun reconcileHeaderImports(headerImports: List<StructElement>, actualImportedTypes: List<StructElement>): List<StructElement> {
        val newImports = mutableListOf<StructElement>()
        headerImports.forEach { import ->
            if (actualImportedTypes.any { import includesTypeImport it } && import !in newImports) {
                newImports.add(import)
            }
        }
        actualImportedTypes.forEach { typeImport ->
            if (!newImports.any { it includesTypeImport typeImport } && typeImport !in newImports) {
                newImports.add(typeImport)
            }
        }
        return newImports.toList()
    }
}

object RewriteHelpers2 {

    internal inline fun rewriteSchema(file: File, basePath: String, newBasePath: String, transform: (schema: SchemaPatcher) -> Unit): Boolean {
        val schemaId = file.path.substring(basePath.length + 1)

        val schemaIonText = file.readText(Charsets.UTF_8)
        val schemaPatcher = SchemaPatcher(schemaId, schemaIonText)

        if (TransitiveImportRewriter.printStatusUpdates) print("Processing $schemaId ...")
        try {
            schemaPatcher.apply(transform)
            val newFile = File("$newBasePath/$schemaId")
            newFile.parentFile.mkdirs()
            if (schemaPatcher.hasChanges()) {
                newFile.createNewFile()
                newFile.appendText(schemaPatcher.toString())
                if (TransitiveImportRewriter.printStatusUpdates) println("CHANGE COMPLETE")
            } else {
                file.copyTo(newFile)
                if (TransitiveImportRewriter.printStatusUpdates) println("NO CHANGE")
            }
            return true
        } catch (t: Throwable) {
            if (TransitiveImportRewriter.printStatusUpdates) println("FAILED")
            else println("$schemaId FAILED")
            t.printStackTrace()
            return false
        }
    }
}
