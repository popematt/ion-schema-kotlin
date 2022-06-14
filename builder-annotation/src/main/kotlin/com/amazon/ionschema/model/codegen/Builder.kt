package com.amazon.ionschema.model.codegen

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
annotation class Builder {

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Default(val code: String)
}
