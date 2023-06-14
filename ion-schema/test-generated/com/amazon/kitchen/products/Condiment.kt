package com.amazon.kitchen.products

/**
 * Things you can put in a sandwich, hamburger, etc.
 *   Condiment is specifically defined as a _spreadable_ thing in this case, and does not include anything that is (subjectively) too unconventional.
 */
public enum class Condiment(val symbolText: String) {
    CREAM_CHEESE("cream_cheese"), STRAWBERRY_JAM("strawberry_jam"), RASPBERRY_JAM("raspberry_jam"), PEANUT_BUTTER("peanut_butter"), RELISH("relish"), MAYONNAISE("mayonnaise"), BUTTER("butter"), MUSTARD("mustard"), KETCHUP("ketchup");
    companion object {
        @JvmStatic
        fun readFrom(ionReader: com.amazon.ion.IonReader): Condiment {
            if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
                throw com.amazon.ion.IonException ("While attempting to read a Condiment, expected a symbol but found a ${ionReader.type}")
            }
            val symbolText = ionReader.stringValue()
            return values().firstOrNull { it.symbolText == symbolText }
                ?: throw IllegalArgumentException("Unknown Condiment: '$symbolText'")
        }
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.writeSymbol(this.symbolText)
    }
}
