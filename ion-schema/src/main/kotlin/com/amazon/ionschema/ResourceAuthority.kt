package com.amazon.ionschema

import com.amazon.ion.IonSystem
import com.amazon.ion.IonValue
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionelement.api.*
import com.amazon.ionschema.util.CloseableIterator
import java.io.File
import java.io.InputStream

/**
 * An [Authority] implementation that attempts to resolve schema ids to resources
 * in a [ClassLoader]'s classpath.
 *
 * @property[rootPackage] The base path within the [ClassLoader]'s classpath in which
 *     to resolve schema identifiers.
 * @property[classLoader] The [ClassLoader] to use to find the schema resources.
 */
class ResourceAuthority(
    private val rootPackage: String,
    private val classLoader: ClassLoader,
    private val loader: IonElementLoader = DEFAULT_LOADER,
    private val ion: IonSystem = DEFAULT_ION_SYSTEM,
) : Authority {

    override fun sequenceFor(id: String): CloseableSequence<IonElement>? {
        val resourcePath = File(rootPackage, id).toPath().normalize().toString()
        if (!resourcePath.startsWith(rootPackage)) {
            throw AccessDeniedException(File(id))
        }
        val stream: InputStream = classLoader.getResourceAsStream(resourcePath) ?: return null
        val reader = ion.newReader(stream)

        return loader.loadAllElements(reader).toCloseableSequence()
    }

    override fun iteratorFor(iss: IonSchemaSystem, id: String): CloseableIterator<IonValue> {
        return sequenceFor(id)?.use { it.map { it.toIonValue(iss.ionSystem) } }?.iterator()?.asCloseableIterator() ?: EMPTY_ITERATOR
    }

    companion object {
        /**
         * Factory method for constructing a [ResourceAuthority] that can access the schemas provided by
         * [`ion-schema-schemas`](https://github.com/amzn/ion-schema-schemas/).
         */
        @JvmStatic
        fun forIonSchemaSchemas(): ResourceAuthority = ResourceAuthority("ion-schema-schemas", ResourceAuthority::class.java.classLoader)

        private val DEFAULT_LOADER = createIonElementLoader(IonElementLoaderOptions(includeLocationMeta = true))
        private val DEFAULT_ION_SYSTEM = IonSystemBuilder.standard().build()
    }
}
