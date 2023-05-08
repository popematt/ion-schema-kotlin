package com.amazon.ionschema.cli

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.IonElementConstraintException
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
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import com.amazon.ionschema.cli.TransitiveImportRewriter.ImportStrategy.KeepSchemaImportsWriteTypeImports
import com.amazon.ionschema.cli.TransitiveImportRewriter.ImportStrategy.RewriteAllAsTypeImports
import com.amazon.ionschema.cli.TransitiveImportRewriter.ImportStrategy.WriteAllAsSchemaImports
import com.amazon.ionschema.cli.util.StringPatcher
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
object TransitiveImportRewriter {

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

    enum class ImportStrategy {
        /* Might be annoying for some people. Will possibly generate larger diff. Guaranteed to never have name conflicts in the future. */
        RewriteAllAsTypeImports,
        /* Tries to make minimal changes. Will not introduce name conflicts, but you could have some in future. */
        KeepSchemaImportsWriteTypeImports,
        /* Keeps imports smaller, but may cause new name conflict, leaving your schemas in a bad state. */
        WriteAllAsSchemaImports
    }

    internal const val printStatusUpdates = true
    private val importStrategy = KeepSchemaImportsWriteTypeImports

    private val ION: IonSystem = IonSystemBuilder.standard().build()

    fun fixTransitiveImports(basePath: String) {
        val newBasePath = basePath + "_rewrite_" + Instant.now().truncatedTo(ChronoUnit.SECONDS)

        println("Pass 1: Rewrite aggregating schemas")
        rewriteAggregatingSchemas(basePath, newBasePath + "_pass1")

        println("Pass 2: Rewrite all other schemas")
        rewriteStandardSchemas(newBasePath + "_pass1", newBasePath + "_pass2")

        println("Pass 3: Validate new schemas")
        val success = validateAll(newBasePath + "_pass2")

        if (success) {
            File(newBasePath + "_pass2").copyRecursively(File(newBasePath))
            File(newBasePath + "_pass1").deleteRecursively()
            File(newBasePath + "_pass2").deleteRecursively()
        } else {
            throw Exception("Rewriter output is invalid.")
        }
    }

    private fun walkFileSystemAuthority(basePath: String) = File(basePath).walk()
        .filter { it.isFile }
        .filter { it.path.endsWith(".isl") }
        .map { file -> file.path.substring(basePath.length + 1) to file }

    private fun validateAll(newBasePath: String): Boolean {
        val iss = IonSchemaSystemBuilder.standard()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(newBasePath))
            .build()

