package com.amazon.ionschema.cli.merge.rules

import com.amazon.ion.IonInt
import com.amazon.ion.IonList
import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue
import com.amazon.ionschema.cli.merge.Constraint
import com.amazon.ionschema.cli.merge.MergeRule
import com.amazon.ionschema.cli.merge.MergeRule.Outcome
import java.math.BigInteger

object MergeFieldsConstraints: MergeRule {

    override val repeatable: Boolean = true

    override fun run(ionSystem: IonSystem, constraintBag: Set<Constraint>, merge: (IonValue, IonValue) -> IonStruct): Outcome {
        if (constraintBag.isEmpty()) return Outcome.NoChange
        val fieldsConstraints = constraintBag.filter { (name) -> name == "fields" }
        if (fieldsConstraints.size <= 1) return Outcome.NoChange

        val fieldsA = fieldsConstraints[0].value as IonStruct
        val fieldsB = fieldsConstraints[1].value as IonStruct


        val ion = fieldsA.system
        val fieldNames = fieldsA.map { it.fieldName }.toSet() + fieldsB.map { it.fieldName }.toSet()
        val aIsClosed = fieldsA.hasTypeAnnotation("closed")
        val bIsClosed = fieldsB.hasTypeAnnotation("closed")

        val newFields = ion.newEmptyStruct()

        fieldNames.forEach {
            // This is safe because the `fields` constraint doesn't allow duplicate field names
            val a = fieldsA[it]
            val b = fieldsB[it]

            if (a == null) {
                if (aIsClosed) {
                    // Field name is present in B but not allowed in A
                    if (b.isOptionalField()) {
                        // Skip adding it
                    } else {
                        // Unsatisfiable requirement
                        return Outcome.Unsatisfiable
                    }
                } else {
                    newFields.add(it, b.clone())
                }
            } else if (b == null) {
                if (bIsClosed) {
                    // Field name is present in A but not allowed in B
                    if (a.isOptionalField()) {
                        // Skip adding it
                    } else {
                        // Unsatisfiable requirement
                        return Outcome.Unsatisfiable
                    }
                } else {
                    newFields.add(it, a.clone())
                }
            } else {
                newFields.add(it, merge(a, b))
            }
        }
        if (aIsClosed || bIsClosed) newFields.addTypeAnnotation("closed")

        return Outcome.Diff(
            add = setOf(Constraint("fields", newFields)),
            delete = setOf(fieldsConstraints[0], fieldsConstraints[1])
        )
    }

    private fun IonValue.isOptionalField(): Boolean {
        if (this !is IonStruct) return true
        return when (val occurs = this["occurs"]) {
            is IonSymbol ->  occurs.stringValue() == "optional"
            is IonInt -> false // occurs: 0 is not allowed, so any int must mean it's required
            is IonList -> if (occurs[0] is IonSymbol) true else (occurs[0] as IonInt).let {
                it.bigIntegerValue().equals(BigInteger.ZERO) && !it.hasTypeAnnotation("exclusive")
            }
            else -> true // Fields are optional by default
        }
    }
}
