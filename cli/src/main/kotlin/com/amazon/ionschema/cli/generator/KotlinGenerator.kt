package com.amazon.ionschema.cli.generator

import com.amazon.ionschema.cli.util.toCamelCase
import com.amazon.ionschema.cli.util.toPascalCase
import com.amazon.ionschema.cli.util.toScreamingSnakeCase
import com.amazon.ionschema.cli.util.toSnakeCase
import com.amazon.ionschema.model.Constraint
import com.amazon.ionschema.model.DiscreteIntRange
import com.amazon.ionschema.model.ExperimentalIonSchemaModel
import java.nio.file.Path


/**
 * ## Generator
 * - Makes a first pass to create the ID to fully qualified names cache
 * - Makes a second pass to generate the code
 *
 * ## Custom bindings
 *
 * Should implement the functions in this interface, but does not need to implement this exactly.
 * ```
 * interface Serde<T> {
 *     fun writeTo(ionWriter: IonWriter, value: T)
 *     fun readFrom(ionReader: IonReader): T
 * }
 * ```
 *
 */
class KotlinGenerator(
    private val typeDomain: TypeDomain,
    private val options: Options,
) {
    data class Options(
        val outputDir: Path,
        val rootPackage: String,
        val kotlinVersion: KotlinVersion,
        /** Whether the generated types should be public rather than internal */
        val publicTypes: Boolean = true,
        /** Whether the fields should be immutable */
        val immutableFields: Boolean = true,
        val useInlineWrappersForSealedTypes: Boolean = true,
    )

    data class KotlinFile(val rootPackage: String, val fileName: String, val content: String)

    // A is parent of B
    // When
    // A.self is None, and B.self is some, A is a package and B is a file/class
    // A.self is Some, and B.self is None, A is a file/class B is a package.
    // A.self is None, and B.self is None, A and B are packages.
    // A.self is Some, and B.self is Some, A is a class, and B is a nested class

    private val files: MutableList<KotlinFile> = mutableListOf()

    private val resolvedIds = mutableMapOf<Id, () -> String>()

    init {
        // Populate the id cache
        resolvedIds[Id()] = { options.rootPackage.let { if (it.isBlank()) "" else "$it." } }
        typeDomain.entities.forEach { cacheIdsToQualifiedNames(it, resolvedIds) }

        println("## Prefixes")
        resolvedIds.forEach { (id, fqn) -> println("${id.parts} -> ${fqn.invoke()}") }
    }

    private fun cacheIdsToQualifiedNames(node: Node, resolvedIds: MutableMap<Id, () -> String>) {
        val resolvedId = when (node.selfType) {
            null -> {
                // Package name
                { getResolvedId(node.id.parentId()) + node.id.parts.joinToString(".") { it.toSnakeCase() } }
            }
            is EntityDefinition.NativeType -> { { node.selfType.qualifiedNames["kotlin"]!!.fullyQualifiedTypeName } }
            is EntityDefinition.ParameterizedType -> {
                { "${getResolvedId(node.selfType.type)}<${node.selfType.parameters.joinToString { generateCodeForMaybeId(it) }}>" }
            }
            is EntityDefinition.CollectionType -> {
                { "${getResolvedId(node.selfType.type)}<${generateCodeForMaybeId(node.selfType.item)}>" }
            }
            is EntityDefinition.AssociationType -> {
                { "${getResolvedId(node.selfType.type)}<${generateCodeForMaybeId(node.selfType.key)},${generateCodeForMaybeId(node.selfType.value)}>" }
            }
            is EntityDefinition.ConstrainedScalarType -> { { getResolvedId(node.selfType.scalarType) } }
            else -> {
                // Class Name
                { resolvedIds[node.id.parentId()]!!() + "." + node.id.name.toPascalCase() }
            }
        }
        resolvedIds[node.id] = resolvedId
        node.children.forEach { cacheIdsToQualifiedNames(it, resolvedIds) }
    }

    private fun getResolvedId(id: Id): String {
        return resolvedIds[id]?.invoke() ?: throw IllegalStateException("No resolved Id for id: ${id.toIon()}")
    }

    private fun generateCodeForMaybeId(typeRef: MaybeId): String {
        // TODO: Consider supporting optional and nullable separately.
        val (innerRef, optional, nullable) = typeRef
        val inner = getResolvedId(innerRef)
        return if (optional || nullable) "$inner?" else inner
    }

    private fun generateType(id: Id, typeDef: EntityDefinition, nested: String): String {
        val name = id.name.toPascalCase()
        return when(typeDef) {
            is EntityDefinition.EnumType -> generateEnumType(name, typeDef, nested)
            is EntityDefinition.SumType -> generateSealedTypes(name, typeDef, nested)
            is EntityDefinition.RecordType -> generateDataClassForRecordType(name, typeDef, nested)
            is EntityDefinition.TupleType -> generateTuple(name, typeDef.components.mapIndexed { idx, it -> "component$idx" to it  }.toMap(), nested)
            // Output nothing for constrained scalar types. Their constraints are added to init blocks.
            is EntityDefinition.ConstrainedScalarType -> ""
            // Output nothing for anything else since we resolve this as type references
            is EntityDefinition.NativeType -> ""
            is EntityDefinition.ParameterizedType -> ""
            is EntityDefinition.CollectionType -> ""
            is EntityDefinition.AssociationType -> ""
        }
    }

    private fun DiscreteIntRange.toRHS() = when {
        start == endInclusive -> "== $start"
        start == null -> "<= $endInclusive"
        endInclusive == null -> ">= $start"
        else -> "in $start..$endInclusive"
    }

    private fun generateDataClassForRecordType(name: String, entityDefinition: EntityDefinition.RecordType, nested: String): String {
        val fields = entityDefinition.components.entries
        val visibility = if (options.publicTypes) "public" else "internal"
        val mut = if (options.immutableFields) "val" else "var"
        val generatedFields = fields.joinToString("\n") { (fName, ref) ->
            "|$mut ${fName.toCamelCase()}: ${generateCodeForMaybeId(ref)},"
        }


        return """
        |${visibility} data class ${name}(
        |    ${generatedFields.indentLines()}
        |) {
        |    ${generateDataClassInitBlock(entityDefinition.components).indentLines()}
        |    
        |    ${generateDataClassWriteToFunction(entityDefinition.components).indentLines()}
        |    
        |    companion object {
        |        @JvmStatic
        |        fun builder() = Builder()
        |    
        |        ${generateDataClassReadFromFunction(name, entityDefinition.components).indentLines(2)}
        |    }
        |    
        |    ${generateBuilder(name, entityDefinition.components).indentLines()}
        |    
        |    ${nested.lines().joinToString("\n|    ")}
        |}
        """.trimMargin()
    }

    @OptIn(ExperimentalIonSchemaModel::class)
    private fun generateDataClassInitBlock(fields: Map<String, MaybeId>): String {
        val invariantCheckerCode = mutableListOf<String>()
        fields.forEach { (fName, ref) ->
            val fieldName = fName.toCamelCase()
            val fieldConstraints = typeDomain[ref.id]?.selfType?.typeDefinition?.constraints ?: emptySet()
            val fieldConstraintsCode = fieldConstraints.joinToString("\n") {
                when (it) {
                    is Constraint.Utf8ByteLength -> {
                        // TODO: Handle not just String, but also IonString, IonSymbol
                        val rhs = it.range.toRHS()
                        """
                        val utf8ByteLength = it.toByteArray(Charsets.UTF_8).size
                        require(utf8ByteLength $rhs) { "Value '$fieldName' is ${'$'}utf8ByteLength bytes in UTF-8; must be $rhs" }
                        """.trimIndent()
                    }
                    is Constraint.CodepointLength -> {
                        // TODO: Handle not just String, but also IonString, IonSymbol
                        val rhs = it.range.toRHS()
                        """
                        val codepointLength = it.codePointCount(0, $fieldName.length)
                        require(codepointLength $rhs) { "Value '$fieldName' is ${'$'}codepointLength codepoints; must be $rhs" }
                        """.trimIndent()
                    }
                    is Constraint.Regex -> {
                        // TODO: Move regex construction to companion object
                        // TODO: String escaping for pattern
                        val m = if (it.multiline) "kotlin.text.RegexOption.MULTILINE," else ""
                        val i = if (it.caseInsensitive) "kotlin.text.RegexOption.IGNORE_CASE," else ""
                        """
                        val regex = kotlin.text.Regex("${it.pattern}", setOf($m $i))
                        require(regex.matches(it)) { "Value '$fieldName' does not match regular expression: ${it.pattern}" }
                        """.trimIndent()
                    }
                    else -> ""
                }
            }.trim()
            val maybeNullSafe = if (ref.nullable || ref.optional) "?" else ""
            if (fieldConstraintsCode.isNotBlank()) {
                invariantCheckerCode += """
                |$fieldName$maybeNullSafe.let {
                |    ${fieldConstraintsCode.lines().joinToString("\n|    ")}
                |}
                """
            }
        }
        return if (invariantCheckerCode.isEmpty()) {
            ""
        } else {
            """
            |init {
            |    ${invariantCheckerCode.indentLines()}
            |}
            """
        }
    }


    private fun generateMemberWriter(ref: MaybeId, variableName: String): String {
        val nullable = ref.nullable || ref.optional
        val typeId = (typeDomain[ref.id]?.selfType as? EntityDefinition.ConstrainedScalarType)?.scalarType ?: ref.id
        val actualType = typeDomain[typeId]!!.selfType
        val nonNullWriteFn = if ((actualType as? EntityDefinition.NativeType)?.qualifiedNames?.get("kotlin") != null) {
            val t = actualType.qualifiedNames.get("kotlin")!!
            "${t.fullyQualifiedSerdeObject}.writeTo(ionWriter, $variableName)"
        } else if (actualType is EntityDefinition.CollectionType) {
            val elementWriterFn = generateMemberWriter(actualType.item, "it")
            """
            ionWriter.stepIn(com.amazon.ion.IonType.LIST)
            try {
                $variableName.forEach { 
                    $elementWriterFn 
                }
            } finally {
                ionWriter.stepOut()
            }
            """.trimIndent()
        } else {
            "$variableName.writeTo(ionWriter)"
        }

        return if (nullable) {
            """
            if ($variableName == null) ionWriter.writeNull() else {
                $nonNullWriteFn
            }
            """.trimIndent()
        } else {
            nonNullWriteFn
        }
    }


    private fun generateDataClassWriteToFunction(fields: Map<String, MaybeId>): String {
        val fieldWriterCode = mutableListOf<String>()
        fields.forEach { (fName, ref) ->
            val fieldName = fName.toCamelCase()
            fieldWriterCode +=
                """
                |ionWriter.setFieldName("$fName")
                |${generateMemberWriter(ref, fieldName)}
                """
        }

        return """
        |fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        |    ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        |    try {
        |        ${fieldWriterCode.indentLines(2)}
        |    } finally {
        |        ionWriter.stepOut()
        |    }
        |}
        """
    }


    private fun generateMemberReader(ref: MaybeId): String {
        val nullable = ref.nullable || ref.optional
        val typeId = (typeDomain[ref.id]?.selfType as? EntityDefinition.ConstrainedScalarType)?.scalarType ?: ref.id
        val actualType = typeDomain[typeId]!!.selfType
        val nonNullReadFn = if ((actualType as? EntityDefinition.NativeType)?.qualifiedNames?.get("kotlin") != null) {
            val t = actualType.qualifiedNames["kotlin"]!!
            "${t.fullyQualifiedSerdeObject}.readFrom(ionReader)"
        } else if (actualType is EntityDefinition.CollectionType) {
            val elementReaderFn = generateMemberReader(actualType.item)
            // TODO: Add support for sets or other collection types
            """
            |
            |mutableListOf<${generateCodeForMaybeId(actualType.item)}>().apply {
            |    check(ionReader.type == com.amazon.ion.IonType.LIST)
            |    ionReader.stepIn()
            |    try {
            |        while (ionReader.next() != null) {
            |            add(${elementReaderFn})
            |        }
            |    } finally {
            |        ionReader.stepOut()
            |    }
            |}.toList()
            |
            """.trimMargin()
        } else {
            "${getResolvedId(typeId)}.readFrom(ionReader)"
        }

        // TODO: Support for default values, perhaps?
        return if (!nullable) nonNullReadFn else "if (ionReader.isNullValue) null else $nonNullReadFn"
    }


    private fun generateDataClassReadFromFunction(name: String, fields: Map<String, MaybeId>): String {
        val fieldReaderCode = mutableListOf<String>()
        fields.forEach { (fName, ref) ->
            fieldReaderCode +=
                """
                |"$fName" -> builder.with${fName.toPascalCase()}(${generateMemberReader(ref)})
                """
        }

        // TODO: Ignore or fail on unknown fields?
        return """
        |@JvmStatic
        |fun readFrom(ionReader: com.amazon.ion.IonReader): $name {
        |    val builder = Builder()
        |    // TODO: Set builder with default values
        |    ionReader.stepIn()
        |    try {
        |        while (ionReader.next() != null) {
        |            when (ionReader.fieldName) {
        |                ${fieldReaderCode.indentLines(4)}
        |            }
        |        }
        |    } finally {
        |        ionReader.stepOut()
        |    }
        |    return builder.build()
        |}
        """
    }


    fun generateBuilder(name: String, fields: Map<String, MaybeId>): String {
        val generatedBuilderVars = mutableListOf<String>()
        val generatedBuilderFuns = mutableListOf<String>()
        val generatedBuildCtorArgs = mutableListOf<String>()
        fields.forEach { (fName, ref) ->
            val fieldName = fName.toCamelCase()

            generatedBuilderVars += """private var $fieldName: ${getResolvedId(ref.id)}? = null"""
            generatedBuilderFuns += """fun with${fName.toPascalCase()}($fieldName: ${generateCodeForMaybeId(ref)}) = apply { this.$fieldName = $fieldName }"""

            val handleNull = if (ref.nullable || ref.optional) { "" } else { """ ?: throw IllegalArgumentException("${fName.toCamelCase()} cannot be null")""" }
            generatedBuildCtorArgs += """${fName.toCamelCase()} = this.${fName.toCamelCase()}$handleNull,"""
        }

        return """
        |class Builder {
        |    ${generatedBuilderVars.joinWithMargin("""
        |    """)}
        |
        |    ${generatedBuilderFuns.joinWithMargin("""
        |    """)}
        |
        |    fun build() = $name(
        |        ${generatedBuildCtorArgs.joinWithMargin("""
        |        """)}
        |    )
        |}
        """
    }


    private fun List<String>.joinWithMargin(margin: String): String = flatMap { it.lines() }.joinToString("") { margin + it }
    private fun String.indentLines(i: Int = 1): String = replaceIndentByMargin("|" + ("    ".repeat(i))).trimStart { it == '|' || it.isWhitespace() }
    private fun List<String>.indentLines(i: Int = 1): String = joinToString("\n") { it.replaceIndentByMargin("|" + ("    ".repeat(i))) }.trimStart { it == '|' || it.isWhitespace() }

    private fun generateTuple(name: String, fields: Map<String, MaybeId>, nested: String): String {
        val visibility = if (options.publicTypes) "public" else "internal"
        val mut = if (options.immutableFields) "val" else "var"
        val generatedFields = fields.entries.joinToString("\n") { (fName, ref) ->
            "    $mut ${fName.toCamelCase()}: ${generateCodeForMaybeId(ref)},"
        }

        return """
        |${visibility} data class ${name}(
        |${generatedFields}
        |) {
        |    ${nested.lines().joinToString("\n|    ")}
        |}
        """.trimMargin()
    }


    private fun generateSealedTypes(name: String, typeDef: EntityDefinition.SumType, nested: String): String {
        val visibility = if (options.publicTypes) "public" else "internal"
        val (variants) = typeDef
        val interfaceOrAbstractClass = if (options.kotlinVersion.isAtLeast(1, 6)) "interface" else "class"
        val maybeInvokeConstructor = if (options.kotlinVersion.isAtLeast(1, 6)) "" else "()"
        val variantClassType = if (options.useInlineWrappersForSealedTypes && options.kotlinVersion.isAtLeast(1, 6)) {
            // Value and inline classes can't extend an abstract class. However, they can implement a sealed interface,
            // so even though value classes are available starting in Kotlin 1.5, our only option before Kotlin 1.6 is
            // non-inline classes.
            "@JvmInline value"
        } else {
            "data"
        }
        val enumVariants = variants.map { (key, typeRef) ->
            val r = generateCodeForMaybeId(typeRef)
            val variantName = key.toPascalCase()
            "$visibility $variantClassType class ${variantName}(val value: ${r}): ${name}${maybeInvokeConstructor}"
        }.joinToString("\n")

        return """
        |${visibility} sealed $interfaceOrAbstractClass $name {
        |    ${enumVariants.lines().joinToString("\n|    ")}
        |    ${nested.lines().joinToString("\n|    ")}
        |}
        """.trimMargin()
    }

    private fun generateEnumType(name: String, typeDef: EntityDefinition.EnumType, nested: String): String {
        val visibility = if (options.publicTypes) "public" else "internal"
        val enumValues = typeDef.values.joinToString { "${it.toScreamingSnakeCase()}(\"$it\")"  }
        return """
        |${visibility} enum class ${name}(val symbolText: String) {
        |    ${enumValues};
        |    
        |    companion object {
        |        @JvmStatic
        |        fun readFrom(ionReader: com.amazon.ion.IonReader): $name {
        |            if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
        |                throw com.amazon.ion.IonException ("While attempting to read a $name, expected a symbol but found a ${'$'}{ionReader.type}")
        |            }
        |            val symbolText = ionReader.stringValue()
        |            return values().firstOrNull { it.symbolText == symbolText }
        |                ?: throw IllegalArgumentException("Unknown $name: '${'$'}symbolText'")
        |        }
        |    }
        |    
        |    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        |        ionWriter.writeSymbol(this.symbolText)
        |    }
        |    
        |    ${nested.lines().joinToString("\n|    ")}
        |}
        """.trimMargin()
    }


    private fun generatePackage(node: Node) {
        check(node.selfType is Nothing?)

        for (child in node.children) {
            if (child.selfType != null)  {
                generateFile(child)
            } else {
                generatePackage(child)
            }
        }
    }

    private fun generateFile(node: Node) {
        val thisPackage = getResolvedId(node.id.parentId())
        val fileName = node.id.name.toPascalCase() + ".kt"

        val content = """
        |package $thisPackage
        |
        |${generateClass(node)}
        |
        """.trimMargin()

        files.add(KotlinFile(thisPackage, fileName, content))
    }

    private fun generateClass(node: Node): String {
        checkNotNull(node.selfType)
        val docs = if (!node.docs.isNullOrEmpty()) {
            val docContent = node.docs.trim().split('\n').joinToString("\n") { " * $it" }
            "/**\n${docContent}\n */"
        } else { "" }

        val nested = node.children.joinToString("\n") { generateClass(it) }
        val generatedType = generateType(node.id, node.selfType, nested)
        return "${docs}\n${generatedType}".lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    fun generateTypeDomain(): List<KotlinFile> {
//        println("Generating from...")
//        fun Iterable<Node>.flatten(): List<Node> = flatMap { it.children.flatten() + it }
//        println(typeDomain.entities.flatten().joinToString("\n") { it.toIon().toString() })
//        println()

//        val content = StringBuilder()

        for (child in typeDomain.entities) {
            if (child.selfType != null) {
                // content.append(generateClass(child))
            } else {
                generatePackage(child)
            }
        }
        generateUtilFile()
//        if (content.isNotBlank()) {
//            // Write a "generated.kt" file
//        }

        return files
    }


    private fun generateUtilFile() {
        val thisPackage = getResolvedId(Id()) + "__internal__"
        val fileName = "generatedUtil.kt"

        val content = """
        |package $thisPackage
        |
        |import com.amazon.ion.IntegerSize
        |import com.amazon.ion.IonReader
        |import com.amazon.ion.IonType
        |import com.amazon.ion.IonWriter
        |import com.amazon.ion.Timestamp
        |import java.math.BigDecimal
        |import java.math.BigInteger
        |
        |interface Serde<T> {
        |    fun writeTo(ionWriter: IonWriter, value: T)
        |    fun readFrom(ionReader: IonReader): T
        |}
        |
        |interface CollectionSerde<T: Iterable<E>, E> {
        |    fun writeTo(ionWriter: IonWriter, value: T, itemWriteFn: (E) -> Unit)
        |    fun readFrom(ionReader: IonReader, itemReadFn: () -> E): T
        |}
        |
        |object StringSerde: Serde<String> {
        |    override fun writeTo(ionWriter: IonWriter, value: String) = ionWriter.writeString(value)
        |    override fun readFrom(ionReader: IonReader): String {
        |        if(ionReader.type != com.amazon.ion.IonType.STRING) {
        |            throw com.amazon.ion.IonException ("Expected a string but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.stringValue()
        |    }
        |}
        |
        |object SymbolSerde: Serde<String> {
        |    override fun writeTo(ionWriter: IonWriter, value: String) = ionWriter.writeSymbol(value)
        |    override fun readFrom(ionReader: IonReader): String {
        |        if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
        |            throw com.amazon.ion.IonException ("Expected a symbol but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.stringValue()
        |    }
        |}
        |
        |object DecimalSerde: Serde<BigDecimal> {
        |    override fun writeTo(ionWriter: IonWriter, value: BigDecimal) = ionWriter.writeDecimal(value)
        |    override fun readFrom(ionReader: IonReader): BigDecimal {
        |        if(ionReader.type != com.amazon.ion.IonType.DECIMAL) {
        |            throw com.amazon.ion.IonException ("Expected a decimal but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.bigDecimalValue()
        |    }
        |}
        |
        |object IntSerde: Serde<BigInteger> {
        |    override fun writeTo(ionWriter: IonWriter, value: BigInteger) = ionWriter.writeInt(value)
        |    override fun readFrom(ionReader: IonReader): BigInteger {
        |        if(ionReader.type != com.amazon.ion.IonType.INT) {
        |            throw com.amazon.ion.IonException ("Expected an int but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.bigIntegerValue()
        |    }
        |}
        |
        |object BoolSerde: Serde<Boolean> {
        |    override fun writeTo(ionWriter: IonWriter, value: Boolean) = ionWriter.writeBool(value)
        |    override fun readFrom(ionReader: IonReader): Boolean {
        |        if(ionReader.type != com.amazon.ion.IonType.BOOL) {
        |            throw com.amazon.ion.IonException ("Expected a BOOL but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.booleanValue()
        |    }
        |}
        |object FloatSerde: Serde<Double> {
        |    override fun writeTo(ionWriter: IonWriter, value: Double) = ionWriter.writeFloat(value)
        |    override fun readFrom(ionReader: IonReader): Double {
        |        if(ionReader.type != com.amazon.ion.IonType.FLOAT) {
        |            throw com.amazon.ion.IonException ("Expected a FLOAT but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.doubleValue()
        |    }
        |}
        |object TimestampSerde: Serde<Timestamp> {
        |    override fun writeTo(ionWriter: IonWriter, value: Timestamp) = ionWriter.writeTimestamp(value)
        |    override fun readFrom(ionReader: IonReader): Timestamp {
        |        if(ionReader.type != com.amazon.ion.IonType.TIMESTAMP) {
        |            throw com.amazon.ion.IonException ("Expected a TIMESTAMP but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.timestampValue()
        |    }
        |}
        |object BlobSerde: Serde<ByteArray> {
        |    override fun writeTo(ionWriter: IonWriter, value: ByteArray) = ionWriter.writeBlob(value)
        |    override fun readFrom(ionReader: IonReader): ByteArray {
        |        if(ionReader.type != com.amazon.ion.IonType.BLOB) {
        |            throw com.amazon.ion.IonException ("Expected a BLOB but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.newBytes()
        |    }
        |}
        |object ClobSerde: Serde<ByteArray> {
        |    override fun writeTo(ionWriter: IonWriter, value: ByteArray) = ionWriter.writeClob(value)
        |    override fun readFrom(ionReader: IonReader): ByteArray {
        |        if(ionReader.type != com.amazon.ion.IonType.CLOB) {
        |            throw com.amazon.ion.IonException ("Expected a CLOB but found a ${'$'}{ionReader.type}")
        |        }
        |        return ionReader.newBytes()
        |    }
        |}
        |object NumberSerde: Serde<Number> {
        |    override fun writeTo(ionWriter: IonWriter, value: Number) = when (value) {
        |        is Double -> ionWriter.writeFloat(value)
        |        is Float -> ionWriter.writeFloat(value.toDouble())
        |        is Byte -> ionWriter.writeInt(value.toLong())
        |        is Short -> ionWriter.writeInt(value.toLong())
        |        is Int -> ionWriter.writeInt(value.toLong())
        |        is Long -> ionWriter.writeInt(value)
        |        is BigInteger -> ionWriter.writeInt(value)
        |        is BigDecimal -> ionWriter.writeDecimal(value)
        |        // TODO: Something less fragile than this
        |        else -> ionWriter.writeDecimal(BigDecimal(value.toString()))
        |    }
        |        
        |    override fun readFrom(ionReader: IonReader): Number {
        |        return when (ionReader.type) {
        |            IonType.INT -> {
        |                when (ionReader.integerSize!!) {
        |                    IntegerSize.BIG_INTEGER -> ionReader.bigIntegerValue()
        |                    IntegerSize.INT -> ionReader.intValue()
        |                    IntegerSize.LONG -> ionReader.longValue()
        |                }
        |            }
        |            IonType.FLOAT -> ionReader.doubleValue()
        |            IonType.DECIMAL -> ionReader.decimalValue()
        |            else -> throw com.amazon.ion.IonException ("Expected a number but found a ${'$'}{ionReader.type}")
        |        }
        |    }
        |}
        |
        """.trimMargin()

        files.add(KotlinFile(thisPackage, fileName, content))
    }
}