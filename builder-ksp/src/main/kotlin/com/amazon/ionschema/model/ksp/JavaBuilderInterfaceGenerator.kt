package com.amazon.ionschema.model.ksp

import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

fun createJavaBuilderInterface(ctx: CodegenContext): TypeSpec {

    val specBuilder = TypeSpec.interfaceBuilder(ctx.javaBuilderClassName)
        .addOriginatingKSFile(ctx.sourceFile)
        .addType(TypeSpec.companionObjectBuilder()
            .addFunction(
                FunSpec.builder("create")
                    .addAnnotation(JvmStatic::class)
                    .returns(ctx.javaBuilderClassName)
                    .addStatement("return %T()", ctx.implClassName)
                    .build())
            .addFunction(
                FunSpec.builder("from")
                    .addAnnotation(JvmStatic::class)
                    .returns(ctx.javaBuilderClassName)
                    .addParameter("copyFrom", ctx.targetClassName)
                    .addStatement("return %T(copyFrom)", ctx.implClassName)
                    .build())
            .build())
        .addFunction(
            FunSpec.builder("build")
                .returns(ctx.targetClassName)
                .addModifiers(KModifier.ABSTRACT)
                .build())

    for (parameter in ctx.ctor.parameters) {
        specBuilder.addFunction(createAbstractWither(parameter, ctx.javaBuilderClassName))
    }

    return specBuilder.build()
}

private fun createAbstractWither(parameter: KSValueParameter, thisType: ClassName): FunSpec {
    val propertyName = parameter.name!!.getShortName()
    
    return FunSpec.builder("with${propertyName.capitalizeFirstLetter()}")
        .addParameter(propertyName, parameter.type.typeName)
        .returns(thisType)
        .addModifiers(KModifier.ABSTRACT)
        .build()
}

private fun String.capitalizeFirstLetter(): String {
    return this.replaceFirstChar { it.uppercaseChar() }
}

val KSTypeReference.typeName get() = try {
    this.toTypeName()
} catch (e: StackOverflowError) {
    ANY
}
