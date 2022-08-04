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

package com.amazon.ionschema

import com.amazon.ionelement.api.*
import com.amazon.ionschema.internal.util.truncate

/**
 * Indicates whether a value validated successfully against a [Type],
 * and if not, provides details indicating why not.
 *
 * Instances of this class contain zero or more Violation objects,
 * and zero or more ViolationChild objects.  Violation objects indicate
 * constraints that failed at the current level of the value, whereas
 * ViolationChild objects correspond to child values that resulted in
 * Violations.
 *
 * @property[violations] List of constraint violations at the current
 *   level of the value.
 */
open class Violations internal constructor (
    private var shortCircuit: Boolean = false,
    private val childrenAllowed: Boolean = true,
    val violations: MutableList<Violation> = mutableListOf()
) : Iterable<Violation> by violations {

    /**
     * Represents violations corresponding to specific fields
     * of elements in a sequence.
     */
    val children: MutableList<ViolationChild> = mutableListOf()

    /**
     * Returns `true` if no violations were found; otherwise `false`.
     */
    fun isValid(): Boolean = violations.isEmpty() && children.isEmpty()

    internal fun add(violation: Violation): Boolean {
        (violation as Violations).shortCircuit = shortCircuit
        violations.add(violation)
        if (shortCircuit) throw ShortCircuitValidationException()
        return true
    }

    internal fun add(child: ViolationChild) {
        if (!childrenAllowed) {
            throw IllegalArgumentException("Children cannot be added to this object")
        }
        (child as Violations).shortCircuit = shortCircuit
        children.add(child)
        if (shortCircuit) throw ShortCircuitValidationException()
    }

    internal inner class Checkpoint(
        private val violationCount: Int,
        private val childCount: Int
    ) {
        fun isValid() = this@Violations.violations.size == violationCount &&
            this@Violations.children.size == childCount
    }

    internal fun checkpoint() = Checkpoint(violations.size, children.size)

    /**
     * Provides a user-friendly string representation of [Violation]s,
     * if there are any;  otherwise returns an empty string.
     */
    override fun toString(): String {
        if (violations.size > 0 || children.size > 0) {
            val sb = StringBuilder("Validation failed:").appendln()
            appendTo(sb)
            return sb.toString()
        }
        return ""
    }

    private fun appendTo(sb: StringBuilder, depth: Int = 0) {
        violations.forEach {
            sb.append(" ".repeat(2 * depth))
            sb.append("- ").append(it.message).appendln()
            (it as Violations).appendTo(sb, depth + 1)
        }
        children.forEach {
            sb.append(" ".repeat(2 * depth))
            sb.append("- ")
            it.fieldName?.let { sb.append(it) }
            it.index?.let { sb.append("[").append(it).append("]") }
            it.value?.let { sb.append(": ").append(it.toString().truncate(20)) }
            sb.appendln()
            (it as Violations).appendTo(sb, depth + 1)
        }
    }
}

/**
 * Describes a constraint violation, including a violation code and message.
 *
 * @property[constraint] Definition of the constraint that created this violation.
 * @property[code] An error code that briefly indicates the type of the violation.
 * @property[message] A description of the cause of the violation.
 */
class Violation(
    var constraint: StructField? = null,
    var code: String? = null,
    var message: String? = null
) : Violations()

/**
 * References a specific struct fieldName or index into a list/sexp/document
 * within a hierarchical [Violations] object.
 *
 * @property[fieldName] Within a struct, the name of the field this object corresponds to.
 * @property[index] Within a sequence, the index of the element this object corresponds to.
 * @property[value] The child value this object corresponds to.
 */
class ViolationChild internal constructor (
    val fieldName: String? = null,
    val index: Int? = null,
    var value: IonElement? = null
) : Violations() {

    internal fun addValue(v: IonElement, fieldName: String? = null) {
        if (value == null) {
            value = v
        } else {
            if (fieldName != null) {
                when (val maybeStruct = value) {
                    is StructElement -> {
                        value = ionStructOf(
                            fields = maybeStruct.fields + field(fieldName, v),
                            annotations = maybeStruct.annotations,
                        )
                    }
                    else -> {
                        val tmp = value!!
                        value = ionStructOf(fieldName to tmp)
                    }
                }
            }
        }
    }
}

/**
 * This exception is only thrown to indicate that validation processing
 * can be short-circuited (specifically in cases where [Type.isValid()] was
 * invoked, and at least one constraint has been violated).
 */
internal class ShortCircuitValidationException : Exception()
