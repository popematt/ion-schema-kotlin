package com.amazon.ionschema.cli.generator

import com.amazon.ion.IonBool
import com.amazon.ion.IonString
import com.amazon.ion.IonStruct
import com.amazon.ion.IonText
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.cli.util.into
import com.amazon.ionschema.cli.util.tryInto
import com.amazon.ionschema.model.Constraint
import com.amazon.ionschema.model.ExperimentalIonSchemaModel
import com.amazon.ionschema.model.NamedTypeDefinition
import com.amazon.ionschema.model.OpenContentFields
import com.amazon.ionschema.model.SchemaDocument
import com.amazon.ionschema.model.SchemaHeader
import com.amazon.ionschema.model.TypeArgument
import com.amazon.ionschema.model.TypeDefinition
import com.amazon.ionschema.model.ValidValue
import com.amazon.ionschema.model.VariablyOccurringTypeArgument.Companion.OCCURS_OPTIONAL
import com.amazon.ionschema.model.VariablyOccurringTypeArgument.Companion.OCCURS_REQUIRED

@OptIn(ExperimentalIonSchemaModel::class)
class Converter(val options: Options) {
    data class Options(
        val nativeTypeMappings: List<NativeTypeMapping> = NativeTypeMapping.DEFAULT,
        val schemaIdToModuleNamespaceStrategy: (String) -> List<String>
    )

    data class NativeTypeMapping(val schemaId: String?, val typeId: String, val mapping: Map<String, String>) {
        companion object {
            private val ION = IonSystemBuilder.standard().build()
            @JvmStatic
            val DEFAULT: List<NativeTypeMapping> = this.javaClass.classLoader.getResourceAsStream("default_native_type_mapping.ion")
                .let { ION.newReader(it) }
                .let { ION.iterate(it) }
                .asSequence()
                .flatMap { schemaStruct ->
                    val schemaId = schemaStruct.typeAnnotations.singleOrNull()
                    schemaStruct.into<IonStruct>().flatMap { langStruct ->
                        val lang = langStruct.fieldName
                        langStruct.into<IonStruct>().map { typeField -> Triple(typeField.fieldName, lang, typeField.into<IonString>().stringValue()) }
                            .groupBy { (typeId, _, _) -> typeId }
                            .mapValues { (_, typeMapping) -> typeMapping.associate { (_, lang, qualifiedName) -> lang to qualifiedName }}
                            .map { (typeId, mapping) -> NativeTypeMapping(schemaId, typeId, mapping) }
                    }
                }
                .toList()
        }
    }

    /**
     * Fields that code generator will check to find documentation. Plain text is recommended since code gen will not
     * convert the documentation format at this time. Markdown is readable even when un-rendered, so it's not a bad idea
     * given that several programming languages support markdown in docs.
     */
    val DOC_FIELDS = listOf("doc", "docs", "documentation", "\$doc", "\$docs", "\$documentation", "_doc", "_docs", "_documentation")
    /** Override the type name, or provide a name for an anonymous inline type */
    val CODEGEN_NAME = "\$codegen_name"
    /** Use to override a field name, or add a field name to a tuple in languages that don't support tuples */
    val CODEGEN_FIELD_NAME = "\$codegen_field_name"
    /** Just ignore it for code gen. Use at your own risk. */
    val CODEGEN_IGNORE = "\$codegen_ignore"
    /** Use a native type instead of generating a type for this */
    val CODEGEN_USE = "\$codegen_use"
    /** Override the default heuristic to specify record, tuple, enum, map, list, set, sum */
    val CODEGEN_KIND = "\$codegen_kind"

    // Todo?
    ///** top level open content that is a type definition that is only used for code generation */
    //val CODEGEN_TYPE = "\$codegen_type"

