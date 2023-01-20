package com.amazon.ionschema.internal.model

import com.amazon.ion.IonContainer
import com.amazon.ion.IonDecimal
import com.amazon.ion.IonFloat
import com.amazon.ion.IonList
import com.amazon.ion.IonLob
import com.amazon.ion.IonNumber
import com.amazon.ion.IonSequence
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonText
import com.amazon.ion.IonTimestamp
import com.amazon.ion.IonValue
import com.amazon.ionschema.IonSchemaException
import com.amazon.ionschema.internal.model.ValidValue.IonNumberRange
import com.amazon.ionschema.internal.model.ValidValue.IonTimestampRange
import com.amazon.ionschema.internal.util.IonTimestampPrecision
import java.math.BigDecimal
import java.time.Instant
import kotlin.Comparator


sealed class Result<T> constructor() {
    private data class Ok<T>(val value: T): Result<T>()
    private data class Err<T>(val message: String): Result<T>()

    val isSuccess: Boolean get() = this !is Err
    val isFailure: Boolean get() = this is Err

    fun getOrNull(): T? =
        when (this) {
            is Ok -> value
            is Err -> null
        }

    fun getOrThrow(): T =
        when (this) {
            is Err -> throw IonSchemaException(message)
            is Ok -> value
        }

    fun errOrNull(): String? =
        when (this) {
            is Err -> message
            is Ok -> null
        }

    companion object {
        fun <T> success(value: T): Result<T> = Ok(value)
        fun <T> failure(message: String): Result<T> = Err(message)
    }
}

class UnsafeType(
    val annotations: UnsafeType?,
    val anyOf: List<List<UnsafeType>>,
    val areFieldNamesDistinct: Boolean?,
    val byteLength: DiscreteIntRange?,
    val codepointLength: DiscreteIntRange?,
    val containerLength: DiscreteIntRange?,
    val contains: List<IonValue>,
    val distinct: Boolean?,
    val element: UnsafeType?,
    val exponent: DiscreteIntRange?,
    val fieldNames: SymbolTokenType?,
    val fields: Map<String, VariablyOccurring<UnsafeType>>,
    val fieldsClosed: Boolean?,
    val ieee754Float: DiscreteIntRange?,
    val name: String?,
    val not: List<UnsafeType>,
    val oneOf: List<List<UnsafeType>>,
    val orderedElements: List<List<VariablyOccurring<UnsafeType>>>,
    val precision: DiscreteIntRange?,
    val regex: List<Regex>,
    val timestampOffset: List<String>,
    val timestampPrecision: DiscreteRange<IonTimestampPrecision>?,
    val type: List<UnsafeType>,
    val userContent: Map<String, List<IonValue>>,
    val utf8ByteLength: DiscreteIntRange?,
    val validValues: List<ValidValue<IonValue>>?,
) {

    fun toAnyType(): Result<AnyType> {
        return AnyTypeImpl.fromUnsafe()
    }

}

interface AnyType {
    val name: String?
    /** Also includes "all_of" **/
    val type: List<AnyType>
    val not: List<AnyType>
    val oneOf: List<List<AnyType>>
    val anyOf: List<List<AnyType>>
    val annotations: AnnotationsType?
    val validValues: List<ValidValue<IonValue>>?
    val userContent: Map<String, List<IonValue>>
}

class AnyTypeImpl private constructor(private val unsafeType: UnsafeType) {
    companion object {
        fun fromUnsafe(): Result<AnyType> {
            TODO()
        }
    }
}

interface NumberType : AnyType {
    override val validValues: List<ValidValue<IonNumber>>?
}

interface FloatType : NumberType {
    override val type: List<TextType>
    val ieee754Float: DiscreteIntRange?
    override val validValues: List<ValidValue<IonFloat>>?
}

interface DecimalType : NumberType {
    override val type: List<DecimalType>
    val precision: DiscreteIntRange?
    val exponent: DiscreteIntRange?
    override val validValues: List<ValidValue<IonDecimal>>?
}

internal interface TimestampType : AnyType {
    override val type: List<TextType>
    val timestampPrecision: DiscreteRange<IonTimestampPrecision>?
    val timestampOffset: List<String>
    override val validValues: List<ValidValue<IonTimestamp>>?
}

interface TextType : AnyType {
    override val type: List<TextType>
    val utf8ByteLength: DiscreteIntRange?
    val codepointLength: DiscreteIntRange?
    val regex: List<Regex>
    override val validValues: List<ValidValue<IonText>>?
}

interface LobType : AnyType {
    override val type: List<TextType>
    val byteLength: DiscreteIntRange?
    override val validValues: List<ValidValue<IonLob>>?
}

interface ContainerType : AnyType {
    override val type: List<ContainerType>
    val element: AnyType?
    val distinct: Boolean?
    val containerLength: DiscreteIntRange?
    val contains: List<IonValue>
    override val validValues: List<ValidValue<IonContainer>>?
}

interface SequenceType : ContainerType {
    override val type: List<SequenceType>
    val orderedElements: List<List<VariablyOccurring<AnyType>>>
    override val validValues: List<ValidValue<IonSequence>>?
}

