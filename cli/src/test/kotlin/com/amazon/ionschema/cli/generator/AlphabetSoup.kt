package com.amazon.ionschema.cli.generator



/**
 * A special type of soup that has letters (or other graphemes) in it.
 */
public data class AlphabetSoup(
    val broth: java.util.Optional<Broth>,
    val letters: java.util.Optional<List<String>>,
) {
    public data class Broth(
        val temp: java.util.Optional<java.math.BigInteger>,
        val flavor: java.util.Optional<Flavor>,
    ) {
        class Builder() {
            private var temp: java.util.Optional<java.math.BigInteger> = java.util.Optional.empty()
            private var flavor: java.util.Optional<Flavor> = java.util.Optional.empty()

            fun withTemp(temp: java.math.BigInteger) = apply { this.temp = java.util.Optional.of(temp) }
            fun withFlavor(flavor: Flavor) = apply { this.flavor = java.util.Optional.of(flavor) }

            fun build() = Broth(temp, flavor)
        }

        companion object {
            @JvmStatic
            fun read(ionReader: com.amazon.ion.IonReader): Broth {
                val builder = Builder()
                ionReader.stepIn()
                var nextType = ionReader.next()
                while (nextType != null) {
                    when (ionReader.fieldName) {
                        "temp" -> builder.withTemp(ionReader.bigIntegerValue())
                        "flavor" -> builder.withFlavor(Flavor.read(ionReader))
                    }
                    nextType = ionReader.next()
                }
                ionReader.stepOut()
                return builder.build()
            }
        }
        fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
            try {
                ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
                if (temp.isPresent) {
                    ionWriter.setFieldName("temp")
                    ionWriter.writeInt(this.temp.get())
                }
                if (flavor.isPresent) {
                    ionWriter.setFieldName("flavor")
                    flavor.get().writeTo(ionWriter)
                }
            } finally {
                ionWriter.stepOut()
            }
        }



        public enum class Flavor(private val symbolText: String) {
            CHICKEN("chicken"), VEGETABLE("vegetable");
            companion object {
                @JvmStatic
                fun read(ionReader: com.amazon.ion.IonReader): Flavor {
                    if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
                        throw com.amazon.ion.IonException ("While attempting to read a Flavor, expected a symbol but found a ${ionReader.type}")
                    }
                    val symbolText = ionReader.stringValue()
                    return values().first { it.symbolText == symbolText }
                }
            }
            fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
                ionWriter.writeSymbol(this.symbolText)
            }
        }
    }
}
