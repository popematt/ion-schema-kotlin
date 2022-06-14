package com.amazon.ionschema.model.ksp

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName

fun createDslInterface(ctx: CodegenContext): TypeSpec {
    val propSpecs = ctx.ctor.parameters.map {
        PropertySpec.builder(it.name!!.getShortName(), it.type.typeName)
            .mutable(true)
            .addModifiers(KModifier.ABSTRACT)
            .build()
    }

    val specBuilder = TypeSpec.interfaceBuilder(ctx.dslBuilderClassName)
        .addOriginatingKSFile(ctx.sourceFile)
        .addProperties(propSpecs)

    return specBuilder.build()
}
