package com.amazon.ionschema.cli.merge

import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder

internal typealias ConstraintBag = Set<Pair<String, IonValue>>

class TypeMerger(private val rules: List<MergeRule>, val ion: IonSystem = IonSystemBuilder.standard().build()) {

    private val UNSATISFIABLE: Set<Constraint> = setOf(Constraint("type", ion.newSymbol("nothing")))

    /**
     * Merges two types. The result will not have a name or a `type::` annotation; it is the responsibility of
     * the caller to re-add those if desired.
     *
     * @return a fresh, unparented IonStruct.
     */
    fun merge(typeA: IonValue, typeB: IonValue): IonStruct {
        val ion = typeA.system
        val typeA = normalizeTypeReference(typeA)
        val typeB = normalizeTypeReference(typeB)

        val constraintBag = (typeA.map { Constraint(it) } + typeB.map { Constraint(it) })
            .filter { (fieldName, _) -> fieldName != "name" }
            .toSet()

        val newConstraintBag = applyRules(constraintBag)

        return ion.newEmptyStruct().apply {
            newConstraintBag.forEach { (name, value) -> add(name, value.cloneIfParented()) }
        }
    }

    private fun applyRules(constraintBag: Set<Constraint>): Set<Constraint> {
        var newConstraintBag = constraintBag
        do {
            val oldConstraintBag = constraintBag
            rules.forEach {
                val innerOldConstraintBar = newConstraintBag
                do {
                    newConstraintBag = it.run(ion, newConstraintBag, this::merge).run {
                        when (this) {
                            is MergeRule.Outcome.Replacement -> value
                            is MergeRule.Outcome.Diff -> applyTo(newConstraintBag)
                            is MergeRule.Outcome.NoChange -> newConstraintBag
                            is MergeRule.Outcome.Unsatisfiable -> return UNSATISFIABLE
                        }
                    }
                } while (it.repeatable && innerOldConstraintBar == newConstraintBag)
            }
        } while (oldConstraintBag == newConstraintBag)

        return newConstraintBag
    }

    private fun MergeRule.Outcome.Diff.applyTo(bag: Set<Constraint>): Set<Constraint> {
        if (add.isEmpty() && delete.isEmpty()) return bag
        return (bag - delete) + add
    }

    /**
     * Normalizes to a type definition
     */
    private fun normalizeTypeReference(ref: IonValue): IonStruct {
        val ion = ref.system
        val newTypeRef = if (ref is IonSymbol) {
            ion.newEmptyStruct().apply { add("type", ref.clone()) }
        } else if (ref is IonStruct) {
            if (ref.containsKey("id")) {
                ion.newEmptyStruct().apply { add("type", ref.clone()) }
            } else {
                ref
            }
        } else {
            TODO("Unreachable")
        }
        return if (ref.hasTypeAnnotation("\$null_or")) {
            ion.newEmptyStruct().apply {
                add("any_of").newEmptyList().apply {
                    add().newSymbol("\$null")
                    add(newTypeRef.clone().apply { clearTypeAnnotations() })
                }
            }
        } else {
            newTypeRef
        }
    }

    private fun IonValue.cloneIfParented() = cloneIf { it.container != null }

    private inline fun IonValue.cloneIf(predicate: (IonValue) -> Boolean) = if (predicate(this)) clone() else this
}