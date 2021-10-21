package com.amazon.ionschema.migratortool

import com.amazon.ion.IonContainer
import com.amazon.ion.IonDatagram
import com.amazon.ion.IonList
import com.amazon.ion.IonSequence
import com.amazon.ion.IonSexp
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonValue
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.Type
import com.amazon.ionschema.internal.IonSchemaSystemImpl
import com.amazon.ionschema.internal.SchemaCore
import com.amazon.ionschema.internal.SchemaImpl
import com.amazon.ionschema.internal.TypeAliased
import com.amazon.ionschema.internal.TypeBuiltin
import com.amazon.ionschema.internal.TypeCore
import com.amazon.ionschema.internal.TypeImpl
import com.amazon.ionschema.internal.TypeInline
import com.amazon.ionschema.internal.TypeInternal
import com.amazon.ionschema.internal.TypeNamed
import com.amazon.ionschema.internal.TypeNullable
import com.amazon.ionschema.internal.constraint.AllOf
import com.amazon.ionschema.internal.constraint.AnyOf
import com.amazon.ionschema.internal.constraint.Element
import com.amazon.ionschema.internal.constraint.Not
import com.amazon.ionschema.internal.constraint.OneOf

// Logic
// Walk file system from a base path
// Process each schema, and rewrite in a new base-path
// Read all schemas in the new base path to make sure that they all load correctly
// Either
//  1. Move contents of new directory to replace files in old directory
//  2. Let the developer handle that, and also comments...
//  3. Use Git merges to merge old (with comments) and new (with correct imports)


fun IonSchemaSystem.fixTransitiveImports2(schemaId: String): Unit {
    this as IonSchemaSystemImpl

    val schema = this.loadSchema(schemaId) as SchemaImpl
    val core = SchemaCore(this)

    val importedTypes = schema.getDeclaredTypes().asSequence().toList().flatMap {
        getAllImportedTypes(schema.schemaId!!, it as TypeInternal, it.isl)
    }.distinctBy { (isl, t) -> Triple(isl, t.schemaId, t.name) }

    importedTypes.forEach { (isl, t) ->
        println(Triple(isl, t.schemaId, t.name))
    }
}

internal fun getAllImportedTypes(schemaId: String, type: TypeInternal, parentNode: IonValue): List<Pair<IonValue, TypeInternal>> {
    return if (type.schemaId != schemaId) when (type) {
            is TypeBuiltin -> emptyList()
            else -> listOf(parentNode to type)
        }
    else when (type) {
        is TypeImpl -> type.constraints.flatMap {
            when (it) {
                is com.amazon.ionschema.internal.constraint.Type -> getAllImportedTypes(schemaId, it.typeReference(), it.ion)
                is Element -> getAllImportedTypes(schemaId, it.typeReference(), it.ion)
                is Not -> getAllImportedTypes(schemaId, it.type(), it.ion)
                is AnyOf -> it.types.mapIndexed { idx, ref -> getAllImportedTypes(schemaId, ref(), (it.ion as IonList)[idx] ) }.flatten()
                is AllOf -> it.types.mapIndexed { idx, ref -> getAllImportedTypes(schemaId, ref(), (it.ion as IonList)[idx] ) }.flatten()
                is OneOf -> it.types.mapIndexed { idx, ref -> getAllImportedTypes(schemaId, ref(), (it.ion as IonList)[idx] ) }.flatten()
                // TODO: OrderedElement
                // TODO: Fields
                else -> emptyList()
            }
        }
        is TypeAliased -> getAllImportedTypes(schemaId, type.type, type.isl)
        is TypeNamed -> getAllImportedTypes(schemaId, type.type, type.isl)
        is TypeNullable -> getAllImportedTypes(schemaId, type.type, type.isl)
        is TypeInline -> getAllImportedTypes(schemaId, type.type, type.isl)
        else -> emptyList()
    }
}


