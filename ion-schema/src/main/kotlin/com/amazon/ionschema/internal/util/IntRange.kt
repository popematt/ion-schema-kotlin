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

package com.amazon.ionschema.internal.util

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.*
import com.amazon.ionelement.api.ListElement
import com.amazon.ionschema.InvalidSchemaException
import java.math.BigInteger

internal interface IntRange {
    companion object {
        private val ION = IonSystemBuilder.standard().build()

        internal val OPTIONAL: IntRange = toIntRange(ionSymbol("optional"))!!
        internal val REQUIRED: IntRange = toIntRange(ionSymbol("required"))!!

        fun toIntRange(ion: IonElement?) = when (ion) {
            is IntElement -> IntRangeForIonInt(ion)
            is ListElement -> IntRangeForListElement(ion)
            is SymbolElement -> IntRangeForIonSymbol(ion)
            null -> null
            else -> throw InvalidSchemaException("Unable to parse $ion as an int range")
        }
    }

    val lower: IntRangeBoundary
    val upper: IntRangeBoundary

    fun contains(value: Int) = contains(BigInteger.valueOf(value.toLong()))
    fun contains(value: BigInteger): Boolean
}

private class IntRangeForListElement(
    private val ion: ListElement
) : IntRange {

    companion object {
        private val MIN = IntRangeBoundaryConstant(-1)
        private val MAX = IntRangeBoundaryConstant(1)
    }

    init {
        checkRange(ion)
    }

    override val lower = when (ion[0]) {
        ionSymbol("min") -> MIN
        is IntElement -> IntRangeLowerBoundary(ion[0] as IntElement)
        else -> throw InvalidSchemaException("Unable to parse lower bound of $ion")
    }

    override val upper = when (ion[1]) {
        ionSymbol("max") -> MAX
        is IntElement -> IntRangeUpperBoundary(ion[1] as IntElement)
        else -> throw InvalidSchemaException("Unable to parse upper bound of $ion")
    }

    override fun contains(value: BigInteger) = lower <= value && upper >= value
    override fun toString() = ion.toString()
}

private class IntRangeForIonInt(
    private val ion: IntElement
) : IntRange {

    private val theValue = ion.bigIntegerValue

    private val boundary = object : IntRangeBoundary {
        override fun compareTo(other: BigInteger) = theValue.compareTo(other)
    }

    override val lower = boundary
    override val upper = boundary
    override fun contains(value: BigInteger) = theValue == value
    override fun toString() = ion.toString()
}

private class IntRangeForIonSymbol private constructor(
    private val ion: SymbolElement,
    delegate: IntRange
) : IntRange by delegate {

    internal constructor(ion: SymbolElement) :
        this(
            ion,
            when (ion.textValue) {
                "optional" -> IntRangeForListElement(loadSingleElement("range::[0, 1]").asList())
                "required" -> IntRangeForIonInt(loadSingleElement("1").asInt())
                else -> throw InvalidSchemaException("Unable to parse $ion as an int range")
            }
        )

    override fun toString() = ion.toString()
}

internal interface IntRangeBoundary : Comparable<Int> {
    override operator fun compareTo(other: Int) = compareTo(BigInteger.valueOf(other.toLong()))
    operator fun compareTo(other: BigInteger): Int
}

private abstract class IntRangeBoundaryBase(
    ion: IntElement,
    compareToResponseWhenExclusiveAndEqual: Int
) : IntRangeBoundary {

    private val value = ion.bigIntegerValue

    private val compareToResponseWhenEqual = when (RangeBoundaryType.forIon(ion)) {
        RangeBoundaryType.INCLUSIVE -> 0
        RangeBoundaryType.EXCLUSIVE -> compareToResponseWhenExclusiveAndEqual
    }

    override operator fun compareTo(other: BigInteger): Int {
        val compareResult = value.compareTo(other)
        return when (compareResult) {
            0 -> compareToResponseWhenEqual
            else -> compareResult
        }
    }
}

private class IntRangeLowerBoundary(ion: IntElement) : IntRangeBoundaryBase(ion, -1)
private class IntRangeUpperBoundary(ion: IntElement) : IntRangeBoundaryBase(ion, 1)
private class IntRangeBoundaryConstant(private val compareResult: Int) : IntRangeBoundary {
    override operator fun compareTo(other: BigInteger) = compareResult
}
