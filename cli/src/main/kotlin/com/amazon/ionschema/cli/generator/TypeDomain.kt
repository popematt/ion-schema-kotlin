package com.amazon.ionschema.cli.generator

import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.model.ExperimentalIonSchemaModel
import com.amazon.ionschema.model.TypeDefinition

private val ION = IonSystemBuilder.standard().build()

data class TypeDomain(val entities: List<Node>) {

    private fun Iterable<Node>.flatten(): List<Node> = flatMap { it.children.flatten() + it }

    operator fun get(id: Id): Node? {
        return entities.flatten().singleOrNull { it.id == id }
    }
}

/**
 * All of these "toIon" functions are for debugging only at this point.
 */
fun Node.toIon(): IonValue = ION.newEmptyStruct().apply {
    add("id", id.toIon())
    add("docs").newString(docs)
    add("selfType", selfType?.toIon() ?: ION.newNull())
    add("children").newEmptyList().apply { children.forEach { add(it.toIon()) } }
}
fun Id.toIon() = ION.newSymbol(name).apply { parentId().parts.toTypedArray().forEach { addTypeAnnotation(it) } }

fun MaybeId.toIon(): IonValue = ION.newEmptySexp().apply {
    addTypeAnnotation("maybe")
    add(id.toIon())
    // TODO: nullable/optional
}

fun EntityDefinition.toIon() = when (this) {
    is EntityDefinition.EnumType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("enum")
        add("values").newEmptyList().apply {
            values.forEach { add(ION.newSymbol(it)) }
        }
    }
    is EntityDefinition.NativeType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("native")
        qualifiedNames.forEach { (k, v) -> add(k).newString(v.fullyQualifiedTypeName) }
    }
    is EntityDefinition.RecordType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("record")
        components.forEach { (k, v) -> add(k, v.toIon()) }
    }
    is EntityDefinition.SumType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("sum")
        variants.forEach { (k, v) -> add(k, v.toIon()) }
    }
    is EntityDefinition.TupleType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("tuple")
        components.forEachIndexed { i, it -> add("$i", it.toIon()) }
    }
    is EntityDefinition.ParameterizedType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("parameterized")
        add("type", this@toIon.type.toIon())
        add("parameters").newEmptyList().apply { parameters.forEach { add(it.toIon()) } }
    }
    is EntityDefinition.CollectionType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("collection")
        add("type", this@toIon.type.toIon())
        add("item", this@toIon.item.toIon())
    }
    is EntityDefinition.AssociationType -> ION.newEmptyStruct().apply {
        addTypeAnnotation("collection")
        add("type", this@toIon.type.toIon())
        add("key", this@toIon.key.toIon())
        add("value", this@toIon.value.toIon())
    }
    is EntityDefinition.ConstrainedScalarType -> ION.newSymbol("TODO") // TODO: implement this
}

data class Id(val parts: List<String>) {
    constructor(vararg parts: String) : this(parts.toList())
    operator fun plus(name: String): Id = Id(parts + name)
    operator fun plus(other: Id): Id = Id(parts + other.parts)
    val name: String get() = parts.lastOrNull() ?: ""
    fun parentId(): Id = Id(parts.dropLast(1))
}

/** Represents Optional AND nullable. Generators may choose to merge the two concepts or keep the separate. */
data class MaybeId(val id: Id, val optional: Boolean, val nullable: Boolean)

data class Node(
    val id: Id,
    val docs: String?,
    val selfType: EntityDefinition?,
    val children: List<Node>,
)

/**
 * Notes:
 * - There is intentionally no inheritance here. The only thing we _will_ consider doing is indicating that X implements
 *   some interface, but it's completely specified by the user. We will not infer it because Ion Schema doesn't have that
 *   type of inheritance.
 * - Polymorphism is achieved by using discriminated unions.
 */
@OptIn(ExperimentalIonSchemaModel::class)
sealed class EntityDefinition {
    abstract val typeDefinition: TypeDefinition?

    // TODO: Interfaces? Maybe needs to have a `$codegen_implements` field in implementations
    /** I.e. like a Java enum */
    data class EnumType(val values: List<String>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /** AKA discriminated union (that can hold state); like a Rust enum or Kotlin sealed class */
    data class SumType(val variants: Map<String, MaybeId>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /** I.e. POJO, kotlin data class, etc. */
    data class RecordType(val components: Map<String, MaybeId>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /** I.e. like a Rust tuple struct */
    data class TupleType(val components: List<MaybeId>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /** A type that is defined by a mapping to fully qualified type names in the target language. */
    data class NativeType(val qualifiedNames: Map<String, Converter.NativeTypeBinding>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /**
     * Parameterized type for e.g. maps, lists, etc.
     * Unlike NativeType, these are hard-coded for certain shapes that look like, e.g. a list, set, or map.
     *
     * This was an attempt to treat maps, lists, etc. generically, but I don't think it works out very well. The
     * trouble is that while we can model the fully qualified types this way, it's not easy to model the serde aspect.
     *
     * TODO: Remove in favor of CollectionType and AssociativeCollectionType?
     */
    data class ParameterizedType(val type: Id, val parameters: List<MaybeId>, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()

    /**
     * For lists, sets, bags, etc.
     */
    data class CollectionType(val type: Id, val item: MaybeId, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()
    /**
     * For dicts, maps, etc.
     */
    data class AssociationType(val type: Id, val key: MaybeId, val value: MaybeId, override val typeDefinition: TypeDefinition? = null) : EntityDefinition()

    /**
     * Represents a type that is a scalar with some constraints. Some programming languages, such as Rust, can easily
     * create type wrappers that implement these constraints. Other languages, such as Java, get way too verbose if you
     * try to do that.
     *
     * TODO: rename? Certainly needs to be refined a little bit.
     */
    data class ConstrainedScalarType(val scalarType: Id, override val typeDefinition: TypeDefinition) : EntityDefinition()
}
