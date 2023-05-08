package com.amazon.ionschema.cli.util

import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import java.io.File

/**
 * Returns a sequence of pairs of `schemaId` to [File]
 */
fun walkFileSystemAuthority(basePath: String) = File(basePath).walk()
    .filter { it.isFile }
    .filter { it.path.endsWith(".isl") }
    .map { file -> file.path.substring(basePath.length + 1) to file }

/**
 * Copies a file from the current [basePath] to the [newBasePath], modifying the contents according to the given [patchSet].
 */
fun rewriteFile(file: File, basePath: String, newBasePath: String, patchSet: PatchSet) {
    val schemaId = file.path.substring(basePath.length + 1)
    val newFile = File("$newBasePath/$schemaId")
    newFile.parentFile.mkdirs()
    if (patchSet.hasChanges()) {
        newFile.createNewFile()
        val schemaIonText = file.readText(Charsets.UTF_8)
        newFile.appendText(patchSet.applyTo(schemaIonText))
    } else {
        file.copyTo(newFile)
    }
}

/**
 * Checks all `.isl` files in the [AuthorityFilesystem] corresponding to [basePath] to make sure that they all load
 * correctly. Returns a map of `schemaId` to [Throwable] (caught failures, usually [InvalidSchemaException]).
 */
fun validateAll(basePath: String, withTransitiveImports: Boolean = false): Map<String, Throwable> {
    val iss = IonSchemaSystemBuilder.standard()
        .allowTransitiveImports(withTransitiveImports)
        .withAuthority(AuthorityFilesystem(basePath))
        .build()

    val failedSchemas = mutableMapOf<String, Throwable>()
    walkFileSystemAuthority(basePath).forEach { (schemaId, _) ->
        runCatching { iss.loadSchema(schemaId) }.onFailure { failedSchemas[schemaId] = it }
    }
    return failedSchemas
}