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

import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.ListElement
import com.amazon.ionelement.api.TimestampElement
import org.junit.jupiter.api.Test

internal class RangeIonTimestampTest : AbstractRangeTest<IonElement>(RangeType.ION_TIMESTAMP) {

    override fun rangeOf(ion: ListElement): Range<IonElement> = RangeFactory.rangeOf<TimestampElement>(ion, RangeType.ION_TIMESTAMP) as Range<IonElement>

    @Test
    fun range_timestamp_inclusive() {
        assertValidRangeAndValues(
            "range::[2022-01-01T00:00Z, 2022-12-31T00:00Z]",
            listOfIonElements(
                "2022-01-01T00:00Z",
                "2022-02-02T00:00Z",
                "2022-04-03T00:00Z",
            ),
            listOfIonElements(
                "2021-01-01T00:00Z",
                "2023-02-02T00:00Z",
            )
        )
    }
}
