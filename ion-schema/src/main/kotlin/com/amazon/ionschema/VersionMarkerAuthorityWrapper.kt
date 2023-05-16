package com.amazon.ionschema

import com.amazon.ion.IonSymbol
import com.amazon.ion.IonValue
import com.amazon.ionschema.util.CloseableIterator

/**
 * Ensures that all schemas have the version marker as the first element of this schema.
 *
 * This wraps an [Authority] to pre-process a schema before it is loaded by the [IonSchemaSystem].
 * You should only use this as a temporary stop-gap until your schemas are corrected.
 *
 * The [callback] constructor arg is a callback function that is invoked whenever an ISL 2.0 schema is detected that has
 * the version marker in the wrong position. The argument to the callback function is the affected schema id. You can
 * use this callback function for things such as logging the names of affected schemas or emitting a metric when an
 * affected schema is encountered.
 *
 * Invalid and/or extraneous version markers will be passed through as is and will result in an exception from
 * [IonSchemaSystem].
 */
class VersionMarkerAuthorityWrapper(private val wrapped: Authority, private val callback: (String) -> Unit = {}) : Authority {
    override fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> {
        val schema = wrapped.iteratorFor(iss, id)
        if (schema == EMPTY_ITERATOR) return EMPTY_ITERATOR

        val islList = schema.asSequence().toList()

        val i = islList.indexOfFirst { IonSchemaVersion.isVersionMarker(it) }

        return if (i <= 0 || (islList.getOrNull(i) as IonSymbol?)?.stringValue() != "\$ion_schema_2_0") {
            // Version marker is not found (aka it's ISl 1.0); OR it's not ISL 2.0;
            // OR version marker is already in the correct location.
            islList.iterator().asCloseableIterator()
        } else {
            callback(id)
            val newList = mutableListOf<IonValue>()
            newList.add(islList[i])
            newList.addAll(islList.slice(0 until i))
            newList.addAll(islList.slice(i + 1 until islList.size))
            newList.iterator().asCloseableIterator()
        }
    }

    /**
     * Wraps a regular [Iterator] in a [CloseableIterator] with a no-op
     * implementation of [CloseableIterator.close]. This may be a useful
     * public function, but it has the potential to be misused on iterators
     * of resources that DO need to be closed, so this function is private
     * for the time being.
     */
    private fun <T> Iterator<T>.asCloseableIterator(): CloseableIterator<T> {
        return object : CloseableIterator<T>, Iterator<T> by this {
            override fun close() = Unit
        }
    }
}
