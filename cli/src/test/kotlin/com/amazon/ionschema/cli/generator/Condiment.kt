package com.amazon.ionschema.cli.generator

import com.amazon.ion.IonException
import com.amazon.ion.IonReader
import com.amazon.ion.IonType

/**
 * Things you can put in a sandwich, hamburger, etc.
 *   Condiment is specifically defined as a _spreadable_ thing in this case, and does not include anything that is (subjectively) too unconventional.
 */
public enum class Condiment(private val symbolText: String) {
    CREAM_CHEESE("cream_cheese"), STRAWBERRY_JAM("strawberry_jam"), RASPBERRY_JAM("raspberry_jam"), PEANUT_BUTTER("peanut_butter"), RELISH("relish"), MAYONNAISE("mayonnaise"), BUTTER("butter"), MUSTARD("mustard"), KETCHUP("ketchup");
    companion object {
        @JvmStatic
        fun read(ionReader: IonReader): Condiment {
            if(ionReader.type != IonType.SYMBOL) throw IonException ("While reading a Condiment, expected a symbol but found a ${ionReader.type}")
            val symbolText = ionReader.stringValue()
            return values().first { it.symbolText == symbolText }
        }
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.writeSymbol(this.symbolText)
    }
}
