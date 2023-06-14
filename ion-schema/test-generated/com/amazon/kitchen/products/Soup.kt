package com.amazon.kitchen.products

public data class Soup(
    val broth: com.amazon.kitchen.products.Soup.Broth?,
) {
    init {
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        try {
            ionWriter.setFieldName("broth")
if (broth == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    broth.writeTo(ionWriter)
}
        } finally {
            ionWriter.stepOut()
        }
    }
    public data class Broth(
        val temp: java.math.BigInteger?,
        val flavor: com.amazon.kitchen.products.Soup.Broth.Flavor?,
    ) {
        init {
        }
        fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
            ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
            try {
                ionWriter.setFieldName("temp")
    if (temp == null) {
        ionWriter.writeNull()
    } else {
        // TODO: handle primitives
        temp.writeTo(ionWriter)
    }
    ionWriter.setFieldName("flavor")
    if (flavor == null) {
        ionWriter.writeNull()
    } else {
        // TODO: handle primitives
        flavor.writeTo(ionWriter)
    }
            } finally {
                ionWriter.stepOut()
            }
        }
        public enum class Flavor(val symbolText: String) {
            CHICKEN("chicken"), VEGETABLE("vegetable");
            companion object {
                @JvmStatic
                fun readFrom(ionReader: com.amazon.ion.IonReader): Flavor {
                    if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
                        throw com.amazon.ion.IonException ("While attempting to read a Flavor, expected a symbol but found a ${ionReader.type}")
                    }
                    val symbolText = ionReader.stringValue()
                    return values().firstOrNull { it.symbolText == symbolText }
                        ?: throw IllegalArgumentException("Unknown Flavor: '$symbolText'")
                }
            }
            fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
                ionWriter.writeSymbol(this.symbolText)
            }
        }
        class Builder {
        var temp: java.math.BigInteger? = null
        fun withTemp(temp: java.math.BigInteger?) = apply {
            this.temp = temp
        }
        var flavor: com.amazon.kitchen.products.Soup.Broth.Flavor? = null
        fun withFlavor(flavor: com.amazon.kitchen.products.Soup.Broth.Flavor?) = apply {
            this.flavor = flavor
        }
        fun build() = Broth(
        temp = this.temp,
        flavor = this.flavor,
        )
    }
    }
    class Builder {
    var broth: com.amazon.kitchen.products.Soup.Broth? = null
    fun withBroth(broth: com.amazon.kitchen.products.Soup.Broth?) = apply {
        this.broth = broth
    }
    fun build() = Soup(
    broth = this.broth,
    )
}
}
