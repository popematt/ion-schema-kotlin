package com.amazon.ionschema.internal.model.safe

import com.amazon.ion.IonContainer
import com.amazon.ionschema.internal.model.DiscreteRange
import com.amazon.ionschema.internal.model.ValidValue
import com.amazon.ionschema.internal.model.VariablyOccurring

import com.amazon.ion.IonDatagram
import com.amazon.ion.IonDecimal
import com.amazon.ion.IonFloat
import com.amazon.ion.IonInt
import com.amazon.ion.IonList
import com.amazon.ion.IonLob
import com.amazon.ion.IonNull
import com.amazon.ion.IonNumber
import com.amazon.ion.IonSequence
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonTimestamp
import com.amazon.ion.IonValue
import com.amazon.ionschema.internal.util.IonTimestampPrecision

typealias ListDefinition<E> = TypeDefSafe<ConstraintSetModel.Sequence<IonList, E>>
typealias TextDefinition = TypeDefSafe<ConstraintSetModel.Text<IonText>>
typealias StringDefinition = Unit

interface ConstraintSetModel<Ion: IonValue> {
    interface HasElements<Ion: IonContainer, Element: IonValue>: ConstraintSetModel<Ion>

    class Any(val constraints: List<ConstraintModel>): ConstraintSetModel<IonValue>

    // Maybe? object Unconstrained: HasElements<Nothing, Nothing>

    class Text<Ion: IonText>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Text<Ion>>?,
        val validValues: ConstraintModel.ValidValues<Ion>?,
        val regex: ConstraintModel.Regex?,
        val utf8ByteLength: ConstraintModel.Utf8ByteLength?,
        val codepointLength: ConstraintModel.CodepointLength?,
    ): ConstraintSetModel<Ion>

    class Number<Ion: IonNumber>: ConstraintSetModel<Ion>

    class Float: ConstraintSetModel<IonFloat>

    class Decimal: ConstraintSetModel<IonDecimal>

    class Lob<Ion: IonLob>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Lob<Ion>>?,
        val validValues: ConstraintModel.ValidValues<Ion>?,
        val byteLength: ConstraintModel.Utf8ByteLength?,
    ): ConstraintSetModel<Ion>

    class Timestamp(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Timestamp>?,
        val validValues: ConstraintModel.ValidValues<IonTimestamp>?,
        val timestampPrecision: ConstraintModel.TimestampPrecision?,
        val timestampOffset: ConstraintModel.TimestampOffset?,
    ): ConstraintSetModel<IonTimestamp>

    class Null(
        val annotations: ConstraintModel.Annotations?,
    ): ConstraintSetModel<IonNull>

    class Sequence<Ion: IonSequence, Element: IonValue>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Sequence<Ion, Element>>?,
        val validValues: ConstraintModel.ValidValues<Ion>?,
        val allOf:  ConstraintModel.AllOf<Sequence<Ion, Element>>?,
        val orderedElements: ConstraintModel.OrderedElements<ConstraintSetModel<Element>>?,
        val elements: ConstraintModel.Element<Element>?,
        val contains: ConstraintModel.Contains<Element>?,
    ): HasElements<Ion, Element>

    class Struct<Element: IonValue>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Struct<Element>>?,
        val validValues: ConstraintModel.ValidValues<IonStruct>?,
        val fields: ConstraintModel.Fields<ConstraintSetModel<Element>>?,
        val element: ConstraintModel.Element<Element>?,
        val contains: ConstraintModel.Contains<Element>?,

    ): HasElements<IonStruct, Element>

    class Document<Element: IonValue>(
        val type: ConstraintModel.Type<HasElements<IonDatagram, Element>>?,
        val allOf:  ConstraintModel.AllOf<HasElements<IonDatagram, Element>>?,
        val orderedElements: ConstraintModel.OrderedElements<ConstraintSetModel<Element>>?,
        val element: ConstraintModel.Element<Element>?,
        val contains: ConstraintModel.Contains<Element>?,
    ): HasElements<IonDatagram, Element>


    class SymbolToken(
        val type: ConstraintModel.Type<SymbolToken>?,
        val validValues: ConstraintModel.ValidValues<IonSymbol>?,
        val regex: ConstraintModel.Regex?,
        val utf8ByteLength: ConstraintModel.Utf8ByteLength?,
        val codepointLength: ConstraintModel.CodepointLength?,
    ): ConstraintSetModel<IonText> // Or this? ConstraintSetModel<Nothing>

    class Annotations(
        val allOf:  ConstraintModel.AllOf<Annotations>?,
        val oneOf:  ConstraintModel.OneOf<Annotations>?,
        val orderedElements: ConstraintModel.OrderedElements<SymbolToken>?,
        val element: ConstraintModel.OrderedElements<SymbolToken>?,
        val contains: ConstraintModel.Contains<IonSymbol>?,
        val type: ConstraintModel.Type<Annotations>?,
        val validValues: ConstraintModel.ValidValues<IonSymbol>?,
    ): HasElements<IonList, IonSymbol> // Or this? ConstraintSetModel<Nothing>

    class SumType<Ion: IonValue>(
        val oneOf:  ConstraintModel.OneOf<ConstraintSetModel<Ion>>,
    ): ConstraintSetModel<Ion>

    class UnionType<Ion: IonValue>(
        val anyOf:  ConstraintModel.OneOf<ConstraintSetModel<Ion>>,
    ): ConstraintSetModel<Ion>



    // Code gen related types:

    class Record(
        val fields: ConstraintModel.Fields<*>
    ): ConstraintSetModel<IonValue>

    class Enum(
        val values: ConstraintModel.ValidValues<IonSymbol>
    ): ConstraintSetModel<IonSymbol>
}


