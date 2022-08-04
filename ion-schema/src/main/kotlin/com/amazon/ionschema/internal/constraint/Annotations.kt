/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ionschema.internal.constraint

import com.amazon.ionelement.api.*
import com.amazon.ionschema.InvalidSchemaException
import com.amazon.ionschema.Violation
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.Constraint
import com.amazon.ionschema.internal.util.IntRange

/**
 * Implements the annotations constraint.
 *
 * Invocations are delegated to either an Ordered or Unordered implementation.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#annotations
 */
internal class Annotations private constructor(
    ion: StructField,
    private val delegate: Constraint
) : ConstraintBase(ion), Constraint by delegate {

    constructor(ion: StructField) : this(ion, delegate(ion))

    companion object {
        private fun delegate(field: StructField): Constraint {
            val ion = field.value
            val requiredByDefault = "required" in ion.annotations
            if (ion !is ListElement || ion.isNull) {
                throw InvalidSchemaException("Expected annotations as a list, found: $ion")
            }
            val annotations = ion.values.map {
                Annotation(it.asSymbol(), requiredByDefault)
            }
            return if ("ordered" in ion.annotations) {
                OrderedAnnotations(field, annotations)
            } else {
                UnorderedAnnotations(field, annotations)
            }
        }
    }

    override val name = delegate.name
}

/**
 * Ordered implementation of the annotations constraint, backed by a [StateMachine].
 */
internal class OrderedAnnotations(
    ion: StructField,
    private val annotations: List<Annotation>
) : ConstraintBase(ion) {

    private val stateMachine: StateMachine

    init {
        val stateMachineBuilder = StateMachineBuilder().apply {
            if ("closed" !in ion.value.annotations) withOpenContent()
        }
        var state: State? = null
        val list = ion.value as ListElement
        list.values.forEachIndexed { idx, it ->
            val newState = State(
                occurs = when {
                    annotations[idx].isRequired -> IntRange.REQUIRED
                    else -> IntRange.OPTIONAL
                },
                isFinal = idx == list.size - 1
            )
            val annotationSymbol = it.withoutAnnotations()
            stateMachineBuilder.addTransition(state, EventIonElement(annotationSymbol), newState)

            state = newState
        }

        stateMachine = stateMachineBuilder.build()
    }

    override fun validate(value: IonElement, issues: Violations) {
        if (!stateMachine.matches(value.annotations.map { ionSymbol(it) }.iterator())) {
            issues.add(Violation(constraintField, "annotations_mismatch", "annotations don't match expectations"))
        }
    }
}

/**
 * Unordered implementation of the annotations constraint.
 */
internal class UnorderedAnnotations(
    ion: StructField,
    private val annotations: List<Annotation>
) : ConstraintBase(ion) {

    private val closedAnnotationStrings: List<String>? = if ("closed" in ion.value.annotations) (ion.value as ListElement).values.map { (it as SymbolElement).textValue } else null

    override fun validate(value: IonElement, issues: Violations) {
        val missingAnnotations = mutableListOf<Annotation>()
        annotations.forEach {
            if (it.isRequired && it.text !in value.annotations) {
                missingAnnotations.add(it)
            }
        }

        if (missingAnnotations.size > 0) {
            issues.add(
                Violation(
                    constraintField, "missing_annotation",
                    "missing annotation(s): " + missingAnnotations.joinToString { it.text }
                )
            )
        }

        closedAnnotationStrings?.let {
            if (!it.containsAll(value.annotations)) {
                issues.add(Violation(constraintField, "unexpected_annotation", "found one or more unexpected annotations"))
            }
        }
    }
}

internal class Annotation(
    ion: SymbolElement,
    requiredByDefault: Boolean
) {
    val text = ion.textValue

    val isRequired = when {
        "required" in ion.annotations -> true
        "optional" in ion.annotations -> false
        else -> requiredByDefault
    }
}
