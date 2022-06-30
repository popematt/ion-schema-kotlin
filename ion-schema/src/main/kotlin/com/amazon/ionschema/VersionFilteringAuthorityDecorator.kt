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

import com.amazon.ion.IonStruct
import com.amazon.ion.IonText
import com.amazon.ion.IonValue
import com.amazon.ionschema.util.CloseableIterator
import com.amazon.ionschema.util.asCloseableIterator
import com.amazon.ionschema.util.asIterable

/**
 * Allows you to have a schema with multiple versions of the same type, like this:
 * ```ion
 * type::{
 *   name: short_text,
 *   $version: '1.0',
 *   type: string,
 *   codepoint_length: range::[min, 16],
 * }
 * type::{
 *   name: short_text,
 *   $version: '1.1',
 *   type: text,
 *   codepoint_length: range::[min, 16],
 * }
 * ```
 * Then, when you reference the schema like this: 'models/short_text.isl#v1.0', the authority
 * will return a filtered view of the schema containing unversioned types and versioned types
 * that match the version suffix.
 *
 */
class VersionFilteringAuthorityDecorator(
    private val wrapped: Authority,
    private val versionDelimiter: String = "#v",
    private val versionFieldName: String = "\$version",
) : Authority {

    override fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> {
        return if (versionDelimiter in id) {
            val (unversionedId, version) = id.split(versionDelimiter, limit = 2)
            wrapped.iteratorFor(iss, unversionedId)
                .asIterable()
                .filter {
                    if (it is IonStruct && it.containsKey(versionFieldName)) {
                        (it.get(versionFieldName) as? IonText)?.stringValue() == version
                    } else {
                        true
                    }
                }
                .asCloseableIterator()
        } else {
            wrapped.iteratorFor(iss, id)
        }
    }
}
