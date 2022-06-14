package com.amazon.ionschema.model.ksp

import com.amazon.ionschema.model.codegen.Builder
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.kspDependencies
import com.squareup.kotlinpoet.ksp.originatingKSFiles
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class BuilderProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val annotatedSymbols = resolver.getSymbolsWithAnnotation(Builder::class.qualifiedName!!, inDepth = false).toList()
        logger.warn("Found ${annotatedSymbols.size} annotated symbols")

        annotatedSymbols.map {
            when {
                it is KSClassDeclaration && it.primaryConstructor != null -> {
                    logger.warn("Generating code for symbol: ${it.simpleName.getShortName()}", it)
                    process(CodegenContext(it, environment = environment, resolver = resolver))
                }
                it is KSFunctionDeclaration && it.isConstructor() && it.parentDeclaration is KSClassDeclaration -> {
                    val parent = it.parentDeclaration as KSClassDeclaration
                    logger.warn("Generating code for symbol: ${parent.simpleName.getShortName()}", it)
                    process(CodegenContext(parent, it, environment, resolver))
                }
                else -> {
                    logger.error("@GeneratedBuilder can't be applied to $it: must be a Kotlin class or constructor", it)
                }
            }
        }
        return emptyList()
    }

    fun process(ctx: CodegenContext) {
        val fileSpec = FileSpec.builder(ctx.targetPackage, ctx.targetClassName.mangle("Builder").simpleName).apply {
            addType(createJavaBuilderInterface(ctx))
            addType(createDslInterface(ctx))
            addType(createBuilderImpl(ctx))
            generateExtensionFunctions(ctx).forEach { addFunction(it) }
        }.build()

        val dependencies = fileSpec.kspDependencies(aggregating = false, fileSpec.originatingKSFiles())
        val file = codeGenerator.createNewFile(dependencies, fileSpec.packageName, fileSpec.name)
        // Don't use writeTo(file) because that tries to handle directories under the hood
        OutputStreamWriter(file, StandardCharsets.UTF_8)
            .use { fileSpec.writeTo(it) }
    }
}