    fun OpenContentFields.getDocs(): String? = firstOrNull { it.first in DOC_FIELDS && it.second is IonText }?.second?.into<IonText>()?.stringValue()
    fun TypeDefinition.getCodegenNativeTypeMapping(): Map<String, String>? = openContent.getAtMostOne(CODEGEN_USE)?.tryInto<IonStruct>()?.associate { it.fieldName to it.into<IonText>().stringValue() }
    fun NamedTypeDefinition.getCodegenName() = typeDefinition.getCodegenName() ?: typeName
    fun TypeDefinition.getCodegenIgnore() = openContent.getAtMostOne(CODEGEN_IGNORE)?.into<IonBool>()?.booleanValue()
    fun TypeDefinition.getCodegenName(): String? = openContent.getAtMostOne(CODEGEN_NAME)?.tryInto<IonText>()?.stringValue()
    fun OpenContentFields.getAtMostOne(name: String): IonValue? = filter { it.first == name }.also { check(it.size <= 1) }.singleOrNull()?.second


    fun toTypeDomain(schemaDocuments: List<SchemaDocument>): TypeDomain {
        val containers = schemaDocuments.map { it.toTypeDomainNode() }
        val nativeTypeBindings = options.nativeTypeMappings.map {
            val baseId = it.schemaId?.let { options.schemaIdToModuleNamespaceStrategy(it) } ?: emptyList()
            Node(
                id = Id(baseId + it.typeId),
                docs = null,
                selfType = EntityDefinition.NativeType(it.mapping),
                children = emptyList()
            )
        }
        return TypeDomain( nativeTypeBindings + containers)
    }

