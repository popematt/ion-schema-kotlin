package com.amazon.ionschema.streaming

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import java.math.BigDecimal
import java.math.BigInteger
import java.util.LinkedList

class ValidatingReader(val type: StreamingType, val wrapped: IonReader) : IonReader by wrapped {

    private val readerView = IonReaderValue(wrapped)

    var typesStack: LinkedList<StreamingType> = LinkedList()
    var statesStack: LinkedList<StatefulStreamingConstraint.ConstraintState> = LinkedList()
    init {
        typesStack.push(type)
        statesStack.push(type.handleStepIn(object : IonReaderValue {
            override fun <T : Any> asFacet(facetType: Class<T>?): T? {
                return wrapped.asFacet(facetType)
            }

            override fun getType(): IonType {
                return IonType.DATAGRAM
            }

            override fun getIntegerSize(): IntegerSize {
                TODO("Not yet implemented")
            }

            override fun getTypeAnnotations(): Array<String> {
                return emptyArray()
            }

            override fun getFieldName(): String {
                TODO("Not yet implemented")
            }

            override fun isNullValue(): Boolean {
                return false
            }
            override fun isInStruct(): Boolean {
                TODO("Not yet implemented")
            }
            override fun booleanValue(): Boolean {
                TODO("Not yet implemented")
            }
            override fun intValue(): Int {
                TODO("Not yet implemented")
            }
            override fun longValue(): Long {
                TODO("Not yet implemented")
            }
            override fun bigIntegerValue(): BigInteger {
                TODO("Not yet implemented")
            }
            override fun doubleValue(): Double {
                TODO("Not yet implemented")
            }
            override fun bigDecimalValue(): BigDecimal {
                TODO("Not yet implemented")
            }
            override fun decimalValue(): Decimal {
                TODO("Not yet implemented")
            }
            override fun timestampValue(): Timestamp {
                TODO("Not yet implemented")
            }
            override fun stringValue(): String {
                TODO("Not yet implemented")
            }
            override fun byteSize(): Int {
                TODO("Not yet implemented")
            }
            override fun newBytes(): ByteArray {
                TODO("Not yet implemented")
            }
            override fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int {
                TODO("Not yet implemented")
            }

        }))
    }

    override fun close() {
        wrapped.close()
    }

    override fun next(): IonType {
        type.handleNext(readerView)
        return wrapped.next()
    }

    override fun stepIn() {
        val state = typesStack.peek().handleStepIn(readerView)
        statesStack.push(state)
        wrapped.stepIn()
    }

    override fun stepOut() {
        statesStack.pop().handleStepOut()
        wrapped.stepOut()
    }
}