interface StructType : ContainerType {
    override val type: List<StructType>
    val fields: Map<String, VariablyOccurring<AnyType>>
    val fieldsClosed: Boolean?
    val fieldNames: SymbolTokenType?
    val areFieldNamesDistinct: Boolean?
    override val validValues: List<ValidValue<IonStruct>>?
}

interface DocumentType : SequenceType {
    override val annotations: Nothing?
    override val validValues: Nothing?
}

/**
 * For annotations constraint
 */
interface AnnotationsType : SequenceType {
    override val annotations: Nothing?
    override val element: SymbolTokenType?
    override val validValues: List<ValidValue<IonList>>?
}

/**
 * For field names constraint
 */
interface SymbolTokenType : TextType {
    override val annotations: Nothing?
    override val validValues: List<ValidValue<IonSymbol>>?
}

class VariablyOccurring<T>(val type: T, val occurs: DiscreteIntRange)

typealias DiscreteIntRange = DiscreteRange<Int>

interface IslRange<T, ImplementingType : IslRange<T, ImplementingType>> {
    operator fun contains(value: T): Boolean
    fun intersect(other: ImplementingType): ImplementingType
    fun isEmpty(): Boolean
}

class DiscreteRange<T : Comparable<T>>(val start: T?, val endInclusive: T?) : IslRange<T, DiscreteRange<T>> {

    private val START_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder<T>())
    private val END_COMPARATOR = Comparator.nullsLast(Comparator.naturalOrder<T>())

    override fun intersect(other: DiscreteRange<T>): DiscreteRange<T> {
        val newStart = maxOf(this.start, start, START_COMPARATOR)
        val newEndInclusive = minOf(this.endInclusive, endInclusive, END_COMPARATOR)
        return DiscreteRange(newStart, newEndInclusive)
    }

    override operator fun contains(value: T): Boolean = (start == null || start <= value) && (endInclusive == null || value <= endInclusive)

    /**
     * Checks whether the range is empty.
     *
     * The range is empty if its start value is greater than the end value.
     */
    override fun isEmpty(): Boolean = start != null && endInclusive != null && start > endInclusive

    override fun equals(other: Any?): Boolean =
        other is DiscreteRange<*> && (
            isEmpty() && other.isEmpty() ||
                start == other.start && endInclusive == other.endInclusive
            )

    override fun hashCode(): Int =
        if (isEmpty()) -1 else (31 * start.hashCode() + endInclusive.hashCode())

    override fun toString(): String = "$start..$endInclusive"
}

class ContinuousRange<T : Comparable<T>>(val start: Bound<T>?, val end: Bound<T>?) : IslRange<T, ContinuousRange<T>> {

    private val START_COMPARATOR = Comparator.nullsFirst(Comparator.comparing<Bound<T>, T> { it.value }.thenBy { it.exclusive })
    private val END_COMPARATOR = Comparator.nullsLast(Comparator.comparing<Bound<T>, T> { it.value }.thenBy { !it.exclusive })

    data class Bound<T : Comparable<T>>(val value: T, val exclusive: Boolean)

    override fun intersect(other: ContinuousRange<T>): ContinuousRange<T> {
        val newStart = maxOf(this.start, start, START_COMPARATOR)
        val newEndInclusive = minOf(this.end, end, END_COMPARATOR)
        return ContinuousRange(newStart, newEndInclusive)
    }

    override operator fun contains(value: T): Boolean {
        val isInLowerBound = null == start || if (start.exclusive) value > start.value else value >= start.value
        val isInUpperBound = null == end || if (end.exclusive) value < end.value else value <= end.value
        return isInLowerBound && isInUpperBound
    }

    /**
     * Checks whether the range is empty.
     *
     * The range is empty if its start value is greater than the end value.
     */
    override fun isEmpty(): Boolean {
        if (!(start != null && end != null)) return false
        val exclusive = start.exclusive || end.exclusive
        return if (exclusive) start.value >= end.value else start.value > end.value
    }

    override fun equals(other: Any?): Boolean =
        other is ContinuousRange<*> && (
            isEmpty() && other.isEmpty() ||
                start == other.start && end == other.end
            )

    override fun hashCode(): Int =
        if (isEmpty()) -1 else (31 * start.hashCode() + end.hashCode())

    override fun toString(): String = "$start..$end"
}

sealed class ValidValue<out ValueType : IonValue> {
    class Value<T : IonValue>(val value: T) : ValidValue<T>()
    class IonNumberRange(start: ContinuousRange.Bound<BigDecimal>?, end: ContinuousRange.Bound<BigDecimal>?) : ValidValue<IonNumber>(), IslRange<BigDecimal, ContinuousRange<BigDecimal>> by ContinuousRange(start, end)
    class IonTimestampRange(start: ContinuousRange.Bound<Instant>?, end: ContinuousRange.Bound<Instant>?) : ValidValue<IonTimestamp>(), IslRange<Instant, ContinuousRange<Instant>> by ContinuousRange(start, end)
}




