package com.amazon.ionschema.tool

import com.amazon.ion.IonContainer
import com.amazon.ion.IonDatagram
import com.amazon.ion.IonList
import com.amazon.ion.IonSexp
import com.amazon.ion.IonStruct
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.ContainerElement
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.IonElementConstraintException
import com.amazon.ionelement.api.IonElementLoaderOptions
import com.amazon.ionelement.api.IonLocation
import com.amazon.ionelement.api.IonTextLocation
import com.amazon.ionelement.api.ListElement
import com.amazon.ionelement.api.SexpElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionelement.api.TextElement
import com.amazon.ionelement.api.ionListOf
import com.amazon.ionelement.api.ionSexpOf
import com.amazon.ionelement.api.ionString
import com.amazon.ionelement.api.ionStructOf
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionelement.api.loadAllElements
import com.amazon.ionelement.api.location
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.internal.IonSchemaSystemImpl
import com.amazon.ionschema.internal.SchemaCore
import com.amazon.ionschema.internal.TypeInternal
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionelement.api.toIonValue
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Type
import com.amazon.ionschema.internal.TypeBuiltin
import com.amazon.ionschema.tool.TransitiveImportRewriter.ImportStrategy.*
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.Exception

/**
 * TODO:
 *  - documentation
 *  - refactor into readable chunks
 *  - proper test cases
 *  - ability to run from CLI
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

    private val printStatusUpdates = true
    private val importStrategy = KeepSchemaImportsWriteTypeImports

    val ION = IonSystemBuilder.standard().build()

    fun fixTransitiveImports(basePath: String) {
        val newBasePath = basePath + "_rewrite_" + Instant.now().truncatedTo(ChronoUnit.SECONDS)

        println("Pass 1: Rewrite aggregating schemas")
        rewriteAggregatingSchemas(basePath, newBasePath + "_pass1")

        println("Pass 2: Rewrite all other schemas")
        rewriteAll(newBasePath  + "_pass1", newBasePath + "_pass2")

        println("Pass 3: Validate new schemas")
        val success = validateAll(newBasePath  + "_pass2")

        if (success) {
            File(newBasePath + "_pass1").deleteRecursively()
            File(newBasePath + "_pass2").copyRecursively(File(newBasePath))
            File(newBasePath + "_pass2").deleteRecursively()
        } else {
            throw Exception("Rewriter output is invalid.")
        }
    }


    private fun walkFileSystemAuthority(basePath: String) = File(basePath).walk()
        .filter { it.isFile }
        .filter { it.path.endsWith(".isl") }
        .map { file -> file.path.substring(basePath.length + 1) to file }

    private inline fun rewriteSchema(file: File, basePath: String, newBasePath: String, transform: (schemaId: String) -> String?) {
        val schemaId = file.path.substring(basePath.length + 1)

        if (printStatusUpdates) print("Processing $schemaId ...")
        try {
            val newSchemaContent = transform(schemaId)
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


    private fun validateAll(newBasePath: String): Boolean {
        val ISL = IonSchemaSystemBuilder.standard()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(newBasePath))
            .build()

        var success = true
        walkFileSystemAuthority(newBasePath).forEach { (schemaId, _) ->
                if (printStatusUpdates) print("Validating $schemaId ...")
                runCatching {
                    ISL.loadSchema(schemaId)
                    if (printStatusUpdates) println("PASS")
                }.onFailure {
                    success = false
                    if (printStatusUpdates) println("FAIL")
                }
            }
        return success
    }

    private fun rewriteAggregatingSchemas(basePath: String, newBasePath: String) {
        val ISL = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        walkFileSystemAuthority(basePath).forEach { (_, file) -> rewriteSchema(file, basePath, newBasePath) { ISL.rewriteAggregatingSchema(it) } }
    }

    private fun rewriteAll(basePath: String, newBasePath: String) {
        val ISL = IonSchemaSystemBuilder.standard()
            .allowTransitiveImports()
            .withIonSystem(ION)
            .withAuthority(AuthorityFilesystem(basePath))
            .build()

        walkFileSystemAuthority(basePath).forEach { (_, file) -> rewriteSchema(file, basePath, newBasePath) { ISL.rewriteImportsForSchema(basePath, it) } }
    }

    internal fun IonSchemaSystem.rewriteAggregatingSchema(schemaId: String): String? {
        this as IonSchemaSystemImpl

        val schema = this.loadSchema(schemaId)

        return if (isExportOnlySchema(schema)) {
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
            preamble + "\n" + writeExports(schema).joinToString("\n") { it.toString() } + "\n"
        } else {
            null
        }
    }

    internal fun IonSchemaSystem.rewriteImportsForSchema(basePath: String, schemaId: String): String? {
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
        fun IonLocation?.toIndex(): Int = with (this as IonTextLocation) {
            lineStartLocations[line.toInt() - 1] + charOffset.toInt() - 1
        }

        val schemaTextReplacements = mutableListOf<Pair<IntRange, String>>()

        if (newHeaderImports != headerImports) {
            schemaIonElements.singleOrNull { "schema_header" in it.annotations }
                ?.let {
                    val imports = it.asStruct().getOptional("imports")?.asList() ?: return@let
                    if (imports.values.isEmpty()) return@let

                    if (newHeaderImports.isEmpty()) {
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
                        val replacementText = newHeaderImports.joinToString(separator = ",$importDelimitingWhitespace")

                        schemaTextReplacements.add(replacementLocation to replacementText)
                    }
                }
        }

        val inlineImportFindingVisitor = visitor@ { it: AnyElement ->
            if (it is StructElement && oldInlineImportsToNewInlineImportsMap.containsKey(it) && oldInlineImportsToNewInlineImportsMap[it] != it) {
                val start = schemaIslString.indexOf('{', startIndex = it.metas.location.toIndex())
                val end = schemaIslString.indexOf("}", startIndex = start)
                val replacementText = oldInlineImportsToNewInlineImportsMap[it].toString()
                schemaTextReplacements.add(start..end to replacementText)
            }
        }
        schemaIonElements.forEach { it.recursivelyVisit(PreOrder, inlineImportFindingVisitor) }
        schemaTextReplacements.sortBy { it.first.first }

        if (schemaTextReplacements.isEmpty()) return null

        val newIslString = applyStringReplacements(schemaIslString, schemaTextReplacements)

        return newIslString
    }

    internal fun IonSchemaSystem.rewriteSchemaIsl(basePath: String, schemaId: String): IonValue {
        this as IonSchemaSystemImpl

        val schema = this.loadSchema(schemaId)

        if (isExportOnlySchema(schema)) {
            return writeExports(schema).mapTo(getIonSystem().newDatagram()) { it.toIonValue(getIonSystem()) }
        }

        val headerImports: IonElement? = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")

        val replacements: Map<out IonElement, IonElement> = calculateNewInlineImportMapping(schema)
            .let {
                if (headerImports != null && headerImports.asAnyElement().asList().values.isNotEmpty()) {
                    it + (headerImports to ionListOf(calculateNewHeaderImports(schema)))
                } else {
                    it
                }
            }

        return if (replacements.isEmpty()) {
            schema.isl
        } else {
            schema.isl.asSequence().map { it.toIonElement() }
                .map { it.recursivelyTransform(PreOrder) { replacements[it] ?: it } }
                .mapTo(getIonSystem().newDatagram()) { it.asAnyElement().toIonValue(getIonSystem()) }
        }
    }

    private fun IonSchemaSystem.calculateNewHeaderImports(schema: Schema): List<StructElement> {
        this as IonSchemaSystemImpl

        val headerImports: List<StructElement> = schema.isl
            .singleOrNull { it.hasTypeAnnotation("schema_header") }?.toIonElement()
            ?.asStruct()?.getOptional("imports")?.asList()?.values?.map { it.asStruct() }
            ?: emptyList()

        if (headerImports.isEmpty()) {
            return emptyList()
        }

        val core = SchemaCore(this)

        // find all type references in the schema
        val actualImportedTypes = schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findTypeReferences(it.toIonElement()) }
            .asSequence()
            .filterIsInstance<TextElement>()
            .distinctBy { it.textValue }
            .filter { schema.getDeclaredType(it.textValue) == null }
            .filter { core.getDeclaredType(it.textValue) == null }
            .map { schema.getType(it.textValue) }
            .map { writeImportToIsl(it!!) }
            .toList()

        return reconcileHeaderImports(headerImports, actualImportedTypes)
    }

    private fun IonSchemaSystem.calculateNewInlineImportMapping(schema: Schema): Map<StructElement, StructElement> {
        return schema.isl
            .filter { it.hasTypeAnnotation("type") }
            .flatMap { findTypeReferences(it.toIonElement()) }
            .filterIsInstance<StructElement>() // Inline imports are structs, all other references are symbols
            .associateWith { writeImportToIsl(loadSchema(it["id"].textValue).getType(it["type"].textValue)!!) }
            .filter { (k, v) -> k isEquivalentImportTo v } // remove any identity transforms
    }

    private fun writeImportToIsl(type: Type): StructElement {
        val alias = type.name
        val name = type.isl.toIonElement().asStruct()["name"].textValue
        val importedFromSchemaId = (type as TypeInternal).schemaId!!
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

    /**
     * Has at least one import, and has no declared types
     */
    private fun isExportOnlySchema(schema: Schema): Boolean {
        return schema.getDeclaredTypes().asSequence().toList().isEmpty()
            && schema.getImports().hasNext()
    }

    private fun writeExports(schema: Schema): List<AnyElement> {
        return schema.getTypes().asSequence()
            .filterIsInstance<TypeInternal>()
            .filter { it !is TypeBuiltin && it.schemaId != null }
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
            .toList()
            .sortedBy { it["name"].textValue }
            .map { it.asAnyElement() }
    }

    private fun applyStringReplacements(original: String, replacements: List<Pair<IntRange, String>>): String {
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

    private fun IonElement.recursivelyTransform(order: TraversalOrder, transform: (AnyElement) -> IonElement): IonElement {
        var result = this as AnyElement
        result = if (order is PreOrder) transform(result).asAnyElement() else result
        result = when (result) {
            is ListElement -> result.values.map { child -> child.recursivelyTransform(order, transform) }.let { ionListOf(it, result.annotations, result.metas) }
            is SexpElement -> result.values.map { child -> child.recursivelyTransform(order, transform) }.let { ionSexpOf(it, result.annotations, result.metas) }
            is StructElement -> result.values.map { child -> child.recursivelyTransform(order, transform) }.let { ionListOf(it, result.annotations, result.metas) }
            else -> result
        }.asAnyElement()
        result = if (order is PostOrder) transform(result).asAnyElement() else result
        return result
    }
}
