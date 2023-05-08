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
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionschema.Authority
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import com.amazon.ionschema.cli.util.PatchSet
import com.amazon.ionschema.cli.util.rewriteFile
import com.amazon.ionschema.cli.util.validateAll
import com.amazon.ionschema.cli.util.walkFileSystemAuthority
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
class TransitiveImportRewriter3(
    private val importStrategy: ImportStrategy,
    private val ION: IonSystem = IonSystemBuilder.standard().build(),
    private val echo: (String) -> Unit = {},
) {

    enum class ImportStrategy {
        /**
         * While fixing imports, rewrite all imports as `{ id: <SCHEMA_ID>, type: <TYPE_NAME> } `.
         * You might find this style annoying, and it will possibly generate larger diff.
         * However, it is guaranteed to never have name conflicts unintentionally introduced by a dependency in the future.
         */
        RewriteAllAsTypeImports,
        /**
         * While fixing imports, keeps all existing "wildcard" imports (i.e. `{ id: <SCHEMA_ID> }`), but all new imports
         * it adds will be type imports (i.e. `{ id: <SCHEMA_ID>, type: <TYPE_NAME> }`).
         * This strategy will introduce smaller changes, and will not introduce any naming conflicts.
         * However, the presence of wildcard imports means that unintentional name conflicts could happen in the future.
         */
        KeepExistingWildcardImports,
    }

    // Logic:
    // Pass 0:
    //     Ensure all schemas load correctly
    // Pass 1:
    //     For each authority
    //        Walk file system from a base path
    //            Only if it's an aggregating export schema, rewrite it in temp directory, otherwise copy to temp directory
    // Pass 2:
    //     For each authority
    //         Walk file system from a base path
    //             Rewrite schema in temp directory, otherwise copy to temp directory
    // Pass 3:
    //    Read all schemas in the new base path to make sure that they all load correctly

    fun fixTransitiveImports(basePath: String, authorities: List<Authority>,) {
        val newBasePath = basePath + "_rewrite_" + Instant.now().truncatedTo(ChronoUnit.SECONDS)

        echo("Pass 0: Validate original schemas")
        val prefixFailures = validateAll(basePath, withTransitiveImports = true)
        if (prefixFailures.isNotEmpty()) {
            val failureList = prefixFailures.map { (k, v) -> "\n  - $k : ${v.message}" }.joinToString("")
            throw Exception("One or more schemas is invalid: $failureList")
        }

        echo("Pass 1: Rewrite aggregating schemas")
        rewriteSchemasForFileSystemAuthority(basePath, newBasePath + "_pass1", this::rewriteAggregatingSchema)

        echo("Pass 2: Rewrite all other schemas")
        rewriteSchemasForFileSystemAuthority(newBasePath + "_pass1", newBasePath + "_pass2", this::rewriteStandardSchema)

        echo("Pass 3: Validate new schemas")
        val failures = validateAll(newBasePath + "_pass2")

        if (failures.isEmpty()) {
            File(newBasePath + "_pass2").copyRecursively(File(newBasePath))
            File(newBasePath + "_pass1").deleteRecursively()
            File(newBasePath + "_pass2").deleteRecursively()
        } else {
            val failureList = prefixFailures.map { (k, v) -> "\n  - $k : ${v.message}" }.joinToString("")
            throw Exception("Rewriter output is invalid: $failureList")
        }
    }

    private fun rewriteSchemasForFileSystemAuthority(basePath: String, newBasePath: String, block: (schemaId: String, schemaFile: File, iss: IonSchemaSystem) -> PatchSet) {
        val iss = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports(true)
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        val errors = walkFileSystemAuthority(basePath).mapNotNull { (schemaId, file) ->
            val patches = block(schemaId, file, iss)
            runCatching { rewriteFile(file, basePath, newBasePath, patches) }
                .exceptionOrNull()
                ?.let { schemaId to it }
        }.toList()

        if (errors.isNotEmpty()){
            val failureList = errors.joinToString("") { (k, v) -> "\n  - $k : ${v.message}" }
            throw Exception("One or more schemas failed: $failureList")
        }
    }

    private fun rewriteAggregatingSchema(schemaId: String, schemaFile: File, iss: IonSchemaSystem): PatchSet {
        val patchSet = PatchSet()
        val schemaIslString = schemaFile.readText()

        val schema = iss.newSchema(schemaIslString)
        if (!isExportOnlySchema(schema)) return patchSet

        val exports = schema.getTypes().asSequence()
            .filter { !isBuiltInTypeName(it.name) && it.schemaId != null }
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

        patchSet.replaceAll(
            """
            |${'$'}ion_schema_1_0
            |
            |// Schema '${schemaId}'
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
            | 
            |
            |$exports
            |
            """.trimMargin()
        )
        return patchSet
    }

    private fun rewriteStandardSchema(schemaId: String, schemaFile: File, iss: IonSchemaSystem): PatchSet {

        val patchSet = PatchSet()

        val schema = iss.loadSchema(schemaId)

        if (isExportOnlySchema(schema)) return patchSet

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        val newHeaderImports = calculateNewHeaderImports(schema)

        val oldInlineImportsToNewInlineImportsMap = calculateNewInlineImportMapping(schemaId, iss)

        // If no header and no changes to inline imports, do nothing?
        if (headerImports.isEmpty() && oldInlineImportsToNewInlineImportsMap.isEmpty()) {
            return patchSet
        }

        val schemaIslString = schemaFile.readText()

        val schemaIonElements = loadAllElements(schemaIslString, IonElementLoaderOptions(includeLocationMeta = true))

        val newlineLocations = schemaIslString.mapIndexedNotNull { i, c -> if (c == '\n') i else null }
        val lineStartLocations = (listOf(0) + newlineLocations.map { it + 1 })
        fun IonLocation?.toIndex(): Int = with(this as IonTextLocation) {
            lineStartLocations[line.toInt() - 1] + charOffset.toInt() - 1
        }

        if (newHeaderImports != headerImports) {
            schemaIonElements.singleOrNull { "schema_header" in it.annotations }
                ?.let {
                    val imports = it.asStruct().getOptional("imports")?.asList() ?: return@let
                    if (imports.values.isEmpty()) {
                        return@let
                    } else if (newHeaderImports.isEmpty()) {
                        val importListStartLocation =
                            schemaIslString.indexOf('[', startIndex = imports.metas.location.toIndex())

                        val importListEndLocation =
                            schemaIslString.indexOf(']', startIndex = imports.values.last().metas.location.toIndex())

                        patchSet.patch(importListStartLocation, importListEndLocation, "[]")
                    } else {

                        // Get key locations
                        val importListContentStartLocation =
                            schemaIslString.indexOf('[', startIndex = imports.metas.location.toIndex()) + 1

                        val firstImportStartLocation =
                            schemaIslString.indexOf('{', startIndex = imports.values.first().metas.location.toIndex())

                        val lastImportEndLocation =
                            schemaIslString.indexOf('}', startIndex = imports.values.last().metas.location.toIndex())

                        val importDelimitingWhitespace =
                            schemaIslString.substring(importListContentStartLocation until firstImportStartLocation)

                        val replacementText = newHeaderImports.joinToString(separator = ",$importDelimitingWhitespace")
                        patchSet.patch(firstImportStartLocation, lastImportEndLocation, replacementText)
                    }
                }
        }

        val inlineImportPatchingVisitor = visitor@{ it: AnyElement ->
            if (it is StructElement && oldInlineImportsToNewInlineImportsMap.containsKey(it) && oldInlineImportsToNewInlineImportsMap[it] != it) {
                val start = schemaIslString.indexOf('{', startIndex = it.metas.location.toIndex())
                val end = schemaIslString.indexOf("}", startIndex = start)
                val replacementText = oldInlineImportsToNewInlineImportsMap[it].toString()
                patchSet.patch(start, end, replacementText)
            }
        }
        schemaIonElements.forEach { it.recursivelyVisit(PreOrder, inlineImportPatchingVisitor) }

        return patchSet
    }


    /**
     * Determines the new header imports for a schema.
     */
    private fun calculateNewHeaderImports(schema: Schema): List<StructElement> {
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
            .filter { !isBuiltInTypeName(it.textValue) }
            .map { schema.getType(it.textValue) }
            .map { createImportForType(it!!) }
            .toList()

        return reconcileHeaderImports(headerImports, actualImportedTypes)
    }

    /**
     * Returns a map of inline imports that need to be replaced, mapped to their replacement inline imports
     */
    private fun calculateNewInlineImportMapping(schemaId: String, iss: IonSchemaSystem): Map<StructElement, StructElement> {
        val schema = iss.loadSchema(schemaId)
        return schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findTypeReferences(it.toIonElement()) }
            .filterIsInstance<StructElement>() // Inline imports are structs, all other references are symbols
            .associateWith { createImportForType(iss.loadSchema(it["id"].textValue).getType(it["type"].textValue)!!) }
            .filter { (old, new) -> old isEquivalentImportTo new } // remove any identity transforms
    }

    /**
     * Checks if a schema has at least one import, and has no declared types. I.e. its only purpose is to re-export other types.
     */
    private fun isExportOnlySchema(schema: Schema): Boolean {
        return schema.getDeclaredTypes().asSequence().toList().isEmpty() &&
            schema.getImports().hasNext()
    }

    /**
     * Computes the minimal set of header imports for the actual imported types, given the Import reconciliation strategy.
     */
    private fun reconcileHeaderImports(headerImports: List<StructElement>, actualImportedTypes: List<StructElement>): List<StructElement> {
        val newImports = mutableListOf<StructElement>()
        when (importStrategy) {
            ImportStrategy.RewriteAllAsTypeImports -> newImports.addAll(actualImportedTypes)
            ImportStrategy.KeepExistingWildcardImports -> {
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
            }
        }
        return newImports.toList()
    }
}
