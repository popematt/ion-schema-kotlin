package com.amazon.ionschema.internal.model

import com.amazon.ion.IonDatagram
import com.amazon.ion.IonDecimal
import com.amazon.ion.IonFloat
import com.amazon.ion.IonInt
import com.amazon.ion.IonLob
import com.amazon.ion.IonNumber
import com.amazon.ion.IonSequence
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonTimestamp
import com.amazon.ion.IonValue
import com.amazon.ionschema.internal.util.IonTimestampPrecision


interface ConstraintSetModel {
    class Any(val constraints: ConstraintSetModel): ConstraintSetModel

    class Text<T: IonText>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Text<T>>?,
        val validValues: ConstraintModel.ValidValues<T>?,
        val regex: ConstraintModel.Regex?,
        val utf8ByteLength: ConstraintModel.Utf8ByteLength?,
        val codepointLength: ConstraintModel.CodepointLength?,
    ): ConstraintSetModel

    class Number: ConstraintSetModel

    class Int: ConstraintSetModel

    class Float: ConstraintSetModel

    class Decimal: ConstraintSetModel

    class Lob: ConstraintSetModel

    class Timestamp(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Timestamp>?,
        val validValues: ConstraintModel.ValidValues<IonTimestamp>?,
        val timestampPrecision: ConstraintModel.TimestampPrecision?,
        val timestampOffset: ConstraintModel.TimestampOffset?,
    ): ConstraintSetModel

    class Null(
        val annotations: ConstraintModel.Annotations?,
    ): ConstraintSetModel

    class Sequence<IonE: IonValue, E: ConstraintSetModel>(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Sequence<IonE, E>>?,
        val validValues: ConstraintModel.ValidValues<IonSequence>?,
        val allOf:  ConstraintModel.AllOf<Sequence<IonE, E>>?,
        val orderedElements: ConstraintModel.OrderedElements<E>?,
        val elements: ConstraintModel.Element<E>?,
        val contains: ConstraintModel.Contains<IonE>?,
    ): ConstraintSetModel

    class Struct(
        val annotations: ConstraintModel.Annotations?,
        val type: ConstraintModel.Type<Struct>?,
        val validValues: ConstraintModel.ValidValues<IonStruct>?,
    ): ConstraintSetModel

    class Document<IonE: IonValue, E: ConstraintSetModel>(
        val type: ConstraintModel.Type<Sequence<IonE, E>>?,
        val allOf:  ConstraintModel.AllOf<Sequence<IonE, E>>?,
        val orderedElements: ConstraintModel.OrderedElements<E>?,
        val elements: ConstraintModel.Element<E>?,
        val contains: ConstraintModel.Contains<IonE>?,
    ): ConstraintSetModel


    class SymbolToken(
        val type: ConstraintModel.Type<SymbolToken>?,
        val validValues: ConstraintModel.ValidValues<IonSymbol>?,
        val regex: ConstraintModel.Regex?,
        val utf8ByteLength: ConstraintModel.Utf8ByteLength?,
        val codepointLength: ConstraintModel.CodepointLength?,
    ): ConstraintSetModel

    class Annotations(
        val allOf:  ConstraintModel.AllOf<Annotations>?,
        val oneOf:  ConstraintModel.OneOf<Annotations>?,
        val orderedElements: ConstraintModel.OrderedElements<SymbolToken>?,
        val elements: ConstraintModel.OrderedElements<SymbolToken>?,
        val contains: ConstraintModel.Contains<IonSymbol>?,
        val type: ConstraintModel.Type<Annotations>?,
        val validValues: ConstraintModel.ValidValues<IonSymbol>?,
    ): ConstraintSetModel

    class SumType<T: ConstraintSetModel>(
        val oneOf:  ConstraintModel.OneOf<T>,
    ): ConstraintSetModel

    class UnionType<T: ConstraintSetModel>(
        val anyOf:  ConstraintModel.OneOf<T>,
    ): ConstraintSetModel



    // Code gen related types:

    class Record(
        val fields: ConstraintModel.Fields<*>
    ): ConstraintSetModel

    class Enum(
        val values: ConstraintModel.ValidValues<IonSymbol>
    ): ConstraintSetModel
}


class TypeDefSafe<T: ConstraintSetModel>(
    val name: String,
    val schemaId: String?,
    val constraints: T,
    val openContent: Map<String, IonValue>
) {
    fun asNamedRef() = TypeReferenceSafe.Reference<T>(name)
}

sealed class TypeReferenceSafe<T: ConstraintSetModel> {
    class Reference<T: ConstraintSetModel>(val name: String): TypeReferenceSafe<T>()
    class Inline<T: ConstraintSetModel>(val constraints: T): TypeReferenceSafe<T>()
}


interface ConstraintModel {
    class Annotations(val annotations: TypeReferenceSafe<ConstraintSetModel.Annotations>): ConstraintModel
    class AllOf<T: ConstraintSetModel>(val types: List<T>): ConstraintModel
    class AnyOf<T: ConstraintSetModel>(val types: List<T>): ConstraintModel
    class ByteLength(val range: DiscreteRange<Int>): ConstraintModel
    class CodepointLength(val range: DiscreteRange<Int>): ConstraintModel
    class ContainerLength(val range: DiscreteRange<Int>): ConstraintModel
    class Contains<T: IonValue>(val values: List<T>): ConstraintModel
    class Element<T: ConstraintSetModel>(val elementType: TypeReferenceSafe<T>): ConstraintModel
    class Exponent(val range: DiscreteRange<Int>): ConstraintModel
    class Fields<T: ConstraintSetModel>(val closed: Boolean, val fields: Map<String, VariablyOccurring<TypeReferenceSafe<T>>>): ConstraintModel
    class FieldNames(val fieldNamesType: TypeReferenceSafe<ConstraintSetModel.SymbolToken>): ConstraintModel
    class Ieee754Float(val floatType: Ieee754InterchangeFormat): ConstraintModel
    class Not(val type: ConstraintSetModel): ConstraintModel
    class OneOf<T: ConstraintSetModel>(val types: List<T>): ConstraintModel
    class OrderedElements<T: ConstraintSetModel>(val elements: List<VariablyOccurring<TypeReferenceSafe<T>>>): ConstraintModel
    class Precision(val range: DiscreteRange<Int>): ConstraintModel
    class Regex(val pattern: String, val multiline: Boolean, val caseInsensitive: Boolean): ConstraintModel
    class TimestampOffset(val offsets: List<TimestampOffsetValue>): ConstraintModel
    class TimestampPrecision(val range: DiscreteRange<IonTimestampPrecision>): ConstraintModel
    class Type<T: ConstraintSetModel>(val type: T): ConstraintModel
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