    private fun SchemaDocument.toTypeDomainNode(): Node {
        val schemaId = checkNotNull(this.id)

        val id = Id(options.schemaIdToModuleNamespaceStrategy(schemaId))
        return Node(
            id = id,
            docs = this.items.firstNotNullOfOrNull {
                when (it) {
                    is SchemaHeader -> it.openContent.getDocs()
                    is SchemaDocument.OpenContent -> {
                        if (it.value.typeAnnotations.any { a -> a in DOC_FIELDS }) {
                            it.value.tryInto<IonText>()?.stringValue()
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            },
            selfType = null,
            children = declaredTypes.map { (_, type) -> type.toTypeDomainNode(id) }
        )
    }

    private fun NamedTypeDefinition.toTypeDomainNode(parentId: Id): Node {
        val children = mutableListOf<Node>()
        val definition = typeDefinition.toEntityDefinition(parentId + getCodegenName(), children)
        return Node(
            id = parentId + getCodegenName(),
            selfType = definition,
            docs = typeDefinition.openContent.getDocs(),
            children = children.toList(),
        )
    }

    private fun TypeArgument.InlineType.toTypeDomainNode(id: Id): Node {
        val children = mutableListOf<Node>()
        val definition = typeDefinition.toEntityDefinition(id, children)
        return Node(
            id = id,
            selfType = definition,
            docs = typeDefinition.openContent.getDocs(),
            children = children.toList()
        )
    }


    // TODO: Pass around a mutable list for children so that we can embed any inline type definitions.

    private fun TypeDefinition.toEntityDefinition(parentId: Id, children: MutableList<Node>): EntityDefinition {
        return when {
            // Native Type
            hasNativeTypeMapping(this) -> {
                EntityDefinition.NativeType(this.getCodegenNativeTypeMapping()!!, this)
            }
            // Enum
            isEnum(constraints) -> {
                EntityDefinition.EnumType(
                    constraints.filterIsInstance<Constraint.ValidValues>()
                        .single().values
                        .map { (it as ValidValue.Value).value.into<IonText>().stringValue() }, this)
            }
            // Discriminated union
            hasSingleOneOfConstraint(constraints) -> {
                val oneOf = constraints.single() as Constraint.OneOf
                val variants = oneOf.types.mapIndexed { idx, it ->
                    val variantName = when (it) {
                        is TypeArgument.Import -> it.typeName
                        is TypeArgument.InlineType -> it.typeDefinition.getCodegenName() ?: "variant$idx"
                        is TypeArgument.Reference ->  it.typeName
                    }
                    val ref = toEntityReference(parentId, variantName, it, children)
                    variantName to ref
                }.toMap()
                EntityDefinition.SumType(variants, this)
            }
            // Record type
            hasClosedFieldNamesConstraint(constraints) -> {
                val fields = constraints.filterIsInstance<Constraint.Fields>().single().fields.mapValues { (fName, fType) ->
                    val optional = when (fType.occurs) {
                        OCCURS_REQUIRED -> false
                        OCCURS_OPTIONAL -> true
                        else -> throw IllegalArgumentException("Codegen does not support fields that can occur more than once.")
                    }
                    toEntityReference(parentId, fName, fType.typeArg, children)
                        .copy(optional = optional)
                }
                EntityDefinition.RecordType(fields, this)
            }
            // Scalars
            isConstrainedScalar(constraints) -> {
                EntityDefinition.ConstrainedScalarType(
                    typeDefinition = this,
                )
            }
            // List/Map types
            isParameterizedList(constraints) -> {
                EntityDefinition.ParameterizedType(
                    type = Id("list"),
                    parameters = listOf(toEntityReference(parentId, "element", constraints.filterIsInstance<Constraint.Element>().single().type, children)),
                    typeDefinition = this,
                )
            }
            // TODO: Map type
            // TODO: Set type?


            // TODO: Tuple type


            else -> TODO("Something else? $this")

        }
    }

    private fun isConstrainedScalar(constraints: Set<Constraint>): Boolean {
        return constraints.any {
            it is Constraint.Regex ||
                    it is Constraint.CodepointLength ||
                    it is Constraint.Utf8ByteLength ||
                    it is Constraint.TimestampPrecision ||
                    it is Constraint.TimestampOffset ||
                    it is Constraint.Exponent ||
                    it is Constraint.Precision ||
                    it is Constraint.ByteLength ||
                    it is Constraint.Ieee754Float
        }
    }

    private fun hasNativeTypeMapping(typeDefinition: TypeDefinition): Boolean {
        val mapping = typeDefinition.getCodegenNativeTypeMapping()
        return mapping != null
    }

    private fun isParameterizedList(constraints: Set<Constraint>): Boolean {
        return constraints.any { it is Constraint.Type && it.type == TypeArgument.Reference("list") }
                && constraints.any { it is Constraint.Element }
    }

    private fun hasSingleOneOfConstraint(constraints: Set<Constraint>): Boolean {
        return constraints.size == 1 && constraints.single() is Constraint.OneOf
    }

    private fun isEnum(constraints: Set<Constraint>): Boolean {
        val validValues = constraints.filterIsInstance<Constraint.ValidValues>().singleOrNull() ?: return false
        return validValues.values.all { it is ValidValue.Value && it.value is IonText && !it.value.isNullValue }
    }

    // Naive heuristic
    // If it has a closed fields constraint, then it's a record
    // If it has an open fields constraint, then it's either a map or an interface
    private fun hasClosedFieldNamesConstraint(constraints: Set<Constraint>): Boolean {
        return constraints.filterIsInstance<Constraint.Fields>().any { it.closed }
    }

    private fun toEntityReference(
        /** ID of the parent of the reference that we're creating */
        parentId: Id,
        /** Name for the ref that can be added to the parent ID if we need an ID for the ref */
        refName: String,
        /** Type Arg that we're converting */
        typeArg: TypeArgument,
        /** Children of the parent */
        children: MutableList<Node>
    ): MaybeId {

        val base = when (typeArg) {
            is TypeArgument.Import -> TODO("Inline imports not supported for code gen yet.")
            is TypeArgument.InlineType -> {
                // Elide inline types that contain only `type`
                if (typeArg.typeDefinition.constraints.singleOrNull() is Constraint.Type) {
                    return toEntityReference(parentId, refName, (typeArg.typeDefinition.constraints.single() as Constraint.Type).type, children)
                }
                // - Calculate an ID for the inline type
                // - Create a container for the type and call the toEntityDefinition function
                // - Add the new container as a child of the current container
                // - return the Appropriate type reference.
                val name = typeArg.typeDefinition.getCodegenName() ?: refName
                val container = typeArg.toTypeDomainNode(parentId + name)
                children.add(container)
                parentId + name
            }
            is TypeArgument.Reference -> Id(listOf(typeArg.typeName))
        }
        return MaybeId(base, optional = false, nullable = typeArg.nullability == TypeArgument.Nullability.OrNull)
    }
}
