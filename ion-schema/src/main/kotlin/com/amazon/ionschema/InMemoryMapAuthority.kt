package com.amazon.ionschema

import com.amazon.ion.IonDatagram
import com.amazon.ion.IonValue
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.loadAllElements
import com.amazon.ionelement.api.toIonElement
import com.amazon.ionelement.api.toIonValue
import com.amazon.ionschema.util.CloseableIterator

/**
 * An Authority implementation that is backed by an in-memory map of [IonElement]s or Ion Text [String]s.
 */
class InMemoryMapAuthority private constructor(
    private val schemaText: Map<String, String> = mapOf(),
    private val schemas: MutableMap<String, Iterable<IonElement>> = mutableMapOf()
) : Authority {
    companion object {
        /**
         * Constructs an instance of [InMemoryMapAuthority] from a map of Ion Text strings, which
         * are lazily materialized to the [IonElement] DOM.
         */
        @JvmStatic
        fun fromIonText(schemaText: Map<String, String>) =
            InMemoryMapAuthority(schemaText = schemaText)

        /**
         * Constructs an instance of [InMemoryMapAuthority] from pairs of schemaIds and Ion Text strings, which
         * are lazily materialized to the [IonElement] DOM.
         */
        fun fromIonText(vararg schemaText: Pair<String, String>) = fromIonText(mapOf(*schemaText))

        /**
         * Constructs an instance of [InMemoryMapAuthority] from a map of [IonDatagram]s.
         */
        @JvmStatic
        fun fromIonElements(schemas: Map<String, Iterable<IonElement>>) =
            InMemoryMapAuthority(schemas = schemas.toMutableMap())

        /**
         * Constructs an instance of [InMemoryMapAuthority] from pairs of schemaIds and [IonDatagram]s.
         */
        fun fromIonElements(vararg schemas: Pair<String, Iterable<IonElement>>) = fromIonElements(mapOf(*schemas))

        /**
         * Constructs an instance of [InMemoryMapAuthority] from a map of [IonDatagram]s.
         */
        @JvmStatic
        fun fromIonValues(schemas: Map<String, IonDatagram>) =
            fromIonElements(schemas.mapValues { (_, dg) -> dg.map { it.toIonElement() } })

        /**
         * Constructs an instance of [InMemoryMapAuthority] from pairs of schemaIds and [IonDatagram]s.
         */
        fun fromIonValues(vararg schemas: Pair<String, IonDatagram>) = fromIonValues(mapOf(*schemas))
    }

    override fun sequenceFor(id: String): CloseableSequence<IonElement>? {
        if (!schemas.containsKey(id)) {
            schemaText[id]?.let {
                schemas[id] = loadAllElements(it)
            }
        }
        return schemas[id]?.asSequence()?.toCloseableSequence()
    }

    override fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> {
        return sequenceFor(id)?.use { it.map { it.toIonValue(iss.ionSystem) } }?.iterator()?.asCloseableIterator() ?: EMPTY_ITERATOR
    }
}
