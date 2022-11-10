package com.amazon.ionschema.streaming

import com.amazon.ion.Decimal
import com.amazon.ion.IntegerSize
import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.Span
import com.amazon.ion.SpanProvider
import com.amazon.ion.Timestamp
import com.amazon.ionschema.IonSchemaException
import com.amazon.ionschema.IonSchemaValidationException
import com.amazon.ionschema.internal.util.RangeInt
import java.math.BigDecimal
import java.math.BigInteger

sealed class ValidationResult {
    object Ok : ValidationResult()
    class Err(val message: String, val span: Span? = null, val children: List<Err> = emptyList()) : ValidationResult()
}

/**
 * View of an IonReader that has no mutator methods
 */
interface IonReaderValue {
    fun <T : Any> asFacet(facetType: Class<T>?): T?
    fun getType(): IonType
    fun getIntegerSize(): IntegerSize
    fun getTypeAnnotations(): Array<String>
    fun getFieldName(): String
    fun isNullValue(): Boolean
    fun isInStruct(): Boolean
    fun booleanValue(): Boolean
    fun intValue(): Int
    fun longValue(): Long
    fun bigIntegerValue(): BigInteger
    fun doubleValue(): Double
    fun bigDecimalValue(): BigDecimal
    fun decimalValue(): Decimal
    fun timestampValue(): Timestamp
    fun stringValue(): String
    fun byteSize(): Int
    fun newBytes(): ByteArray
    fun getBytes(buffer: ByteArray?, offset: Int, len: Int): Int

    companion object {
        private class IonReaderValueImpl(private val wrapped: IonReader) : IonReaderValue, IonReader by wrapped {
            override fun close() {
                TODO("Not allowed to mutate this IonReader")
            }
            override fun next(): IonType {
                TODO("Not allowed to mutate this IonReader")
            }
            override fun stepIn() {
                TODO("Not allowed to mutate this IonReader")
            }
            override fun stepOut() {
                TODO("Not allowed to mutate this IonReader")
            }
            override fun newBytes(): ByteArray {
                TODO("Not allowed to mutate this IonReader")
            }
        }
        operator fun invoke(reader: IonReader): IonReaderValue = IonReaderValueImpl(reader)
    }
}

class StreamingType(
    private val constraints: List<StreamingConstraint>,
    private val states: List<StatefulStreamingConstraint.ConstraintState>
) {

    fun handleNext(reader: IonReaderValue): ValidationResult {
        val stateErr = states.map { it.handleNext(reader) }
            .filterIsInstance<ValidationResult.Err>()
            .firstOrNull()

        stateErr?.let { return it }

        val constraintErr = constraints.map { it.handleNext(reader) }
            .filterIsInstance<ValidationResult.Err>()
            .firstOrNull()

        constraintErr?.let { return it }

        return ValidationResult.Ok
    }

    fun handleStepIn(reader: IonReaderValue): StreamingType {
        val states = constraints.filterIsInstance<StatefulStreamingConstraint>()
            .map { it.handleStepIn(reader) }

        return StreamingType(emptyList(), states)
    }

    fun handleStepOut(): ValidationResult {
        return states.map { it.handleStepOut() }
            .filterIsInstance<ValidationResult.Err>()
            .firstOrNull()
            ?: ValidationResult.Ok
    }
}

class TypeConstraint(val constraints: List<StreamingConstraint>) : StatefulStreamingConstraint {
    override fun handleNext(reader: IonReaderValue): ValidationResult {
        return constraints.map { it.handleNext(reader) }
            .filterIsInstance<ValidationResult.Err>()
            .firstOrNull()
            ?: ValidationResult.Ok
    }
    override fun handleStepIn(reader: IonReaderValue): StatefulStreamingConstraint.ConstraintState {
        return constraints.filterIsInstance<StatefulStreamingConstraint>()
            .map { it.handleStepIn(reader) }
            .let { StateTracker(it) }
    }

    private class StateTracker(private val states: List<StatefulStreamingConstraint.ConstraintState>) : StatefulStreamingConstraint.ConstraintState {
        override fun handleNext(reader: IonReaderValue): ValidationResult {
            return states
                .map { it.handleNext(reader) }
                .filterIsInstance<ValidationResult.Err>()
                .firstOrNull()
                ?: ValidationResult.Ok
        }

        override fun handleStepOut(): ValidationResult {
            return states
                .map { it.handleStepOut() }
                .filterIsInstance<ValidationResult.Err>()
                .firstOrNull()
                ?: ValidationResult.Ok
        }
    }
}

