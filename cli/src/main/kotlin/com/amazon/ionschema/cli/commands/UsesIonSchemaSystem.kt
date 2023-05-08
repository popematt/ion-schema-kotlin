package com.amazon.ionschema.cli.commands

import com.amazon.ion.IonSystem
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSchemas
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.ResourceAuthority
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

/**
 * Base class for commands that use a set of [Authority][com.amazon.ionschema.Authority] and/or an
 * [IonSchemaSystem][com.amazon.ionschema.IonSchemaSystem].
 *
 * Use this as a base class so that we have consistently named options across all subcommands.
 */
abstract class UsesIonSchemaSystem(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false,
    printHelpOnEmptyArgs: Boolean = false,
    helpTags: Map<String, String> = emptyMap(),
    autoCompleteEnvvar: String? = "",
    allowMultipleSubcommands: Boolean = false,
    treatUnknownOptionsAsArgs: Boolean = false
): CliktCommand(help, epilog, name, invokeWithoutSubcommand, printHelpOnEmptyArgs, helpTags, autoCompleteEnvvar, allowMultipleSubcommands, treatUnknownOptionsAsArgs) {

    abstract val ion: IonSystem

    protected val fileSystemAuthorityRoots by option(
        "-a", "--authority",
        help = "The root(s) of the file system authority(s). " +
                "Authorities are only required if you need to import a type from another " +
                "schema file or if you are loading a schema using the --id option."
    )
        .file(canBeFile = false, mustExist = true, mustBeReadable = true)
        .multiple()

    private val useIonSchemaSchemaAuthority by option(
        "-I", "--isl-for-isl",
        help = "Indicates that the Ion Schema Schemas authority should be included in the schema system configuration."
    ).flag()

    protected val authorities by lazy {
        if (useIonSchemaSchemaAuthority) {
            fileSystemAuthorityRoots.map { AuthorityFilesystem(it.path) } + IonSchemaSchemas.authority()
        } else {
            fileSystemAuthorityRoots.map { AuthorityFilesystem(it.path) }
        }
    }

    protected val iss by lazy {
        IonSchemaSystemBuilder.standard()
            .withIonSystem(ion)
            .withAuthorities(authorities)
            .allowTransitiveImports(false)
            .build()
    }
}