class TypeDefSafe<T: ConstraintSetModel<*>>(
    val name: String,
    val schemaId: String?,
    val constraints: T,
    val openContent: Map<String, IonValue>
) {
    fun asNamedRef() = TypeReferenceSafe.Reference<T>(name)
}

sealed class TypeReferenceSafe<T> {
    class Reference<T: ConstraintSetModel<*>>(val name: String): TypeReferenceSafe<T>()
    class Inline<T: ConstraintSetModel<*>>(val constraints: T): TypeReferenceSafe<T>()
}


interface ConstraintModel {
    class Annotations(val annotations: TypeReferenceSafe<ConstraintSetModel.Annotations>): ConstraintModel
    class AllOf<T: ConstraintSetModel<*>>(val types: List<T>): ConstraintModel
    class AnyOf<T: ConstraintSetModel<*>>(val types: List<T>): ConstraintModel
    class ByteLength(val range: DiscreteRange<Int>): ConstraintModel
    class CodepointLength(val range: DiscreteRange<Int>): ConstraintModel
    class ContainerLength(val range: DiscreteRange<Int>): ConstraintModel
    class Contains<T: IonValue>(val values: List<T>): ConstraintModel
    class Element<T: IonValue>(val elementType: TypeReferenceSafe<ConstraintSetModel<T>>): ConstraintModel
    class Exponent(val range: DiscreteRange<Int>): ConstraintModel
    class Fields<T: ConstraintSetModel<*>>(val closed: Boolean, val fields: Map<String, VariablyOccurring<TypeReferenceSafe<T>>>): ConstraintModel
    class FieldNames(val fieldNamesType: TypeReferenceSafe<ConstraintSetModel.SymbolToken>): ConstraintModel
    class Ieee754Float(val floatType: Ieee754InterchangeFormat): ConstraintModel
    class Not(val type: ConstraintSetModel<*>): ConstraintModel
    class OneOf<T: ConstraintSetModel<*>>(val types: List<T>): ConstraintModel
    class OrderedElements2<T: IonValue>(val elements: List<VariablyOccurring<TypeReferenceSafe<ConstraintSetModel<T>>>>): ConstraintModel
    class OrderedElements<T: ConstraintSetModel<*>>(val elements: List<VariablyOccurring<TypeReferenceSafe<T>>>): ConstraintModel
    class Precision(val range: DiscreteRange<Int>): ConstraintModel
    class Regex(val pattern: String, val multiline: Boolean, val caseInsensitive: Boolean): ConstraintModel
    class TimestampOffset(val offsets: List<TimestampOffsetValue>): ConstraintModel
    class TimestampPrecision(val range: DiscreteRange<IonTimestampPrecision>): ConstraintModel
    class Type<T: ConstraintSetModel<*>>(val type: T): ConstraintModel
    class Utf8ByteLength(val range: DiscreteRange<Int>): ConstraintModel
    class ValidValues<V: IonValue>(val values: List<ValidValue<V>>): ConstraintModel
}

sealed class TimestampOffsetValue {
    object Unknown: TimestampOffsetValue() {
        override fun toString(): String = "-00:00"
    }
    class Known(val minutes: Int): TimestampOffsetValue() {
        override fun toString(): String = "${minutes/60}:${minutes%60}"
    }
}
enum class Ieee754InterchangeFormat {
    Binary16,
    Binary32,
    Binary64;
}