interface StreamingConstraint {
    fun handleNext(reader: IonReaderValue): ValidationResult
}

interface StepInConstraint {
    fun handleStepIn(reader: IonReaderValue): StatefulStreamingConstraint.ConstraintState
}

interface StatefulStreamingConstraint : StreamingConstraint {
    fun handleStepIn(reader: IonReaderValue): ConstraintState
    interface ConstraintState {
        fun handleNext(reader: IonReaderValue): ValidationResult
        fun handleStepOut(): ValidationResult
    }
}

internal class ExponentConstraint(val range: RangeInt) : StreamingConstraint {
    override fun handleNext(reader: IonReaderValue): ValidationResult {
        if (reader.getType() != IonType.DECIMAL) {
            val span = reader.asFacet(SpanProvider::class.java)?.currentSpan()
            return ValidationResult.Err("not applicable for type ${reader.getType()}", span)
        }
        if (reader.isNullValue()) {
            val span = reader.asFacet(SpanProvider::class.java)?.currentSpan()
            return ValidationResult.Err("not applicable for null.${reader.getType()}", span)
        }
        val decimal = reader.decimalValue()

        if ((decimal.scale() * -1) !in range) {
            val span = reader.asFacet(SpanProvider::class.java)?.currentSpan()
            return ValidationResult.Err("exponent not in range $range: $decimal", span)
        }

        return ValidationResult.Ok
    }
}

internal class ContainerLengthConstraint(val range: RangeInt) : StatefulStreamingConstraint {

    override fun handleNext(reader: IonReaderValue): ValidationResult {
        if (!IonType.isContainer(reader.getType())) {
            val span = reader.asFacet(SpanProvider::class.java)?.currentSpan()
            return ValidationResult.Err("not applicable for type ${reader.getType()}", span)
        }
        if (reader.isNullValue()) {
            val span = reader.asFacet(SpanProvider::class.java)?.currentSpan()
            return ValidationResult.Err("not applicable for null.${reader.getType()}", span)
        }
        return ValidationResult.Ok
    }

    override fun handleStepIn(reader: IonReaderValue): StatefulStreamingConstraint.ConstraintState {
        return StateTracker(lazy { reader.asFacet(SpanProvider::class.java)?.currentSpan() })
    }

    private inner class StateTracker(private val span: Lazy<Span?>) : StatefulStreamingConstraint.ConstraintState {
        var length = 0

        override fun handleNext(reader: IonReaderValue): ValidationResult {
            length++
            return ValidationResult.Ok
        }

        override fun handleStepOut(): ValidationResult {
            return if (length in range) {
                ValidationResult.Ok
            } else {
                ValidationResult.Err("invalid container length; expected length $range, but was $length", span.value)
            }
        }
    }
}

internal class OneOfConstraint(val types: List<StreamingType>) : StatefulStreamingConstraint {
    override fun handleNext(reader: IonReaderValue): ValidationResult {
        val results = types.associateWith { it.handleNext(reader) }

        return if (!IonType.isContainer(reader.getType())) {
            val matchedTypeCount = results.count { (_, v) -> v is ValidationResult.Ok }

            if (matchedTypeCount == 1) {
                ValidationResult.Ok
            } else {
                ValidationResult.Err("matched $matchedTypeCount types")
            }
        } else {
            ValidationResult.Ok
        }
    }

    override fun handleStepIn(reader: IonReaderValue): StatefulStreamingConstraint.ConstraintState {
        return StateTracker(types.map { OneOfTypeState(it, it.handleStepIn(reader), it.handleNext(reader)) })
    }

    private data class OneOfTypeState(val type: StreamingType, val state: StatefulStreamingConstraint.ConstraintState, var result: ValidationResult)

    private class StateTracker(val typeStates: List<OneOfTypeState>) : StatefulStreamingConstraint.ConstraintState {
        override fun handleNext(reader: IonReaderValue): ValidationResult {
            typeStates.onEach {
                if (it.result is ValidationResult.Ok) {
                    it.result = it.state.handleNext(reader)
                }
            }
            return ValidationResult.Ok
        }

        override fun handleStepOut(): ValidationResult {
            typeStates.onEach {
                if (it.result is ValidationResult.Ok) {
                    it.result = it.state.handleStepOut()
                }
            }
            val matchedTypeCount = typeStates.count { (_, _, v) -> v is ValidationResult.Ok }
            return if (matchedTypeCount == 1) {
                ValidationResult.Ok
            } else {
                ValidationResult.Err("matched $matchedTypeCount types")
            }
        }
    }
}