fun IonSchemaSystem.fixTransitiveImports(schemaId: String): Unit { // TODO: IonValue?
    this as IonSchemaSystemImpl

    val schema = this.loadSchema(schemaId)
    val core = SchemaCore(this)

    // find all type references in the schema
    val allTypeReferences: List<IonValue> = schema.isl
        .filter { it.hasTypeAnnotation("type") }
        .flatMap { findNamedTypeReferences(it) }

    val inlineImports = allTypeReferences.filter { it.isInlineImport() }

    val namedImportedTypes = allTypeReferences.filterIsInstance<IonText>()
        .filter { schema.getDeclaredType(it.stringValue()) == null }
        .filter { core.getDeclaredType(it.stringValue()) == null }

    val headerImports: List<IonValue> = schema.isl
        .singleOrNull { it.hasTypeAnnotation("schema_header") }?.let { it as? IonStruct }
        ?.get("imports")?.let { it as IonList }
        ?: emptyList()

    // If no header and no inline imports, do nothing?
    if (headerImports.isEmpty() && inlineImports.isEmpty()) {
        return
        //TODO("Do nothing")
    }

    println("Schema: $schemaId")
    println("Header Imports:\n    ${headerImports.joinToString(",\n    ")}")
    println("Inline Imports:\n    ${inlineImports.joinToString(",\n    ")}")
    println("Named References: $namedImportedTypes")

    val ION = this.getIonSystem()
    val newImports = namedImportedTypes.distinctBy { it.stringValue() }
        .map { schema.getType(it.stringValue()) }
        .map {
            when (it) {
                is TypeAliased -> ION.newEmptyStruct().apply {
                    add("id").newString(it.type.schemaId)
                    add("type").newSymbol(it.type.name)
                    add("as").newSymbol(it.name)
                }
                is TypeImpl -> ION.newEmptyStruct().apply {
                    add("id").newString(it.schemaId)
                    add("type").newSymbol(it.name)
                }
                else -> TODO()
            }
        }
    println("New Header Imports:\n    ${newImports.joinToString(",\n    ")}")


    val inlineImportMap = inlineImports.map {
        val importedSchemaId = ((it as IonStruct).get("id") as IonText).stringValue()
        val importedTypeName = ((it as IonStruct).get("type") as IonText).stringValue()
        val importedSchema = loadSchema(importedSchemaId)
        val importedType = importedSchema.getType(importedTypeName) as TypeInternal

        if (importedType is TypeImpl)
            it to ION.newEmptyStruct().apply {
                add("id").newString(importedType.schemaId)
                add("type").newSymbol(importedType.name)
            }
        else if (importedType is TypeAliased) {
            it to ION.newEmptyStruct().apply {
                add("id").newString(importedType.type.schemaId)
                add("type").newSymbol(importedType.type.name)
            }
        } else {
            TODO()
        }
    }.toMap()

    println("Inline Imports Map:\n${inlineImportMap.entries.joinToString("\n") { (k, v) -> "    $k => $v" }}")


    val newIsl = schema.isl
        .recursivelyApply(PreOrder) { (inlineImportMap[it] ?: it).clone() }
        .let { it as IonSequence }
        .map {
            if (it.hasTypeAnnotation("schema_header"))
                ION.newEmptyStruct().apply {
                    add("imports").newEmptyList().apply { addAll(newImports.map(IonValue::clone)) }
                    addTypeAnnotation("schema_header")
                }
            else
                it.clone()
        }

    println(newIsl.toString())
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
                else -> emptySet<IonValue>()
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

fun IonValue.isInlineImport(): Boolean =
    this is IonStruct
        && this.containsKey("id")
        && this.containsKey("type")


/**
 * Represents different traversal orders for the [recursivelyApply] function.
 */
private sealed class TraversalOrder
private object PreOrder : TraversalOrder()
private object PostOrder : TraversalOrder()

/**
 * Recursively applies a transformation/mapping function to an IonValue and its children
 *
 * The transform function _must return the same instance_ if no change is desired for a given node.
 *
 * This function _always returns a new IonValue instance_.
 */
private fun IonValue.recursivelyApply(order: TraversalOrder, transform: (IonValue) -> IonValue): IonValue {
    return run { if (order is PreOrder) transform(this) else this }
        .run { if (this is IonContainer && !this.isNullValue) this.map { child -> child.recursivelyApply(order) { transform(it) } } else this }
        .run { if (order is PostOrder) transform(this) else this }
}

/**
 * Returns a new IonContainer of the same implementation type as the original IonContainer containing the results of applying the given [transform] function
 * to each element in the original IonContainer. Because an IonValue cannot have more than one parent, this will perform a deep copy of any elements where the
 * [transform] function returns the same instance that it was given.
 *
 * All annotations on the original IonContainer are copied to the new one.
 */
inline fun <reified T : IonContainer> T.map(transform: (IonValue) -> IonValue): T {
    return when (this) {
        is IonStruct -> mapTo(system.newEmptyStruct()) { name, value -> name to transform(value) } as T
        is IonList -> mapTo(system.newEmptyList(), transform) as T
        is IonSexp -> mapTo(system.newEmptySexp(), transform) as T
        is IonDatagram -> mapTo(system.newDatagram(), transform) as T
        else -> throw UnsupportedOperationException(
            "All implementations of IonContainer are one of IonStruct, IonList, IonSexp, and IonDatagram. " +
                "Instead, type is: ${this.javaClass.canonicalName}"
        )
    }.also {
        this.mapAnnotationsTo(it)
    }
}

/**
 * Applies the given [transform] function to each element of the original IonSequence and appends the results to the given [destination]. Because an IonValue
 * cannot have more than one parent, this will perform a deep copy of any elements where the [transform] function returns the same instance that it was given.
 */
inline fun <T : IonSequence> IonSequence.mapTo(destination: T, transform: (IonValue) -> IonValue): T {
    for (item in this) {
        destination.add(transform(item).cloneIf { it === item })
    }
    return destination
}

/**
 * Applies the given [transform] function to each element of the original IonStruct and appends the results to the given [destination]. Because an IonValue
 * cannot have more than one parent, this will perform a deep copy of any elements where the [transform] function returns the same IonValue instance that it was
 * given.
 */
inline fun IonStruct.mapTo(destination: IonStruct, transform: (String, IonValue) -> Pair<String, IonValue>): IonStruct {
    for (item in this) {
        val (fieldName, transformedItem) = transform(item.fieldName, item)
        destination.add(fieldName, transformedItem.cloneIf { it === item })
    }
    return destination
}

/**
 * Applies the given [transform] function to each annotation of the original IonValue and appends the results to the given [destination]. If no transformation
 * is given, defaults to the identity function, and can be used for simple copying of annotations.
 */
inline fun <C : IonValue> IonValue.mapAnnotationsTo(destination: C, transform: (String) -> String = { it }): C {
    for (annotation in this.typeAnnotations) {
        destination.addTypeAnnotation(transform(annotation))
    }
    return destination
}

/**
 * Returns a clone of the the ion value if the given condition evaluates to true. Uses [IonValue.clone] to get a deep copy.
 */
inline fun IonValue.cloneIf(predicate: (IonValue) -> Boolean) = if (predicate(this)) clone() else this

/**
 * Gets the value of a field in this struct. This allows us to get [null] even from a [RequiredFieldsTypeSafeStruct] if we want it.
 */
fun IonStruct.getOrNull(fieldName: String): IonValue? = if (containsKey(fieldName)) get(fieldName) else null

