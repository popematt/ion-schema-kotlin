package com.amazon.ionschema.cli.commands

import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.IonSchemaVersion
import com.amazon.ionschema.Schema
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

/**
 * Base class for commands that use a (possibly empty) set of [Authority][com.amazon.ionschema.Authority] and load or
 * create a [Schema] using those authorities.
 *
 * Use this as a base class so that we have consistently named options across all subcommands.
*/
abstract class UsesSchema(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false,
    printHelpOnEmptyArgs: Boolean = false,
    helpTags: Map<String, String> = emptyMap(),
    autoCompleteEnvvar: String? = "",
    allowMultipleSubcommands: Boolean = false,
    treatUnknownOptionsAsArgs: Boolean = false
): UsesIonSchemaSystem(help, epilog, name, invokeWithoutSubcommand, printHelpOnEmptyArgs, helpTags, autoCompleteEnvvar, allowMultipleSubcommands, treatUnknownOptionsAsArgs) {

    private val schemaArg by mutuallyExclusiveOptions<IonSchemaSystem.() -> Schema>(

        option("--id", help = "The ID of a schema to load from one of the configured authorities.")
            .convert { { loadSchema(it) } },

        option("--schema-text", "-t", help = "The Ion text contents of a schema document.")
            .convert { { newSchema(it) } },

        option("--schema-file", "-f", help = "A schema file")
            .file(mustExist = true, mustBeReadable = true, canBeDir = false)
            .convert { { newSchema(it.readText()) } },

        option(
            "-v", "--version",
            help = "An empty schema document for the specified Ion Schema version. " +
                    "The version must be specified as X.Y; e.g. 2.0"
        )
            .enum<IonSchemaVersion> { it.name.drop(1).replace("_", ".") }
            .convert { { newSchema(it.symbolText) } },

        name = "Schema",
        help = "All Ion Schema types are defined in the context of a schema document, so it is necessary to always " +
                "have a schema document, even if that schema document is an implicit, empty schema. If a schema is " +
                "not specified, the default is an implicit, empty Ion Schema 2.0 document."
    ).default { newSchema(IonSchemaVersion.v2_0.symbolText) }

    val schema by lazy { iss.schemaArg() }
}