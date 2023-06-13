package com.amazon.ionschema.cli.generator

import com.amazon.ionschema.cli.util.toCamelCase
import com.amazon.ionschema.cli.util.toPascalCase
import com.amazon.ionschema.cli.util.toScreamingSnakeCase
import com.amazon.ionschema.cli.util.toSnakeCase
import java.nio.file.Path


/**
 * ## Converter
 * Need to resolve type references
 * Everything is an ID mapped to a definition, represented as a tree
 *
 * ## Generator
 * - Makes a first pass to create the ID to fully qualified names cache
 * - Makes a second pass to generate the code
 */
open class KotlinGenerator(
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
            is EntityDefinition.NativeType -> {
                { node.selfType.qualifiedNames["kotlin"]!! }
            }
            is EntityDefinition.ParameterizedType -> {
                { "${getResolvedId(node.selfType.type)}<${node.selfType.parameters.joinToString { generateCodeForMaybeId(it) }}>" }
            }
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

    protected fun generateCodeForMaybeId(type_ref: MaybeId): String {
        val (inner_ref, optional, nullable) = type_ref
        val inner = getResolvedId(inner_ref)
        return if (optional && nullable) {
            "java.util.Optional<$inner?>"
        } else if (optional) {
            "java.util.Optional<$inner>"
        } else if (nullable) {
            "$inner?"
        } else {
            inner
        }
    }

    private fun generateType(id: Id, typeDef: EntityDefinition, nested: String): String {
        val name = id.name.toPascalCase()
        return when(typeDef) {
            is EntityDefinition.EnumType -> generateEnumType(name, typeDef, nested)
            is EntityDefinition.SumType -> generateSealedTypes(name, typeDef, nested)
            is EntityDefinition.RecordType -> generateDataClass(name, typeDef.components, nested)
            is EntityDefinition.TupleType -> generateDataClass(name, typeDef.components.mapIndexed { idx, it -> "component$idx" to it  }.toMap(), nested)
            // Output nothing for anything else since we resolve this as type references
            is EntityDefinition.NativeType -> ""
            is EntityDefinition.ParameterizedType -> ""
        }
    }

    protected open fun generateDataClass(name: String, fields: Map<String, MaybeId>, nested: String): String {
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

    protected open fun generateSealedTypes(name: String, typeDef: EntityDefinition.SumType, nested: String): String {
        val visibility = if (options.publicTypes) "public" else "internal"
        val (variants) = typeDef
        val interfaceOrAbstractClass = if (options.kotlinVersion.isAtLeast(1, 6)) "interface" else "class"
        val maybeInvokeConstructor = if (options.kotlinVersion.isAtLeast(1, 6)) "" else "()"
        val variantClassType = if (options.useInlineWrappersForSealedTypes) {
            if (options.kotlinVersion.isAtLeast(1, 5)) "value" else "inline"
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

    protected open fun generateEnumType(name: String, typeDef: EntityDefinition.EnumType, nested: String): String {
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
        |            return values().first { it.symbolText == symbolText }
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

    protected open fun generateFile(node: Node) {
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

    protected open fun generateClass(node: Node): String {
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
//        if (content.isNotBlank()) {
//            // Write a "generated.kt" file
//        }

        return files
    }
}