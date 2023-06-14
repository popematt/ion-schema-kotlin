package com.amazon.kitchen.products

public data class Salad(
    val name: com.amazon.kitchen.products.Salad.Name?,
    val vegetarian: kotlin.Boolean?,
) {
    init {
        name?.let {
            val codepointLength = it.codePointCount(0, name.length)
            require(codepointLength in 1..32) { "Value '$fieldName' is $codepointLength codepoints; must be in 1..32" }
        }
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        try {
            ionWriter.setFieldName("name")
if (name == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    name.writeTo(ionWriter)
}
ionWriter.setFieldName("vegetarian")
if (vegetarian == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    vegetarian.writeTo(ionWriter)
}
        } finally {
            ionWriter.stepOut()
        }
    }
    class Builder {
    var name: com.amazon.kitchen.products.Salad.Name? = null
    fun withName(name: com.amazon.kitchen.products.Salad.Name?) = apply {
        this.name = name
    }
    var vegetarian: kotlin.Boolean? = null
    fun withVegetarian(vegetarian: kotlin.Boolean?) = apply {
        this.vegetarian = vegetarian
    }
    fun build() = Salad(
    name = this.name,
    vegetarian = this.vegetarian,
    )
}
}
