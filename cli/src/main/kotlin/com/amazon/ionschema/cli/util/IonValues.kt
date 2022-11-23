package com.amazon.ionschema.cli.util

import com.amazon.ion.IonValue

private fun IonValue.cloneIfParented() = cloneIf { it.container != null }

private inline fun IonValue.cloneIf(predicate: (IonValue) -> Boolean) = if (predicate(this)) clone() else this
