package com.amazon.ionschema.internal.model.safe3

import com.amazon.ionschema.internal.model.DiscreteRange
import com.amazon.ionschema.internal.model.VariablyOccurring

import com.amazon.ion.IonValue
import com.amazon.ionschema.internal.model.ContinuousRange
import com.amazon.ionschema.internal.model.IslRange
import com.amazon.ionschema.internal.util.IonTimestampPrecision
import java.math.BigDecimal
import java.time.Instant


interface IslValue {
    interface IslText: IslValue
    interface IslSequence: IslContainer
    interface IslContainer: IslValue
    interface IslContained: IslValue

    class Document: IslValue, IslSequence
    class SymbolToken: IslValue, IslContained
    class AnnotationsList: IslValue, IslSequence
    
    interface Ion: IslValue, IslContained, ConstrainedBy.Annotations {
        interface Number: Ion
        interface Lob: Ion
        interface Text: Ion, IslText
        interface Container: Ion, IslContainer
        interface Sequence: Ion, IslSequence
        class Null: Ion
        class Bool: Ion
        class Int: Number
        class Float: Number
        class Decimal: Number
        class String: Text, ConstrainedBy.Regex, ConstrainedBy.CodepointLength
        class Symbol: Text
        class Blob: Lob
        class Clob: Lob
        class Timestamp: Ion
        class Struct: Container
        class Sexp: Sequence
        class List: Sequence
    }
}
object ConstraintOf {
    interface Null
    interface Bool
    interface Int
    interface Float
    interface Decimal
    interface String
    interface Symbol
    interface Container<T>
    
    interface Text: String, Symbol, SymbolToken
    interface Ion: String, Symbol
    
    interface AnnotationsList
    interface SymbolToken
}



class Foo2: ConstraintOf.Ion
class Foo3: ConstraintOf.Text

interface ConstrainedBy {
    interface Annotations
    interface Regex
    interface CodepointLength
    interface Exponent
    interface Type<T: IslValue>
}

fun doSomethingElse(): List<ConstraintOf.String> {
    return listOf(
        Foo2(),
        Foo3()
    )
}


interface Constraint<in T: IslValue> {
    class Annotations(val annotations: TypeRefSafe<IslValue.AnnotationsList>): Constraint<IslValue.Ion>
    class AllOf<T: IslValue>(val types: List<TypeRefSafe<T>>): Constraint<T>
    class AnyOf<T: IslValue>(val types: List<TypeRefSafe<T>>): Constraint<T>
    class ByteLength(val range: DiscreteRange<Int>): Constraint<IslValue.Ion.Lob>
    class CodepointLength(val range: DiscreteRange<Int>): Constraint<IslValue.IslText>, ConstraintOf.String, ConstraintOf.Symbol, ConstraintOf.SymbolToken
    class Utf8ByteLength(val range: DiscreteRange<Int>): Constraint<IslValue.IslText>

    class ContainerLength<T: IslValue.IslContainer>(val range: DiscreteRange<Int>): Constraint<T>
    class Contains<T: IslValue.IslContainer, V: IslValue.Ion>(val values: List<V>): Constraint<T>
    class Element<E: IslValue.Ion>(val elementType: TypeRefSafe<E>): Constraint<IslValue.IslContainer>
    class Exponent(val range: DiscreteRange<Int>): Constraint<IslValue.Ion.Decimal>
    class Fields<E: IslValue.Ion>(val closed: Boolean, val fields: Map<String, VariablyOccurring<TypeRefSafe<E>>>): Constraint<IslValue.Ion.Struct>
    class FieldNames(val fieldNamesType: TypeRefSafe<IslValue.SymbolToken>): Constraint<IslValue.Ion.Struct>
    class Ieee754Float(val floatType: Ieee754InterchangeFormat): Constraint<IslValue.Ion.Float>
    class Not(val type: TypeRefSafe<*>): Constraint<IslValue> // Or nothing?
    class OneOf<T: IslValue>(val types: List<TypeRefSafe<T>>): Constraint<T>
    class OrderedElements<E: IslValue.Ion>(val elements: List<VariablyOccurring<TypeRefSafe<E>>>): Constraint<IslValue.IslSequence>
    class Precision(val range: DiscreteRange<Int>): Constraint<IslValue.Ion.Decimal>
    class Regex(val pattern: String, val multiline: Boolean, val caseInsensitive: Boolean): Constraint<IslValue.IslText>
    class TimestampOffset(val offsets: List<TimestampOffsetValue>): Constraint<IslValue.Ion.Timestamp>
    class TimestampPrecision(val range: DiscreteRange<IonTimestampPrecision>): Constraint<IslValue.Ion.Timestamp>
    class Type<in T: IslValue>(val type: TypeRefSafe<T>): Constraint<T>
    class ValidValues<T: IslValue.IslContained>(val values: List<ValidValue<T>>): Constraint<T>
}

