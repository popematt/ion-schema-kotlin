package com.amazon.kitchen.products

public data class Sandwich(
    val bread: kotlin.Boolean?,
) {
    init {
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        try {
            ionWriter.setFieldName("bread")
if (bread == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    bread.writeTo(ionWriter)
}
        } finally {
            ionWriter.stepOut()
        }
    }
    class Builder {
    var bread: kotlin.Boolean? = null
    fun withBread(bread: kotlin.Boolean?) = apply {
        this.bread = bread
    }
    fun build() = Sandwich(
    bread = this.bread,
    )
}
}
