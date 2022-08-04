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

import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.*
import com.amazon.ionschema.util.CloseableIterator
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader

/**
 * An [Authority] implementation that attempts to resolve schema ids to files
 * relative to a basePath.
 *
 * @property[basePath] The base path in the filesystem in which to resolve schema identifiers.
 */
class AuthorityFilesystem(
    basePath: String,
    private val loader: IonElementLoader = DEFAULT_LOADER,
    private val ion: IonSystem = DEFAULT_ION_SYSTEM,
) : Authority {
    private companion object {
        val DEFAULT_LOADER = createIonElementLoader(IonElementLoaderOptions(includeLocationMeta = true))
        val DEFAULT_ION_SYSTEM = IonSystemBuilder.standard().build()!!
    }

    private val basePath: String

    init {
        val file = File(basePath)
        if (!file.exists()) {
            throw FileNotFoundException("Path '$basePath' does not exist")
        }

        this.basePath = if (file.canonicalPath.endsWith(File.separator)) {
            file.canonicalPath
        } else {
            file.canonicalPath + File.separator
        }
    }

    override fun sequenceFor(id: String): CloseableSequence<IonElement>? {
        val file = File(basePath, id)

        if (!file.canonicalPath.startsWith(basePath)) {
            // constructing a new File here avoids leaking information about the filesystem
            throw AccessDeniedException(File(id))
        }

        return if (file.exists() && file.canRead()) {
            loader.loadAllElements(ion.newReader(FileReader(file))).toCloseableSequence()
        } else {
            null
        }
    }

    override fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> {
        return sequenceFor(id)?.use { it.map { it.toIonValue(iss.ionSystem) } }?.iterator()?.asCloseableIterator() ?: EMPTY_ITERATOR
    }
}
