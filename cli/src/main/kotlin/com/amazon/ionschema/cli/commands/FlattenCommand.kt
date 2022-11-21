package com.amazon.ionschema.cli.commands

import com.amazon.ion.IonList
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonType
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.IonSchemaVersion
import com.amazon.ionschema.ResourceAuthority
import com.amazon.ionschema.Schema
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

class FlattenCommand : CliktCommand() {

    companion object {
        val BASE_TYPES = listOf(
            "\$null",
            "\$bool",
            "\$int",
            "\$float",
            "\$decimal",
            "\$timestamp",
            "\$symbol",
            "\$string",
            "\$blob",
            "\$clob",
            "\$struct",
            "\$sexp",
            "\$list",
            "\$number",
            "\$lob",
            "\$text",
            "\$any",
            "nothing",
            "document",
        )
    }

    private val ion = IonSystemBuilder.standard().build()

    private val fileSystemAuthorities by option(
        "-a", "--authority",
        help = "The root(s) of the file system authority(s). " +
            "Authorities are only required if you need to import a type from another schema file or if you are loading a schema using the --id option."
    )
        .file(canBeFile = false, mustExist = true, mustBeReadable = true)
        .multiple()

    private val useIonSchemaSchemaAuthority by option("-I", "--isl-for-isl", help = "Indicates that the Ion Schema Schemas authority should be included in the schema system configuration.")
        .flag()

    val iss by lazy {
        IonSchemaSystemBuilder.standard()
            .apply {
                withIonSystem(ion)
                withAuthorities(fileSystemAuthorities.map { AuthorityFilesystem(it.path) })
                if (useIonSchemaSchemaAuthority) addAuthority(ResourceAuthority.forIonSchemaSchemas())
                allowTransitiveImports(false)
            }
            .build()
    }

    private val schema by mutuallyExclusiveOptions<IonSchemaSystem.() -> Schema>(

        option("--id", help = "The ID of a schema to load from one of the configured authorities")
            .convert { { loadSchema(it) } },

        option("--schema-text", "-t", help = "The Ion text content of a schema document")
            .convert { { newSchema(it) } },

        option("--schema-file", "-f", help = "A path to a schema file")
            .file(mustExist = true, mustBeReadable = true, canBeDir = false)
            .convert { { newSchema(it.readText()) } },

        option(
            "-v", "--version",
            help = "An empty schema document for the specified Ion Schema version. " +
                "The version must be specified as X.Y; e.g. 2.0"
        )
            .enum<IonSchemaVersion> { it.name.drop(1).replace("_", ".") }
            .convert { { newSchema(it.symbolText) } },

        name = "Schema",
        help = "All Ion Schema types are defined in the context of a schema document, so it is necessary to always " +
            "have a schema document, even if that schema document is an implicit, empty schema. If a schema is " +
            "not specified, the default is an implicit, empty Ion Schema 2.0 document."
    ).default { newSchema() }

    private val type by argument(help = "An ISL type name or inline type definition.")
        .check(lazyMessage = { "Not a valid type reference: $it" }) {
            with(ion.singleValue(it)) {
                !isNullValue && type in listOf(IonType.SYMBOL, IonType.STRUCT)
            }
        }

    override fun run() {
        val islSchema = iss.schema()

        val typeIon = iss.ionSystem.singleValue(type)
        val islType = if (typeIon is IonSymbol) {
            islSchema.getType(typeIon.stringValue()) ?: throw IllegalArgumentException("No such type: $type -- ${islSchema.getTypes().asSequence().map { it.name }.toList()}")
        } else {
            islSchema.newType(typeIon as IonStruct)
        }

        val result = flatten(islSchema, islType.isl, stripNameAndTypeAnnotation = false)

        echo(islType.isl.toString())
        echo("\n...flattens to...\n")
        echo(result.toString())
    }

    // lift constraints from "all_of" and "type"
    // try to merge two constraints of the same name
    // If all the branches of "any_of" or "one_of" have the same constraint, lift it up

