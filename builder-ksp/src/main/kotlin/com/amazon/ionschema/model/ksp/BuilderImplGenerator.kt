package com.amazon.ionschema.model.ksp

import com.amazon.ionschema.model.codegen.Builder
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

@OptIn(KspExperimental::class)
fun getDefaultParameterValue(ctx: CodegenContext, parameter: KSValueParameter): String? {
    val default = parameter.getAnnotationsByType(Builder.Default::class)
    return default.singleOrNull()?.code
}

fun createBuilderImpl(ctx: CodegenContext): TypeSpec {

    val implPropSpecs = mutableListOf<PropertySpec>()
    val implFunSpecs = mutableListOf<FunSpec>()

    for (parameter in ctx.ctor.parameters) {
        implPropSpecs += PropertySpec.builder(parameter.name!!.getShortName(), parameter.type.typeName)
            .mutable(true)
            .addModifiers(KModifier.OVERRIDE)
            .apply {
                if (parameter.hasDefault && getDefaultParameterValue(ctx, parameter) != null) {
                    initializer(getDefaultParameterValue(ctx, parameter)!!)
                } else if (parameter.type.typeName.isNullable) {
                    initializer("null")
                } else {
                    addModifiers(KModifier.LATEINIT)
                }
            }
            .build()

        implFunSpecs += createConcreteWither(parameter.name!!.getShortName(), parameter.type.typeName, ctx.implClassName)
    }

    val specBuilder = TypeSpec.classBuilder(ctx.implClassName).apply {
        addOriginatingKSFile(ctx.sourceFile)
        addSuperinterface(ctx.dslBuilderClassName)
        addSuperinterface(ctx.javaBuilderClassName)
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder("sourceObject", ctx.targetClassName.copy(nullable = true)).defaultValue("null").build())
                .build()
        )
        addProperties(implPropSpecs)

        addInitializerBlock(CodeBlock.builder()
            .beginControlFlow("if (sourceObject != null)")
            .apply {
                ctx.ctor.parameters.forEach {
                    addStatement("this.${it.name?.getShortName()} = sourceObject.${it.name?.getShortName()}")
                }
            }
            .endControlFlow().build())


        addFunctions(implFunSpecs)
        addFunction(
            FunSpec.builder("build").apply {
                returns(ctx.targetClassName)
                modifiers.clear()
                modifiers += KModifier.OVERRIDE
                addStatement("return %T(" +
                        ctx.ctor.parameters.joinToString { "${it.name?.getShortName()} = this.${it.name?.getShortName()}" } +
                        ")", ctx.targetClassName)
            }.build()
        )
    }

    return specBuilder.build()
}

private fun createConcreteWither(propertyName: String, propertyType: TypeName, thisType: TypeName): FunSpec {
    return FunSpec.builder("with${propertyName.capitalizeFirstLetter()}")
        .addParameter(propertyName, propertyType)
        .returns(thisType)
        .addModifiers(KModifier.OVERRIDE)
        .addCode(CodeBlock.builder()
            .addStatement("this.$propertyName = $propertyName")
            .addStatement("return this")
            .build())
        .build()
}

private fun String.capitalizeFirstLetter(): String {
    return this.replaceFirstChar { it.uppercaseChar() }
}

