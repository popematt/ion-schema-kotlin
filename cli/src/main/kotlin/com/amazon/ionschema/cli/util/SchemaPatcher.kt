package com.amazon.ionschema.cli.util

import java.util.TreeSet

class SchemaPatcher(val schemaId: String, val original: String) {
    private data class Patch(val start: Int, val endInclusive: Int, val replacementText: String)

    private val patchSet = TreeSet<Patch>(compareByDescending { it.start })
    private var cachedString: String = original
    private var cachedPatchCount: Int = patchSet.size

    fun hasChanges() = patchSet.isNotEmpty()

    fun patch(first: Int, last: Int, newText: String) {
        patchSet.add(Patch(first, last, newText))
    }

    fun replaceAll(newText: String) {
        patchSet.add(Patch(0, original.length, newText))
    }

    override fun toString(): String {
        if (patchSet.size != cachedPatchCount) {
            val sb = StringBuilder(original)
            for ((start, end, newText) in patchSet) {
                sb.replace(start, end + 1, newText)
            }
            cachedString = sb.toString()
            cachedPatchCount = patchSet.size
        }
        return cachedString
    }

    fun toPatchSet() = PatchSet().apply {
        patchSet.forEach { this.patch(it.start, it.endInclusive, it.replacementText) }
    }
}