    private fun flatten(schema: Schema, typeIsl: IonValue, typeStack: List<String> = emptyList(), stripNameAndTypeAnnotation: Boolean = true): IonStruct {

        val typeName = ((typeIsl as? IonStruct)?.get("name") as? IonSymbol)?.stringValue()

        if (typeName in typeStack) {
            return ion.newEmptyStruct().apply {
                // addTypeAnnotation("typeName=$typeName")
                add("type").newSymbol(typeName).addTypeAnnotation("__recursion__")
                // add("recursive").newBool(true)
            }
        }

        fun flatten(schema: Schema, typeIsl: IonValue): IonStruct {
            val typeNames = if (typeName == null) typeStack else (typeStack + typeName)
            return flatten(schema, typeIsl, typeNames)
        }

        return when (typeIsl) {
            is IonStruct -> if (typeIsl.containsKey("id")) {
                schema.getSchemaSystem().loadSchema((typeIsl["id"] as IonText).stringValue())
                    .let { flatten(it, it.getType((typeIsl["type"] as IonSymbol).stringValue())!!.isl) }
            } else {
                // Base case is when `otherStructs` is empty
                val newFields: List<Pair<String, IonValue>> = typeIsl
                    .map { it.fieldName to it }
                    .flatMap { (key, value) ->
                        when (key) {
                            "type" -> flatten(schema, value).map { it.fieldName to it }
                            "all_of" -> (value as IonList).flatMap { t -> flatten(schema, t).map { it.fieldName to it } }
                            "any_of", "one_of", "ordered_elements" -> listOf(key to (value as IonList).mapTo(ion.newEmptyList()) { flatten(schema, it).elideSingleTypeConstraint() })
                            "element", "not", "field_names" -> listOf(key to flatten(schema, value).elideSingleTypeConstraint())
                            "annotations" -> if (value is IonList) {
                                listOf(key to value)
                            } else {
                                listOf(key to flatten(schema, value).elideSingleTypeConstraint())
                            }
                            "fields" -> {
                                val fields = (value as IonStruct).map {
                                    it.fieldName to flatten(schema, it).elideSingleTypeConstraint()
                                }
                                val newFieldsStruct = ion.newEmptyStruct().apply {
                                    fields.forEach { (f, t) -> add(f, t.clone()) }
                                }
                                listOf(key to newFieldsStruct)
                            }
                            else -> listOf(key to value)
                        }
                    }
                    .filter { (fieldName, _) -> fieldName != "name" }
                    .map { (k, v) -> k to if (v.hasTypeAnnotation("type")) v.clone().apply { clearTypeAnnotations() } else v }

                val newStruct = typeIsl.cloneAndRetain("name")
                newFields.forEach { newStruct.add(it.first, it.second.clone()) }
                newStruct
            }
            is IonSymbol -> {
                val name = typeIsl.stringValue()
                if (name in BASE_TYPES || "\$$name" in BASE_TYPES) {
                    // Another base case
                    ion.newEmptyStruct().apply {
                        add("type", typeIsl.clone())
                    }
                } else {
                    val type = schema.getType(name)
                    if (type != null) {
                        if (schema.getDeclaredType(name) != null) {
                            flatten(schema, type.isl)
                        } else {
                            val import = schema.getImports().asSequence().first { it.getType(name) != null }
                            val importedSchema = iss.loadSchema(import.id)
                            flatten(importedSchema, type.isl)
                        }
                    } else {
                        TODO("Unreachable maybe?")
                    }
                }
            }
            else -> TODO("Unreachable")
        }.apply {
            if (stripNameAndTypeAnnotation) {
                remove("name")
                removeTypeAnnotation("type")
            }
            if (typeName != null && !containsKey("name")) {
                add("__name__").newSymbol(typeName)
            }
        }.let {
            simplify(it)
        }.let {
            handleNullOrAnnotation(typeIsl, it)
        }.let {
            it.map { it.fieldName to it.clone() }
                .sortedBy { (k, _) -> if (k == "name") "__name__" else k }
                .fold(it.cloneAndRetain()) { struct, (key, value) -> struct.apply { add(key, value) } }
        }
    }

