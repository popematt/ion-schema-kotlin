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

import com.amazon.ionelement.api.LobElement
import com.amazon.ionelement.api.StructField

/**
 * Implements the byte_length constraint.
 *
 * @see https://amzn.github.io/ion-schema/docs/spec.html#byte_length
 */
internal class ByteLength(
    ion: StructField
) : ConstraintBaseIntRange<LobElement>(LobElement::class.java, ion) {

    override val violationCode = "invalid_byte_length"
    override val violationMessage = "invalid byte length %s, expected %s"

    override fun getIntValue(value: LobElement) = value.bytesValue.size()
}
