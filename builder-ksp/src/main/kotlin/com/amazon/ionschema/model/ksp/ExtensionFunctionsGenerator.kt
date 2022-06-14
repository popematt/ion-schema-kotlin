package com.amazon.ionschema.model.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

fun generateExtensionFunctions(ctx: CodegenContext): List<FunSpec> {
    val funSpecs = mutableListOf<FunSpec>()

    val mutatorParam = ParameterSpec.builder("fn", LambdaTypeName.get(ctx.dslBuilderClassName, returnType = UNIT)).build()

    if (ctx.ksClass.declarations.any { it is KSClassDeclaration && it.isCompanionObject }) {
        funSpecs.add(
            FunSpec.builder("invoke")
                .addOriginatingKSFile(ctx.sourceFile)
                .receiver(ClassName.bestGuess("${ctx.targetClassName}.Companion"))
                .returns(ctx.targetClassName)
                .addModifiers(KModifier.OPERATOR)
                .addParameter(mutatorParam)
                .addStatement("return %T().apply(fn).build()", ctx.implClassName)
                .build()
        )
    }

    funSpecs.add(FunSpec.builder(ctx.targetClassName.mangle().simpleName)
        .addOriginatingKSFile(ctx.sourceFile)
        .returns(ctx.targetClassName)
        .addParameter(mutatorParam)
        .addStatement("return %T().apply(fn).build()", ctx.implClassName)
        .build())

    funSpecs.add(
        FunSpec.builder("copy")
            .addOriginatingKSFile(ctx.sourceFile)
            .receiver(ctx.targetClassName)
            .returns(ctx.targetClassName)
            .addParameter(mutatorParam)
            .addStatement("return %T(this).apply(fn).build()", ctx.implClassName)
            .build())

    return funSpecs
}