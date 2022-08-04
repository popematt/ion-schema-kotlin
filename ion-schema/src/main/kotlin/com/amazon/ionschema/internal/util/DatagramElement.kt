package com.amazon.ionschema.internal.util

import com.amazon.ion.Decimal
import com.amazon.ion.IonWriter
import com.amazon.ion.Timestamp
import com.amazon.ionelement.api.*
import java.math.BigInteger

internal fun ionDatagramOf(
    iterable: Iterable<IonElement>,
): DatagramElement = DatagramElement(iterable.map { it.asAnyElement() })

internal class DatagramElement(override val values: List<AnyElement>) : AnyElement, SeqElement, List<AnyElement> by values {
    private fun typeErr(): Nothing = TODO("DatagramElement does not have any ElementType and has no corresponding typed null.")

    override val size: Int
        get() = values.size

    override val annotations: List<String> = emptyList()
    override val metas: MetaContainer = emptyMetaContainer()

    override val type: Nothing
        get() = typeErr()

    override fun writeTo(writer: IonWriter) {
        values.forEach { it.writeTo(writer) }
    }

    override fun toString(): String {
        return values.joinToString("\n") { it.toString() }
    }

    override val containerValues: Collection<AnyElement> get() = values
    override val containerValuesOrNull: Collection<AnyElement> get() = values
    override val seqValues: List<AnyElement> get() = values
    override val seqValuesOrNull: List<AnyElement> get() = values
    override fun asContainer(): ContainerElement = this
    override fun asContainerOrNull(): ContainerElement = this
    override fun asSeq(): SeqElement = this
    override fun asSeqOrNull(): SeqElement = this

    // Boring overrides follow

    override fun asAnyElement(): AnyElement = this

    override fun copy(annotations: List<String>, metas: MetaContainer): Nothing = TODO("A DatagramElement does not have annotations or metas.")
    override fun withAnnotations(vararg additionalAnnotations: String): Nothing = copy()
    override fun withAnnotations(additionalAnnotations: Iterable<String>): Nothing = copy()
    override fun withMeta(key: String, value: Any): Nothing = copy()
    override fun withMetas(additionalMetas: MetaContainer): Nothing = copy()
    override fun withoutAnnotations(): DatagramElement = this
    override fun withoutMetas(): DatagramElement = this

    override val bigIntegerValue: BigInteger get() = typeErr()
    override val bigIntegerValueOrNull: BigInteger get() = typeErr()
    override val blobValue: ByteArrayView get() = typeErr()
    override val blobValueOrNull: ByteArrayView get() = typeErr()
    override val booleanValue: Boolean get() = typeErr()
    override val booleanValueOrNull: Boolean get() = typeErr()
    override val bytesValue: ByteArrayView get() = typeErr()
    override val bytesValueOrNull: ByteArrayView get() = typeErr()
    override val clobValue: ByteArrayView get() = typeErr()
    override val clobValueOrNull: ByteArrayView get() = typeErr()
    override val decimalValue: Decimal get() = typeErr()
    override val decimalValueOrNull: Decimal get() = typeErr()
    override val doubleValue: Double get() = typeErr()
    override val doubleValueOrNull: Double get() = typeErr()
    override val integerSize: IntElementSize get() = typeErr()
    override val isNull: Boolean = false
    override val listValues: List<AnyElement> get() = typeErr()
    override val listValuesOrNull: List<AnyElement> get() = typeErr()
    override val longValue: Long get() = typeErr()
    override val longValueOrNull: Long get() = typeErr()
    override val sexpValues: List<AnyElement> get() = typeErr()
    override val sexpValuesOrNull: List<AnyElement> get() = typeErr()
    override val stringValue: String get() = typeErr()
    override val stringValueOrNull: String get() = typeErr()
    override val structFields: Collection<StructField> get() = typeErr()
    override val structFieldsOrNull: Collection<StructField> get() = typeErr()
    override val symbolValue: String get() = typeErr()
    override val symbolValueOrNull: String get() = typeErr()
    override val textValue: String get() = typeErr()
    override val textValueOrNull: String get() = typeErr()
    override val timestampValue: Timestamp get() = typeErr()
    override val timestampValueOrNull: Timestamp get() = typeErr()

    override fun asBlob(): Nothing = typeErr()
    override fun asBlobOrNull(): Nothing = typeErr()
    override fun asBoolean(): Nothing = typeErr()
    override fun asBooleanOrNull(): Nothing = typeErr()
    override fun asClob(): Nothing = typeErr()
    override fun asClobOrNull(): Nothing = typeErr()
    override fun asDecimal(): Nothing = typeErr()
    override fun asDecimalOrNull(): Nothing = typeErr()
    override fun asFloat(): Nothing = typeErr()
    override fun asFloatOrNull(): Nothing = typeErr()
    override fun asInt(): Nothing = typeErr()
    override fun asIntOrNull(): Nothing = typeErr()
    override fun asList(): Nothing = typeErr()
    override fun asListOrNull(): Nothing = typeErr()
    override fun asLob(): Nothing = typeErr()
    override fun asLobOrNull(): Nothing = typeErr()
    override fun asSexp(): Nothing = typeErr()
    override fun asSexpOrNull(): Nothing = typeErr()
    override fun asString(): Nothing = typeErr()
    override fun asStringOrNull(): Nothing = typeErr()
    override fun asStruct(): Nothing = typeErr()
    override fun asStructOrNull(): Nothing = typeErr()
    override fun asSymbol(): Nothing = typeErr()
    override fun asSymbolOrNull(): Nothing = typeErr()
    override fun asText(): Nothing = typeErr()
    override fun asTextOrNull(): Nothing = typeErr()
    override fun asTimestamp(): Nothing = typeErr()
    override fun asTimestampOrNull(): Nothing = typeErr()
}
