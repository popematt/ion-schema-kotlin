package com.amazon.ionschema.internal.model.safe2

import com.amazon.ion.IonContainer
import com.amazon.ion.IonValue
import com.amazon.ionschema.Type

fun IonValue.findAll(type: Type): List<IonValue> {
    val childMatches = if (this is IonContainer && !this.isNullValue) {
        flatMap { it.findAll(type) }
    } else {
        emptyList()
    }
    return if (type.isValid(this)) {
        childMatches + this
    } else {
        childMatches
    }
}