package com.amazon.ionschema.cli.merge

import com.amazon.ion.IonStruct
import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue

data class Constraint(val name: String, val value: IonValue) {
    constructor(value: IonValue): this(value.fieldName!!, value)
}

interface MergeRule {
    val repeatable: Boolean

    fun run(ionSystem: IonSystem, constraintBag: Set<Constraint>, merge: (IonValue, IonValue) -> IonStruct): Outcome

    sealed class Outcome {
        class Replacement(val value: Set<Constraint>): Outcome()
        class Diff(val add: Set<Constraint> = emptySet(), val delete: Set<Constraint> = emptySet()): Outcome()
        object Unsatisfiable: Outcome()
        object NoChange: Outcome()
    }
}
