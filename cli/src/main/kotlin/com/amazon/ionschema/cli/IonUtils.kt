package com.amazon.ionschema.cli

import com.amazon.ionelement.api.AnyElement
import com.amazon.ionelement.api.ContainerElement
import com.amazon.ionelement.api.IonElement
import com.amazon.ionelement.api.ListElement
import com.amazon.ionelement.api.SexpElement
import com.amazon.ionelement.api.StructElement
import com.amazon.ionelement.api.field
import com.amazon.ionelement.api.ionListOf
import com.amazon.ionelement.api.ionSexpOf
import com.amazon.ionelement.api.ionStructOf

sealed class TraversalOrder
internal object PreOrder : TraversalOrder()
internal object PostOrder : TraversalOrder()

internal fun IonElement.recursivelyVisit(order: TraversalOrder, visitor: (AnyElement) -> Unit) {
    with(this.asAnyElement()) {
        if (order is PreOrder) visitor(this)
        if (this is ContainerElement) asContainerOrNull()?.values?.forEach { child -> child.recursivelyVisit(order, visitor) }
        if (order is PostOrder) visitor(this)
    }
}

internal fun IonElement.recursivelyTransform(order: TraversalOrder, transform: (AnyElement) -> IonElement): IonElement {
    var result = this as AnyElement
    result = if (order is PreOrder) transform(result).asAnyElement() else result
    result = when (result) {
        is ListElement -> ionListOf(result.values.map { child -> child.recursivelyTransform(order, transform) }, result.annotations, result.metas)
        is SexpElement -> ionSexpOf(result.values.map { child -> child.recursivelyTransform(order, transform) }, result.annotations, result.metas)
        is StructElement -> ionStructOf(result.fields.map { field(it.name, it.value.recursivelyTransform(order, transform)) }, result.annotations, result.metas)
        else -> result
    }.asAnyElement()
    result = if (order is PostOrder) transform(result).asAnyElement() else result
    return result
}
