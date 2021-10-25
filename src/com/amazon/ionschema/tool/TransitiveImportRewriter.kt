package com.amazon.ionschema.tool

import com.amazon.ion.IonContainer
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.ContainerElement
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.IonElementConstraintException
import com.amazon.ionelement.api.IonElementLoaderOptions
import com.amazon.ionelement.api.IonLocation
import com.amazon.ionelement.api.IonTextLocation
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.StructField
import com.amazon.ionelement.api.field
import com.amazon.ionelement.api.ionString
import com.amazon.ionelement.api.ionStructOf
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionelement.api.loadAllElements
import com.amazon.ionelement.api.location
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.internal.IonSchemaSystemImpl
import com.amazon.ionschema.internal.SchemaCore
import com.amazon.ionschema.internal.TypeAliased
import com.amazon.ionschema.internal.TypeImpl
import com.amazon.ionschema.internal.TypeInternal

import com.amazon.ionelement.api.toIonElement
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import com.amazon.ionschema.internal.SchemaImpl
import com.amazon.ionschema.internal.TypeBuiltin
import com.amazon.ionschema.tool.TransitiveImportRewriter.ImportStrategy.*
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

/**
 * TODO:
 *  - documentation
 *  - refactor into readable chunks
 *  - proper test cases
 *  - ability to run from CLI
 */
object TransitiveImportRewriter {

    // Logic
    // Walk file system from a base path
    // Process each schema, and rewrite in a new base-path
    // Read all schemas in the new base path to make sure that they all load correctly

    enum class ImportStrategy {
        /* Might be annoying for some people. Will possibly generate larger diff. Guaranteed to never have name conflicts in the future. */
        RewriteAllAsTypeImports,
        /* Tries to make minimal changes. Will not introduce name conflicts, but you could have some in future. */
        KeepSchemaImportsWriteTypeImports,
        /* Keeps imports smaller, but may cause new name conflict, leaving your schemas in a bad state. */
        WriteAllAsSchemaImports
    }

    private val printStatusUpdates = true
    private val importStrategy = KeepSchemaImportsWriteTypeImports

    val ION = IonSystemBuilder.standard().build()

    fun fixTransitiveImports(basePath: String) {
        val newBasePath = basePath + "_rewrite_" + Instant.now().truncatedTo(ChronoUnit.SECONDS)

        rewriteAll(basePath, newBasePath)

        validateAll(newBasePath)
    }

    private fun validateAll(newBasePath: String) {
        val ISL = IonSchemaSystemBuilder.standard()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(newBasePath))
            .build()

