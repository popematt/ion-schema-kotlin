package com.amazon.ionschema.model.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

data class CodegenContext(val ksClass: KSClassDeclaration, val ctor: KSFunctionDeclaration = ksClass.primaryConstructor!!, val environment: SymbolProcessorEnvironment, val resolver: Resolver) {
    val sourceFile get() = ksClass.containingFile!!
    val targetClassName = ksClass.toClassName()
    val targetPackage get() = targetClassName.packageName
    val dslBuilderClassName = targetClassName.mangle("Dsl")
    val javaBuilderClassName = targetClassName.mangle("Builder")
    val implClassName = targetClassName.mangle("Impl")
}

fun ClassName.mangle(suffix: String = "") = ClassName(packageName, simpleNames.joinToString("_") + suffix)
