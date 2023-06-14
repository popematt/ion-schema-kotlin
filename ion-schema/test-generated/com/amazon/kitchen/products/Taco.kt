package com.amazon.kitchen.products

public data class Taco(
    val shell: com.amazon.kitchen.products.Taco.Shell,
) {
    init {
    }
    fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
        ionWriter.stepIn(com.amazon.ion.IonType.STRUCT)
        try {
            ionWriter.setFieldName("shell")
if (shell == null) {
    ionWriter.writeNull()
} else {
    // TODO: handle primitives
    shell.writeTo(ionWriter)
}
        } finally {
            ionWriter.stepOut()
        }
    }
    public enum class Shell(val symbolText: String) {
        HARD("hard"), SOFT("soft");
        companion object {
            @JvmStatic
            fun readFrom(ionReader: com.amazon.ion.IonReader): Shell {
                if(ionReader.type != com.amazon.ion.IonType.SYMBOL) {
                    throw com.amazon.ion.IonException ("While attempting to read a Shell, expected a symbol but found a ${ionReader.type}")
                }
                val symbolText = ionReader.stringValue()
                return values().firstOrNull { it.symbolText == symbolText }
                    ?: throw IllegalArgumentException("Unknown Shell: '$symbolText'")
            }
        }
        fun writeTo(ionWriter: com.amazon.ion.IonWriter) {
            ionWriter.writeSymbol(this.symbolText)
        }
    }
    class Builder {
    var shell: com.amazon.kitchen.products.Taco.Shell? = null
    fun withShell(shell: com.amazon.kitchen.products.Taco.Shell) = apply {
        this.shell = shell
    }
    fun build() = Taco(
    shell = this.shell ?: throw IllegalArgumentException("shell cannot be null"),
    )
}
}
