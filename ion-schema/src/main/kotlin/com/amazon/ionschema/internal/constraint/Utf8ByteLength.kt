package com.amazon.ionschema.internal.constraint

import com.amazon.ionelement.api.StructField
import com.amazon.ionelement.api.TextElement

/**
 * Implements the utf8_byte_length constraint.
 */
internal class Utf8ByteLength(
    ion: StructField
) : ConstraintBaseIntRange<TextElement>(TextElement::class.java, ion) {

    override val violationCode = "invalid_utf8_byte_length"
    override val violationMessage = "invalid utf8 byte length %s, expected %s"

    override fun getIntValue(value: TextElement) = byteLength(value.textValue)

    private fun byteLength(cs: CharSequence): Int {
        var count = 0
        var skipLowSurrogate = false
        for (c in cs) {
            if (skipLowSurrogate) {
                count += 2
                skipLowSurrogate = false
            } else {
                val cValue = c.toInt()
                if (cValue < 0x80) {
                    count++
                } else if (cValue < 0x800) {
                    count += 2
                } else if (c.isHighSurrogate()) {
                    count += 2
                    skipLowSurrogate = true
                } else {
                    count += 3
                }
            }
        }

        return count
    }
}
