package com.amazon.ionschema.cli.util

import java.util.*

fun String.toSnakeCase(): String = replace(humps, "_").lowercase()
private val humps = "(?<=.)(?=\\p{Upper})".toRegex()

fun String.toScreamingSnakeCase(): String = toSnakeCase().uppercase()
fun String.toPascalCase(): String = split("_", "-", ".").joinToString("") { it.capitalizeFirstLetter() }
fun String.toCamelCase(): String = toPascalCase().uncapitalizeFirstLetter()

fun String.capitalizeFirstLetter(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
fun String.uncapitalizeFirstLetter(): String = replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }
