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

import com.amazon.ion.IonList
import com.amazon.ion.IonSequence
import com.amazon.ion.IonStruct
import com.amazon.ion.IonValue
import com.amazon.ionschema.IonSchemaVersion.v2_0
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Violation
import com.amazon.ionschema.ViolationChild
import com.amazon.ionschema.Violations
import com.amazon.ionschema.internal.TypeReference
import com.amazon.ionschema.internal.util.IntRange
import com.amazon.ionschema.internal.util.islName
import com.amazon.ionschema.internal.util.islRequire
import com.amazon.ionschema.internal.util.islRequireIonTypeNotNull

/**
 * Implements the ordered_element constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/isl-1-0/spec#ordered_elements
 * @see https://amzn.github.io/ion-schema/docs/isl-2-0/spec#ordered_elements
 */
internal class OrderedElements(
    ion: IonValue,
    private val schema: Schema
) : ConstraintBase(ion) {

    private val nfa: NFA<IonValue, Violation> = run {
        islRequireIonTypeNotNull<IonList>(ion) { "Invalid ordered_elements constraint: $ion" }
        if (schema.ionSchemaLanguageVersion >= v2_0) {
            islRequire(ion.typeAnnotations.isEmpty()) { "ordered_elements list may not be annotated. Found: $ion" }
        }

        val stateBuilder = OrderedElementsNfaStatesBuilder()
        ion.forEach {
            val occursRange = IntRange.toIntRange((it as? IonStruct)?.get("occurs")) ?: IntRange.REQUIRED
            val typeRef = TypeReference.create(it, schema, variablyOccurring = true)
            stateBuilder.addState(
                min = occursRange.lower,
                max = occursRange.upper,
                matches = { Violation().let { v -> typeRef().validate(it, v); NFA.State.Decision(v.isValid(), v.singleOrNull()) } },
                description = it.toString(),
            )
        }
        NFA(stateBuilder.build())
    }

    override fun validate(value: IonValue, issues: Violations) {
        validate(value, issues, debug = false)
    }

    // Visible for testing
    internal fun validate(value: IonValue, issues: Violations, debug: Boolean) {
        validateAs<IonSequence>(value, issues) { v ->
            val outcome = nfa.matches(v, debug)

            if (outcome is NFA.Outcome.IsNotMatch<*>) {
                val describe = fun (s: NFA.State<*, *>): String = if (s == NFA.State.Final) "<END OF ${value.type.islName}>" else s.description

                val children = mutableListOf<ViolationChild>()
                outcome.reasons.groupBy { it.eventId }
                    .forEach {(idx, reasons) ->
                        val endOfSequenceEvent = idx == v.size
                        val valueViolation = ViolationChild(
                            index = if (endOfSequenceEvent) null else idx,
                            fieldName = if (endOfSequenceEvent) "<END OF ${value.type.islName}>" else null,
                            value = v.getOrNull(idx)
                        )

                        reasons.forEach {
                            val message = when (it) {
                                is NFA.InvalidTransition.CannotEnterState<*, *> -> "does not match: ${describe(it.toState)}"
                                is NFA.InvalidTransition.CannotExitState<*, *> -> "min occurs not reached: ${describe(it.fromState)}"
                                is NFA.InvalidTransition.CannotReenterState<*, *> -> "max occurs already reached: ${describe(it.state)}"
                            }
                            valueViolation.add(
                                Violation(message = message).apply {
                                    if (it is NFA.InvalidTransition.CannotEnterState<*, *> && it.reason is Violations) {
                                        it.reason.violations.forEach{ this.add(it) }
                                        it.reason.children.forEach{ this.add(it) }
                                    }
                                }
                            )
                        }

                        children.add(valueViolation)
                    }

                issues.add(
                    Violation(
                        ion, "ordered_elements_mismatch",
                        "one or more ordered elements don't match specification"
                    ).apply {
                        children.forEach { add(it) }
                    }
                )
            }
        }
    }
}
