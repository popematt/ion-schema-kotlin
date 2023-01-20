package com.amazon.ionschema.internal.model.safe2

import com.amazon.ion.IonBlob
import com.amazon.ion.IonBool
import com.amazon.ion.IonClob
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
import com.amazon.ion.IonSexp
import com.amazon.ion.IonString
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonTimestamp
import com.amazon.ion.IonValue
import com.amazon.ionschema.internal.util.IonTimestampPrecision

/*

sealed class Value {
    class Document(val values: List<IonValue>): Value()
    class Ion<T: IonValue>(val value: T): Value()
    class SymbolToken(val text: String?): Value()
}

interface Marker {
    interface Ion<Value: IonValue>: Contained, Marker
    interface Annotated: Marker
    interface Sequence: Container, Marker
    interface Text: Marker
    interface Container: Marker
    interface TypedContainer<C: Marker.Container, E: Marker.Contained>: Marker, Container
    interface Lob: Marker
    interface Number: Marker
    interface Contained: Marker
    object Null: Annotated, Ion<IonNull>
    object Bool: Annotated, Ion<IonBool>
    object Int: Annotated, Number, Ion<IonInt>
    object Float: Annotated, Number, Ion<IonFloat>
    object Decimal: Annotated, Number, Ion<IonDecimal>
    object String: Annotated, Text, Ion<IonString>
    object Symbol: Annotated, Text, Ion<IonSymbol>
    object Blob: Annotated, Lob, Ion<IonBlob>
    object Clob: Annotated, Lob, Ion<IonClob>
    object Timestamp: Annotated, Ion<IonTimestamp>
    object List: Annotated, Sequence, Ion<IonList>
    object Sexp: Annotated, Sequence, Ion<IonSexp>
    object Struct: Annotated, Container, Ion<IonStruct>
    object SymbolToken: Text, Contained
    object Annotations: Sequence
    object Document: Sequence
}

class TypeDefSafe<T: Marker>(
    val name: String,
    val schemaId: String? = null,
    val constraints: ConstraintSetModel<T>,
    val openContent: Map<String, IonValue> = emptyMap()
) {
    fun asNamedRef() = TypeRefSafe.Reference<T>(name)
}

sealed class TypeRefSafe<M: Marker> {
    class Reference<M: Marker>(val name: String): TypeRefSafe<M>()
    class Inline<M: Marker>(val constraints: List<ConstraintModel<M>>): TypeRefSafe<M>()
}

interface ConstraintModel<C: Marker> {
    class Annotations<M: Marker.Annotated>(val annotations: TypeRefSafe<Marker.Annotations>): ConstraintModel<M>
    class AllOf<M: Marker>(val types: List<TypeRefSafe<M>>): ConstraintModel<M>
    class AnyOf<M: Marker>(val types: List<TypeRefSafe<M>>): ConstraintModel<M>
    class ByteLength<T: Marker.Lob>(val range: DiscreteRange<Int>): ConstraintModel<T>
    class CodepointLength<T: Marker.Text>(val range: DiscreteRange<Int>): ConstraintModel<T>
    class ContainerLength<T: Marker.Container>(val range: DiscreteRange<Int>): ConstraintModel<T>
    class Contains<T: IonValue, M: Marker.Ion<T>>(val values: List<T>): ConstraintModel<M>
    class Element<E: Marker.Contained>(val elementType: TypeRefSafe<E>): ConstraintModel<Marker.Container>
    class Exponent(val range: DiscreteRange<Int>): ConstraintModel<Marker.Decimal>
    class Fields<E: Marker.Ion<*>>(val closed: Boolean, val fields: Map<String, VariablyOccurring<TypeRefSafe<E>>>): ConstraintModel<Marker.Struct>
    class FieldNames(val fieldNamesType: TypeRefSafe<Marker.SymbolToken>): ConstraintModel<Marker.Struct>
    class Ieee754Float(val floatType: Ieee754InterchangeFormat): ConstraintModel<Marker.Float>
    class Not(val type: TypeRefSafe<*>): ConstraintModel<Marker> // Or nothing?
    class OneOf<T: Marker>(val types: List<TypeRefSafe<T>>): ConstraintModel<T>
    class OrderedElements<T: Marker.Sequence, E: Marker.Contained>(val elements: List<VariablyOccurring<TypeRefSafe<E>>>): ConstraintModel<T>
    class Precision(val range: DiscreteRange<Int>): ConstraintModel<Marker.Decimal>
    class Regex<T: Marker.Text>(val pattern: String, val multiline: Boolean, val caseInsensitive: Boolean): ConstraintModel<T>
    class TimestampOffset(val offsets: List<TimestampOffsetValue>): ConstraintModel<Marker.Timestamp>
    class TimestampPrecision(val range: DiscreteRange<IonTimestampPrecision>): ConstraintModel<Marker.Timestamp>
    class Type<M: Marker>(val type: TypeRefSafe<M>): ConstraintModel<M>
    class Utf8ByteLength<T: Marker.Text>(val range: DiscreteRange<Int>): ConstraintModel<T>
    class ValidValues<V: IonValue, M: Marker.Ion<V>>(val values: List<ValidValue<V>>): ConstraintModel<M>
}


val foo = TypeDefSafe(
    name = "foo",
    constraints = listOf(

    )
)





interface ConstraintSetModel<T: Marker> {
    abstract val constraints: List<ConstraintModel<T>>

    interface HasElements<T: Marker.Container, E: Marker.Contained>: ConstraintSetModel<T>

    class Any(override val constraints: List<ConstraintModel<in Marker>>): ConstraintSetModel<Marker>

    // Maybe? object Unconstrained: HasElements<Nothing, Nothing>

    class Text<Ion> (
        val annotations: ConstraintModel.Annotations<Ion>?,
        val type: ConstraintModel.Type<Ion>?,
        val validValues: ConstraintModel.ValidValues<IonText, Ion>?,
        val regex: ConstraintModel.Regex<Ion>?,
        val utf8ByteLength: ConstraintModel.Utf8ByteLength<Ion>?,
        val codepointLength: ConstraintModel.CodepointLength<Ion>?,
    ): ConstraintSetModel<Ion> where Ion : Marker.Text, Ion: Marker.Ion<IonText>, Ion: Marker.Annotated {
        override val constraints: List<ConstraintModel<Ion>>
            get() = listOfNotNull(annotations, type, validValues, regex, utf8ByteLength, codepointLength)
    }

    // class Number<Ion: Marker.Number>: ConstraintSetModel<Ion>

    class Float(
        val annotations: ConstraintModel.Annotations<Marker.Float>?,
        val type: ConstraintModel.Type<Marker.Float>?,
        val validValues: ConstraintModel.ValidValues<IonFloat, Marker.Float>?,
        val floatType: ConstraintModel.Ieee754Float?,
    ): ConstraintSetModel<Marker.Float> {
        override val constraints: List<ConstraintModel<Marker.Float>>
            get() = listOfNotNull(annotations, type, validValues, floatType)
    }

    //class Decimal: ConstraintSetModel<Marker.Decimal>

    class Lob<Ion>(
        val annotations: ConstraintModel.Annotations<Ion>?,
        val type: ConstraintModel.Type<Ion>?,
        val validValues: ConstraintModel.ValidValues<IonLob, Ion>?,
        val byteLength: ConstraintModel.ByteLength<Ion>?,
    ): ConstraintSetModel<Ion> where Ion : Marker.Lob, Ion: Marker.Ion<IonLob>, Ion: Marker.Annotated {
        override val constraints: List<ConstraintModel<Ion>>
            get() = listOfNotNull(annotations, type, validValues, byteLength)
    }

    class Timestamp(
        val annotations: ConstraintModel.Annotations<Marker.Timestamp>?,
        val type: ConstraintModel.Type<Marker.Timestamp>?,
        val validValues: ConstraintModel.ValidValues<IonTimestamp, Marker.Timestamp>?,
        val timestampOffset: ConstraintModel.TimestampOffset?,
        val timestampPrecision: ConstraintModel.TimestampPrecision?,
    ): ConstraintSetModel<Marker.Timestamp> {
        override val constraints: List<ConstraintModel<Marker.Timestamp>>
            get() = listOfNotNull(annotations, type, validValues, timestampOffset, timestampPrecision)
    }

    class Null(
        val annotations: ConstraintModel.Annotations<Marker.Null>?,
    ): ConstraintSetModel<Marker.Null> {
        override val constraints: List<ConstraintModel<Marker.Null>>
            get() = listOfNotNull(annotations)
    }

    class Sequence<Ion, Element: IonValue>(
        val annotations: ConstraintModel.Annotations<Ion>?,
        val type: ConstraintModel.Type<Marker.TypedContainer<Ion, Element>>?,
        val validValues: ConstraintModel.ValidValues<Ion>?,
        val allOf:  ConstraintModel.AllOf<Sequence<Ion, Element>>?,
        val orderedElements: ConstraintModel.OrderedElements<ConstraintSetModel<Element>>?,
        val elements: ConstraintModel.Element<Element>?,
        val contains: ConstraintModel.Contains<IonValue, Element>?,
    ): HasElements<Ion, Element> where Element : Marker.Contained, Ion : Marker.Sequence, Ion: Marker.Ion<*>, Ion: Marker.Annotated, Element: Marker.Ion<*> {
        override val constraints: List<ConstraintModel<Ion>>
            get() = listOfNotNull(annotations, type)
    }

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
        val orderedElements: ConstraintModel.OrderedElements<Marker.Annotations, Marker.SymbolToken>?,
        val element: ConstraintModel.Element<Marker.SymbolToken>?,
        val contains: ConstraintModel.Contains<IonSymbol, Marker.Ion<IonSymbol>>?,
        val type: ConstraintModel.Type<Marker.Annotations>?,
        val validValues: ConstraintModel.ValidValues<IonSymbol, Marker.Ion<IonSymbol>>?,
    ): HasElements<Marker.Annotations, Marker.Symbol> // Or this? ConstraintSetModel<Nothing>

    class SumType<M: Marker>(
        val oneOf:  ConstraintModel.OneOf<M>,
    ): ConstraintSetModel<M>

    class UnionType<M: Marker>(
        val anyOf:  ConstraintModel.AnyOf<M>,
    ): ConstraintSetModel<M>



    // Code gen related types:

    class Record(
        val fields: ConstraintModel.Fields<*>
    ): ConstraintSetModel<Marker.Struct>

    class Enum(
        val values: ConstraintModel.ValidValues<IonSymbol, Marker.Ion<IonSymbol>>
    ): ConstraintSetModel<Marker.Ion<IonSymbol>>
}




sealed class TypeReferenceSafe<T> {
    class Reference<T: ConstraintSetModel<*>>(val name: String): TypeReferenceSafe<T>()
    class Inline<T: ConstraintSetModel<*>>(val constraints: T): TypeReferenceSafe<T>()
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

 */