    private fun IonStruct.elideSingleTypeConstraint(): IonValue {
        return if (size() == 1 && containsKey("type")) {
            this["type"]!!.clone().apply {
                this@elideSingleTypeConstraint.typeAnnotations
                    .forEach { addTypeAnnotation(it) }
            }
        } else {
            this
        }
    }

    private fun handleNullOrAnnotation(old: IonValue, new: IonStruct): IonStruct {
        return if (old.hasTypeAnnotation("\$null_or")) {
            ion.newEmptyStruct().apply {
                add("any_of").newEmptyList().apply {
                    add().newSymbol("\$null")
                    add(new.elideSingleTypeConstraint())
                }
            }
        } else {
            new
        }
    }

    fun simplify(struct: IonStruct): IonStruct {
        val (possibleTypes, canBeNull) = determineIonTypeFromConstraints(struct)

        if (possibleTypes.isEmpty() || struct.any { it.fieldName == "type" && it.maybe<IonSymbol>()?.stringValue() == "nothing" }) {
            return struct.cloneAndRetain("name").apply {
                add("type").newSymbol("nothing")
            }
        }

        return struct.cloneAndRetain().apply {
            struct.forEach {
                if (it is IonStruct) {
                    add(it.fieldName, it.elideSingleTypeConstraint().cloneIfParented())
                } else {
                    add(it.fieldName, it.cloneIfParented())
                }
            }
        }
    }

    private fun mergeFields(fieldsA: IonStruct, fieldsB: IonStruct): IonStruct? {
        val fieldNames = fieldsA.map { it.fieldName }.toSet() + fieldsB.map { it.fieldName }.toSet()

        val newFields = ion.newEmptyStruct()

        fieldNames.forEach {
        }

        return newFields
    }

    private fun determineIonTypeFromConstraints(struct: IonStruct): Pair<Set<IonType>, Boolean> {
        var canBeNull = true
        val possibleTypes = mutableSetOf(*IonType.values())

        struct.forEach {
            when (it.fieldName) {
                "timestamp_offset", "timestamp_precision" -> {
                    possibleTypes.retain(IonType.TIMESTAMP)
                    canBeNull = false
                }
                "precision", "exponent" -> {
                    possibleTypes.retain(IonType.DECIMAL)
                    canBeNull = false
                }
                "codepoint_length", "utf8_byte_length", "regex" -> {
                    possibleTypes.retain(IonType.SYMBOL, IonType.STRING)
                    canBeNull = false
                }
                "byte_length" -> {
                    possibleTypes.retain(IonType.CLOB, IonType.BLOB)
                    canBeNull = false
                }
                "ieee754_float" -> {
                    possibleTypes.retain(IonType.FLOAT)
                    canBeNull = false
                }
                "fields", "field_names" -> {
                    possibleTypes.retain(IonType.STRUCT)
                    canBeNull = false
                }
                "container_length", "element", "contains" -> {
                    possibleTypes.retain(IonType.DATAGRAM, IonType.STRUCT, IonType.LIST, IonType.SEXP)
                    canBeNull = false
                }
                "ordered_elements" -> {
                    possibleTypes.retain(IonType.DATAGRAM, IonType.LIST, IonType.SEXP)
                    canBeNull = false
                }
                "annotations" -> {
                    possibleTypes.remove(IonType.DATAGRAM)
                }
                "valid_values" -> {
                    // TODO:
                }
                else -> {}
            }
        }
        return possibleTypes.toSet() to canBeNull
    }

    private fun <E> MutableSet<E>.retain(vararg elements: E) = retainAll(elements)

    private fun IonValue.cloneIfParented() = cloneIf { it.container != null }

    private inline fun IonValue.cloneIf(predicate: (IonValue) -> Boolean) = if (predicate(this)) clone() else this

    private inline fun <reified T : Any> Any.maybe() = this as? T
}