        File(newBasePath).walk()
            .filter { it.isFile }
            .filter { it.path.endsWith(".isl") }
            .forEach { file ->
                val schemaId = file.path.substring(newBasePath.length + 1)
                if (printStatusUpdates) print("Validating $schemaId ...")
                runCatching {
                    ISL.loadSchema(schemaId)
                    if (printStatusUpdates) println("PASS")
                }.onFailure {
                    if (printStatusUpdates) println("FAIL")
                }
            }

    }

    private fun rewriteAll(basePath: String, newBasePath: String) {
        val ISL = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        File(basePath).walk()
            .filter { it.isFile }
            .filter { it.path.endsWith(".isl") }
            .forEach { file ->
                val schemaId = file.path.substring(basePath.length + 1)

                if (printStatusUpdates) print("Rewriting $schemaId ...")
                try {
                    val newSchemaContent = ISL.rewriteImportsForSchema(basePath, schemaId)
                    val newFile = File("$newBasePath/$schemaId")
                    newFile.parentFile.mkdirs()
                    if (newSchemaContent == null) {
                        file.copyTo(newFile)
                        if (printStatusUpdates) println("NO CHANGE")
                    } else {
                        newFile.createNewFile()
                        newFile.appendText(newSchemaContent)
                        if (printStatusUpdates) println("CHANGE COMPLETE")
                    }
                } catch (t: Throwable) {
                    if (printStatusUpdates) println("FAILED")
                    else println("$schemaId FAILED")
                    println(t)
                    if (t is IonElementConstraintException) t.printStackTrace()
                }

            }
    }

    
    internal fun IonSchemaSystem.rewriteImportsForSchema(basePath: String, schemaId: String, debug: Boolean = false): String? {
        this as IonSchemaSystemImpl

        val schema = this.loadSchema(schemaId)

        if (isExportOnlySchema(schema)) {
            val preamble = """
                // Schema '${schemaId}'
                // 
                // The purpose of this schema is to decouple consumers of the schema from the implementation details (ie. specific locations)
                // of each type that it provides, and to indicate to consumers, which types they SHOULD use. Consumers of this type CAN bypass
                // this schema and import other types directly, but they SHOULD NOT without having a really, really good reason to do so.
                // 
                // Essentially, this schema declares types that are intended for public consumption.
                // 
                // Consumers of this schema should not bypass this schema unless directed to do so by the owner(s)/author(s) of this schema.
                // 
                // The type
                //     type::{name:foobar,type:{id:"bar.isl",type:foo}}
                // is analogous to ecmascript
                //     export { foo as foobar } from 'bar.isl'
                // 
            """.trimIndent()
            return preamble + "\n" + writeExports(schema).joinToString("\n") { it.toString() } + "\n"
        }

        val core = SchemaCore(this)

        // find all type references in the schema
        val allTypeReferences = schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findNamedTypeReferences(it) }

        val inlineImports = allTypeReferences.filter { it.isInlineImport() }.map { it.toIonElement().asStruct() }

        val namedImportedTypes = allTypeReferences.filterIsInstance<IonText>()
            .filter { schema.getDeclaredType(it.stringValue()) == null }
            .filter { core.getDeclaredType(it.stringValue()) == null }

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        // If no header and no inline imports, do nothing?
        if (headerImports.isEmpty() && inlineImports.isEmpty()) {
            return null
        }

        if (debug) println("Schema: $schemaId")
        if (debug) println("Header Imports:\n    ${headerImports.joinToString(",\n    ")}")
        if (debug) println("Inline Imports:\n    ${inlineImports.joinToString(",\n    ")}")
        if (debug) println("Named References: $namedImportedTypes")

        val actualImportedTypes = namedImportedTypes.distinctBy { it.stringValue() }
            .map { schema.getType(it.stringValue()) }
            .map {
                when (it) {
                    is TypeAliased -> ionStructOf(
                        "id" to ionString(it.type.schemaId!!),
                        "type" to ionSymbol(it.type.name),
                        "as" to ionSymbol(it.name)
                    )
                    is TypeImpl -> ionStructOf(
                        "id" to ionString(it.schemaId!!),
                        "type" to ionSymbol(it.name)
                    )
                    else -> TODO("Should not be reachable")
                }
            }
            .sortedBy { it["id"].textValue }

        val newImports = reconcileHeaderImports(headerImports, actualImportedTypes)

        if (debug) println("New Header Imports:\n    ${newImports.joinToString(",\n    ")}")


        val inlineImportMap = inlineImports.associate {
                val importedSchemaId = it["id"].textValue
                val importedTypeName = it["type"].textValue
                val importedSchema = loadSchema(importedSchemaId)
                val importedType = importedSchema.getType(importedTypeName) as TypeInternal

                when (importedType) {
                    is TypeImpl -> it to ionStructOf(
                        // Note on the !! -- since the type is imported, then we know it must have a non-null schema id.
                        "id" to ionString(importedType.schemaId!!),
                        "type" to ionSymbol(importedType.name)
                    )
                    is TypeAliased -> {
                        it to ionStructOf(
                            // Note on the !! -- since the type is imported, then we know it must have a non-null schema id.
                            "id" to ionString(importedType.type.schemaId!!),
                            "type" to ionSymbol(importedType.type.name)
                        )
                    }
                    else -> TODO("This should be unreachable")
                }
            }
            .filter { (k, v) -> k isEquivalentImportTo v }

        if (debug) println("Inline Imports Map:\n${inlineImportMap.entries.joinToString("\n") { (k, v) -> "    $k => $v" }}")




        val schemaIslString = File("$basePath/$schemaId").readText(Charsets.UTF_8)

        val schemaIonElements = loadAllElements(schemaIslString, IonElementLoaderOptions(includeLocationMeta = true))


        val newlineLocations = schemaIslString.mapIndexedNotNull { i, c -> if (c == '\n') i else null }
        val lineStartLocations = (listOf(0) + newlineLocations.map { it + 1 })
        fun IonLocation?.toIndex(): Int = with (this as IonTextLocation) {
            lineStartLocations[line.toInt() - 1] + charOffset.toInt() - 1
        }

        val schemaTextReplacements = mutableListOf<Pair<IntRange, String>>()

        if (newImports != headerImports) {
            schemaIonElements.singleOrNull { "schema_header" in it.annotations }
                ?.let {
                    val imports = it.asStruct().getOptional("imports")?.asList() ?: return@let
                    if (imports.values.isEmpty()) return@let

                    if (newImports.isEmpty()) {
                        // TODO: Just delete all imports
                        // Get key locations
                        val importListStartLocation = schemaIslString.indexOf('[', startIndex = imports.metas.location.toIndex())
                        val importListEndLocation = schemaIslString.indexOf(']', startIndex = imports.values.last().metas.location.toIndex())

                        schemaTextReplacements.add(importListStartLocation..importListEndLocation to "[]")
                    } else {
                        // Get key locations
                        val firstImportStartLocation = schemaIslString.indexOf('{', startIndex = imports.values.first().metas.location.toIndex())
                        val lastImportEndLocation = schemaIslString.indexOf('}', startIndex = imports.values.last().metas.location.toIndex())

                        // TODO: this won't work if you have "imports:[ {id:foo type: bar} ]"
                        val startOfFirstImportLine = schemaIslString.substring(0, firstImportStartLocation).lastIndexOf("\n")

                        val importDelimitingWhitespace = schemaIslString.substring(startOfFirstImportLine until firstImportStartLocation)

                        val replacementLocation = firstImportStartLocation..lastImportEndLocation
                        val replacementText = newImports.joinToString(separator = ",$importDelimitingWhitespace")

                        schemaTextReplacements.add(replacementLocation to replacementText)
                    }
                }
        }

        val inlineImportFindingVisitor = visitor@ { it: AnyElement ->
        if (it is StructElement && inlineImportMap.containsKey(it) && inlineImportMap[it] != it) {
                val start = schemaIslString.indexOf('{', startIndex = it.metas.location.toIndex())
                val end = schemaIslString.indexOf("}", startIndex = start)
                val replacementText = inlineImportMap[it].toString()
                schemaTextReplacements.add(start..end to replacementText)
            }
        }
        schemaIonElements.forEach { it.recursivelyVisit(PreOrder, inlineImportFindingVisitor) }
        schemaTextReplacements.sortBy { it.first.first }

        if (schemaTextReplacements.isEmpty()) return null

        if (debug) println(schemaTextReplacements.joinToString(separator = "\n"))

        val newIslString = applyReplacements(schemaIslString, schemaTextReplacements)

        if (debug) println(newIslString)
        
        return newIslString
    }

    private fun isExportOnlySchema(schema: Schema): Boolean {
        return schema.getDeclaredTypes().asSequence().toList().isEmpty()
    }

    private fun writeExports(schema: Schema): List<IonElement> {
        return schema.getTypes().asSequence()
            .filterIsInstance<TypeInternal>()
            .filter { it !is TypeBuiltin && it.schemaId != null }
            .map {
                ionStructOf(
                    "name" to ionSymbol(it.name),
                    "type" to when (it) {
                        is TypeAliased -> ionStructOf(
                            "id" to ionString(it.type.schemaId!!),
                            "type" to ionSymbol(it.type.name)
                        )
                        is TypeImpl -> ionStructOf(
                            "id" to ionString(it.schemaId!!),
                            "type" to ionSymbol(it.name)
                        )
                        else -> TODO("Should not be reachable")
                    },
                    annotations = listOf("type")
                )
            }
            .toList()
            .sortedBy { it["name"].textValue }
    }


    private fun applyReplacements(original: String, replacements: List<Pair<IntRange, String>>): String {
        var startOfRangeToKeep = 0
        return with(StringBuilder()) {
            replacements
                .sortedBy { (range, _) -> range.first }
                .forEach { (range, replacement) ->
                    append(original.substring(startOfRangeToKeep until range.first))
                    append(replacement)
                    startOfRangeToKeep = range.last + 1
                }
            append(original.substring(startOfRangeToKeep))
            toString()
        }
    }


    fun findNamedTypeReferences(ionValue: IonValue): List<IonValue> {
        return if (ionValue is IonStruct) {
            ionValue.flatMap {
                when (it.fieldName) {
                    "type",
                    "not",
                    "element" -> listOf(it)
                    "one_of",
                    "any_of",
                    "all_of",
                    "ordered_elements",
                    "fields" -> it as IonContainer
                    else -> emptyList<IonValue>()
                }
            }.flatMap {
                if (it is IonSymbol || it.isInlineImport())
                    listOf(it)
                else
                    findNamedTypeReferences(it)
            }
        } else {
            emptyList()
        }
    }

    fun reconcileHeaderImports(headerImports: List<StructElement>, actualImportedTypes: List<StructElement>): List<StructElement> {
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
                            newImports.add(ionStructOf(
                                "id" to typeImport["id"]
                            ))
                        }
                    }
                }
            }
        }
        return newImports.toList()
    }

    private infix fun StructElement.includesTypeImport(other: StructElement): Boolean {
        return this["id"].textValue == other["id"].textValue
            && (this.getOptional("type") ?: other["type"]).textValue == other["type"].textValue
            && this.getOptional("as")?.textValue == other.getOptional("as")?.textValue
    }

    private infix fun StructElement.isEquivalentImportTo(other: StructElement): Boolean {
        return this["id"].textValue == other["id"].textValue
            && this.getOptional("type")?.textValue == other.getOptional("type")?.textValue
            && this.getOptional("as")?.textValue == other.getOptional("as")?.textValue
    }

    private fun IonValue.isInlineImport(): Boolean =
        this is IonStruct
            && this.containsKey("id")
            && this.containsKey("type")

    private fun IonElement.isInlineImport(): Boolean =
        this is StructElement && this.fields.map { it.name }
            .let { fieldNames -> "id" in fieldNames && "type" in fieldNames }


    private interface TraversalOrder
    private object PreOrder: TraversalOrder
    private object PostOrder: TraversalOrder

    private fun IonElement.recursivelyVisit(order: TraversalOrder, visitor: (AnyElement) -> Unit) {
        with (this.asAnyElement()) {
            if (order is PreOrder) visitor(this)
            if (this is ContainerElement) asContainerOrNull()?.values?.forEach { child -> child.recursivelyVisit(order, visitor) }
            if (order is PostOrder) visitor(this)
        }
    }
}