fun doSomething(a: Constraint<IslValue.Ion>, b: Constraint<IslValue.IslText>, c: Constraint<IslValue.Ion.String>): List<Constraint<IslValue.Ion.String>> {
    return listOf(a, b, c)
}

class TypeDefSafe<T: IslValue>(
    val name: String,
    val schemaId: String? = null,
    val constraints: List<Constraint<T>>,
    val openContent: Map<String, IonValue> = emptyMap()
) {
    fun asNamedRef() = TypeRefSafe.Reference<T>(name)
}


        /*
fun stringType(
    annotations: Constraint.Annotations? = null,
    type: Constraint.Type<IslValue.Ion.String>? = null,
    utf8ByteLength: Constraint.Utf8ByteLength? = null,
) = TypeRefSafe.Inline<IslValue.Ion.String>(
    listOfNotNull(
        annotations, 
        type,
        utf8ByteLength
    )
)

         */

sealed class TypeRefSafe<in T: IslValue> {
    class Reference<T: IslValue>(val name: String): TypeRefSafe<T>()
    sealed class BuiltIn<in T: IslValue>: TypeRefSafe<T>() {
        object IonBool: BuiltIn<IslValue.Ion.Bool>()
        object IonString: BuiltIn<IslValue.Ion.String>()
        object IonSymbol: BuiltIn<IslValue.Ion.Symbol>()
        object IonText: BuiltIn<IslValue.Ion.Text>()
    }
    abstract class Inline<T: IslValue>: TypeRefSafe<T>() {
        abstract val constraints: List<Constraint<T>>
    }
}

/*
val foo1234 = IonTextConstraints(
    type = Constraint.Type(TypeRefSafe.BuiltIn.IonSymbol)
)

 */

class IonTextConstraints (
    val annotations: Constraint.Annotations?,
    val type: Constraint.Type<IslValue.IslText>?,
    val validValues: Constraint.ValidValues<IslValue.Ion.Text>?,
    val regex: Constraint.Regex?,
    val utf8ByteLength: Constraint.Utf8ByteLength?,
    val codepointLength: Constraint.CodepointLength?,
): TypeRefSafe.Inline<IslValue.Ion.Text>() {
    override val constraints: List<Constraint<IslValue.Ion.Text>>
        get() = listOfNotNull(annotations, type, validValues, regex, utf8ByteLength, codepointLength)
}

class IslSequenceConstraints<Element: IslValue.Ion>(
    val annotations: Constraint.Annotations?,
    val type: Constraint.Type<IslValue.IslSequence>?,
    val validValues: Constraint.ValidValues<IslValue.Ion.Sequence>?,
    val allOf:  Constraint.AllOf<IslValue.IslSequence>?,
    val orderedElements: Constraint.OrderedElements<Element>?,
    val elements: Constraint.Element<Element>?,
    val contains: Constraint.Contains<IslValue.Ion.Sequence, Element>?,
): TypeRefSafe.Inline<IslValue.Ion.Sequence>() {
    override val constraints: List<Constraint<IslValue.Ion.Sequence>>
        get() = listOfNotNull(annotations, type, validValues, allOf, orderedElements, elements, contains)
}


sealed class ValidValue<out ValueType : IslValue.IslContained> {
    class Value<T : IslValue.IslContained>(val value: T) : ValidValue<T>()
    class IonNumberRange(start: ContinuousRange.Bound<BigDecimal>?, end: ContinuousRange.Bound<BigDecimal>?) : ValidValue<IslValue.Ion.Number>(), IslRange<BigDecimal, ContinuousRange<BigDecimal>> by ContinuousRange(start, end)
    class IonTimestampRange(start: ContinuousRange.Bound<Instant>?, end: ContinuousRange.Bound<Instant>?) : ValidValue<IslValue.Ion.Timestamp>(), IslRange<Instant, ContinuousRange<Instant>> by ContinuousRange(start, end)
}


/*
interface ConstraintSetModel<T: Marker> {
    abstract val constraints: List<ConstraintModel<T>>

    interface HasElements<T: Marker.Container, E: Marker.Contained>: ConstraintSetModel<T>

    class Any(override val constraints: List<ConstraintModel<in Marker>>): ConstraintSetModel<Marker>

    // Maybe? object Unconstrained: HasElements<Nothing, Nothing>



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
*/


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