        var success = true
        walkFileSystemAuthority(newBasePath).forEach { (schemaId, _) ->
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

    private fun rewriteAggregatingSchemas(basePath: String, newBasePath: String) {
        val iss = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports(true)
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        walkFileSystemAuthority(basePath).forEach { (_, file) ->
            RewriteHelpers.rewriteSchema(file, basePath, newBasePath) { iss.rewriteAggregatingSchema(it) }
        }
    }

    private fun rewriteStandardSchemas(basePath: String, newBasePath: String) {
        val iss = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports(true)
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        walkFileSystemAuthority(basePath).forEach { (_, file) ->
            RewriteHelpers.rewriteSchema(file, basePath, newBasePath) { iss.rewriteStandardSchema(basePath, it) }
        }
    }

    internal fun IonSchemaSystem.rewriteAggregatingSchema(schemaId: String): String? {
        val schema = this.loadSchema(schemaId)

        if (!isExportOnlySchema(schema)) return null

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

        return """
            |// Schema '$schemaId'
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
            |
            |$exports
            |
            """.trimMargin()
    }

    private fun IonSchemaSystem.rewriteStandardSchema(basePath: String, schemaId: String): String? {

        val schema = this.loadSchema(schemaId)

        if (isExportOnlySchema(schema)) {
            return null
        }

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        val newHeaderImports = calculateNewHeaderImports(schema)

        val oldInlineImportsToNewInlineImportsMap = calculateNewInlineImportMapping(schema)

        // If no header and no changes to inline imports, do nothing?
        if (headerImports.isEmpty() && oldInlineImportsToNewInlineImportsMap.isEmpty()) {
            return null
        }

        val schemaIslString = File("$basePath/$schemaId").readText(Charsets.UTF_8)

        val schemaIonElements = loadAllElements(schemaIslString, IonElementLoaderOptions(includeLocationMeta = true))

        val newlineLocations = schemaIslString.mapIndexedNotNull { i, c -> if (c == '\n') i else null }
        val lineStartLocations = (listOf(0) + newlineLocations.map { it + 1 })
        fun IonLocation?.toIndex(): Int = with(this as IonTextLocation) {
            lineStartLocations[line.toInt() - 1] + charOffset.toInt() - 1
        }

        val newSchemaString = StringPatcher(schemaIslString)

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

        val inlineImportFindingVisitor = visitor@{ it: AnyElement ->
            if (it is StructElement && oldInlineImportsToNewInlineImportsMap.containsKey(it) && oldInlineImportsToNewInlineImportsMap[it] != it) {
                val start = schemaIslString.indexOf('{', startIndex = it.metas.location.toIndex())
                val end = schemaIslString.indexOf("}", startIndex = start)
                val replacementText = oldInlineImportsToNewInlineImportsMap[it].toString()
                newSchemaString.patch(start, end, replacementText)
            }
        }
        schemaIonElements.forEach { it.recursivelyVisit(PreOrder, inlineImportFindingVisitor) }

        return newSchemaString.takeIf { it.hasChanges() }?.toString()
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
            .filter { !isBuiltInTypeName(it.textValue) }
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
        return schema.getDeclaredTypes().asSequence().toList().isEmpty() &&
            schema.getImports().hasNext()
    }

    private fun reconcileHeaderImports(headerImports: List<StructElement>, actualImportedTypes: List<StructElement>): List<StructElement> {
        val newImports = mutableListOf<StructElement>()

        when (importStrategy) {
            RewriteAllAsTypeImports -> newImports.addAll(actualImportedTypes)
            KeepSchemaImportsWriteTypeImports -> {
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
            WriteAllAsSchemaImports -> {
                headerImports.forEach { import ->
                    if (actualImportedTypes.any { import includesTypeImport it } && import !in newImports) {
                        newImports.add(import)
                    }
                }
                actualImportedTypes.forEach { typeImport ->
                    if (!newImports.any { it includesTypeImport typeImport } && typeImport !in newImports) {
                        if (typeImport.getOptional("as") != null) {
                            newImports.add(typeImport)
                        } else {
                            newImports.add(
                                ionStructOf(
                                    "id" to typeImport["id"]
                                )
                            )
                        }
                    }
                }
            }
        }
        return newImports.toList()
    }
}

object RewriteHelpers {

    internal inline fun rewriteSchema(file: File, basePath: String, newBasePath: String, transform: (schemaId: String) -> String?) {
        val schemaId = file.path.substring(basePath.length + 1)

        if (TransitiveImportRewriter.printStatusUpdates) print("Processing $schemaId ...")
        try {
            val newSchemaContent = transform(schemaId)
            val newFile = File("$newBasePath/$schemaId")
            newFile.parentFile.mkdirs()
            if (newSchemaContent == null) {
                file.copyTo(newFile)
                if (TransitiveImportRewriter.printStatusUpdates) println("NO CHANGE")
            } else {
                newFile.createNewFile()
                newFile.appendText(newSchemaContent)
                if (TransitiveImportRewriter.printStatusUpdates) println("CHANGE COMPLETE")
            }
        } catch (t: Throwable) {
            if (TransitiveImportRewriter.printStatusUpdates) println("FAILED")
            else println("$schemaId FAILED")
            println(t)
            if (t is IonElementConstraintException) t.printStackTrace()
        }
    }

    internal inline fun rewriteSchema99(file: File, basePath: String, newBasePath: String, transform: (schema: StringPatcher) -> Unit) {
        val schemaId = file.path.substring(basePath.length + 1)

        val schemaIonText = file.readText(Charsets.UTF_8)
        val schemaPatcher = StringPatcher(schemaIonText)

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
        } catch (t: Throwable) {
            if (TransitiveImportRewriter.printStatusUpdates) println("FAILED")
            else println("$schemaId FAILED")
            println(t)
        }
    }
}
