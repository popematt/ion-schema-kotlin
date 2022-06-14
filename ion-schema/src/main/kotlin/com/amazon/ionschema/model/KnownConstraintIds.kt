/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package com.amazon.ionschema.model

import com.amazon.ionschema.model.constraints.AllOfConstraint
import com.amazon.ionschema.model.constraints.AnnotationsConstraint
import com.amazon.ionschema.model.constraints.AnyOfConstraint
import com.amazon.ionschema.model.constraints.ByteLengthConstraint
import com.amazon.ionschema.model.constraints.CodepointLengthConstraint
import com.amazon.ionschema.model.constraints.ContainerLengthConstraint
import com.amazon.ionschema.model.constraints.ContainsConstraint
import com.amazon.ionschema.model.constraints.ElementConstraint
import com.amazon.ionschema.model.constraints.FieldsConstraint
import com.amazon.ionschema.model.constraints.NotConstraint
import com.amazon.ionschema.model.constraints.OneOfConstraint
import com.amazon.ionschema.model.constraints.OrderedElementsConstraint
import com.amazon.ionschema.model.constraints.PrecisionConstraint
import com.amazon.ionschema.model.constraints.RegexConstraint
import com.amazon.ionschema.model.constraints.ScaleConstraint
import com.amazon.ionschema.model.constraints.TimestampOffsetConstraint
import com.amazon.ionschema.model.constraints.TimestampPrecisionConstraint
import com.amazon.ionschema.model.constraints.TypeConstraint
import com.amazon.ionschema.model.constraints.Utf8ByteLengthConstraint
import com.amazon.ionschema.model.constraints.ValidValuesConstraint

/**
 * A pseudo-enum of all known (ie. built-in) [ConstraintId]s.
 * We can't use a real enum because it can't extend [ConstraintId]`<T>` without specifying `T`.
 */
object KnownConstraintIds {
    val AllOf = AllOfConstraint.ID
    val Annotations = AnnotationsConstraint.ID
    val AnyOf = AnyOfConstraint.ID
    val ByteLength = ByteLengthConstraint.ID
    val CodepointLength = CodepointLengthConstraint.ID
    val ContainerLength = ContainerLengthConstraint.ID
    val Contains = ContainsConstraint.ID
    val Element = ElementConstraint.ID
    val Fields = FieldsConstraint.ID
    val Not = NotConstraint.ID
    val OneOf = OneOfConstraint.ID
    val OrderedElements = OrderedElementsConstraint.ID
    val Precision = PrecisionConstraint.ID
    val Regex = RegexConstraint.ID
    val Scale = ScaleConstraint.ID
    val TimestampOffset = TimestampOffsetConstraint.ID
    val TimestampPrecision = TimestampPrecisionConstraint.ID
    val Type = TypeConstraint.ID
    val Utf8ByteLength = Utf8ByteLengthConstraint.ID
    val ValidValues = ValidValuesConstraint.ID

    fun values() = listOf(
        AllOf, Annotations, AnyOf, ByteLength, CodepointLength, ContainerLength, Contains, Element, Fields, Not, OneOf,
        OrderedElements, Precision, Regex, Scale, TimestampOffset, TimestampPrecision, Type, Utf8ByteLength, ValidValues
    )
}
