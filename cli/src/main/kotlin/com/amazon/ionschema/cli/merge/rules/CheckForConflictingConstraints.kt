package com.amazon.ionschema.cli.merge.rules

import com.amazon.ion.IonList
import com.amazon.ion.IonNumber
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSystem
import com.amazon.ion.IonTimestamp
import com.amazon.ion.IonType
import com.amazon.ion.IonValue
import com.amazon.ionschema.cli.merge.Constraint
import com.amazon.ionschema.cli.merge.MergeRule
import com.amazon.ionschema.cli.merge.MergeRule.Outcome
import com.amazon.ionschema.cli.util.retain

object CheckForConflictingConstraints: MergeRule {

    override val repeatable: Boolean = false

    private val NUMBER_TYPES = setOf(IonType.DECIMAL, IonType.FLOAT, IonType.INT)
    private val TEXT_TYPES = setOf(IonType.STRING, IonType.SYMBOL)
    private val LOB_TYPES = setOf(IonType.BLOB, IonType.CLOB)

    override fun run(ionSystem: IonSystem, constraintBag: Set<Constraint>, merge: (IonValue, IonValue) -> IonStruct): Outcome {

        if (constraintBag.isEmpty()) return Outcome.NoChange

        val possibleTypes = mutableSetOf(*IonType.values())
        var canBeNull = true

        constraintBag.forEach { (fieldName, value) ->
            when (fieldName) {
                "timestamp_offset", "timestamp_precision" -> {
                    possibleTypes.retain(IonType.TIMESTAMP)
                    canBeNull = false
                }
                "precision", "exponent" -> {
                    possibleTypes.retain(IonType.DECIMAL)
                    canBeNull = false
                }
                "codepoint_length", "utf8_byte_length", "regex" -> {
                    possibleTypes.retain(IonType.SYMBOL, IonType.STRING)
                    canBeNull = false
                }
                "byte_length" -> {
                    possibleTypes.retain(IonType.CLOB, IonType.BLOB)
                    canBeNull = false
                }
                "ieee754_float" -> {
                    possibleTypes.retain(IonType.FLOAT)
                    canBeNull = false
                }
                "fields", "field_names" -> {
                    possibleTypes.retain(IonType.STRUCT)
                    canBeNull = false
                }
                "container_length", "element", "contains" -> {
                    possibleTypes.retain(IonType.DATAGRAM, IonType.STRUCT, IonType.LIST, IonType.SEXP)
                    canBeNull = false
                }
                "ordered_elements" -> {
                    possibleTypes.retain(IonType.DATAGRAM, IonType.LIST, IonType.SEXP)
                    canBeNull = false
                }
                "annotations" -> {
                    possibleTypes.remove(IonType.DATAGRAM)
                }
                "valid_values" -> {
                    val validValuesTypes = mutableSetOf<IonType>()

                    if (value.isRange()) {
                        validValuesTypes.addAll(getTypesForRange(value as IonList))
                    } else {
                        var hasNullValue = false
                        (value as IonList).forEach {
                            if (it.isRange()) {
                                validValuesTypes.addAll(getTypesForRange(it as IonList))
                            } else {
                                validValuesTypes.add(it.type)
                                if (it.isNullValue) hasNullValue = true
                            }
                        }
                        canBeNull = hasNullValue
                    }

                    possibleTypes.retainAll(validValuesTypes)
                }
                else -> {}
            }
        }

        val prefix = if (canBeNull) "$" else ""

        return if (possibleTypes.size == 1) {
            val type = when (val t = possibleTypes.single()) {
                IonType.DATAGRAM -> "document"
                else -> "${prefix}${t.name.lowercase()}"
            }
            Outcome.Diff(add = setOf(Constraint("type", ionSystem.newSymbol(type))))
        } else when (possibleTypes) {
            emptySet<IonType>() -> Outcome.Unsatisfiable
            NUMBER_TYPES -> Outcome.Diff(add = setOf(Constraint("type", ionSystem.newSymbol("${prefix}number"))))
            LOB_TYPES -> Outcome.Diff(add = setOf(Constraint("type", ionSystem.newSymbol("${prefix}lob"))))
            TEXT_TYPES -> Outcome.Diff(add = setOf(Constraint("type", ionSystem.newSymbol("${prefix}text"))))
            else -> Outcome.NoChange
        }
    }

    private fun IonValue.isRange(): Boolean {
        return this is IonList && this.hasTypeAnnotation("range")
    }

    private fun getTypesForRange(range: IonList): Collection<IonType> {
        return if (range[0] is IonNumber || range[1] is IonNumber) {
            NUMBER_TYPES
        } else if (range[0] is IonTimestamp || range[1] is IonNumber) {
            listOf(IonType.TIMESTAMP)
        } else {
            TODO("Unreachable")
        }
    }
}
