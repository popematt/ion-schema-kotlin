package com.amazon.ionschema.streaming

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ionschema.Type

interface IonStream {
    fun hasNext(): Boolean
    fun close()
    fun next(): IonType
    fun getFieldName(): String
    fun getAnnotations(): List<String>
    fun stepIn()
    fun stepOut()
}

class StreamingValidator(val type: Type, val reader: IonReader)
