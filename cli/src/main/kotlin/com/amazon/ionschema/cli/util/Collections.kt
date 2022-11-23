package com.amazon.ionschema.cli.util

internal fun <E> MutableSet<E>.retain(vararg elements: E) = retainAll(elements)
