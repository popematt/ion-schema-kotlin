package com.amazon.ionschema.cli.commands

import com.amazon.ion.IonStruct
import com.amazon.ion.IonSymbol
import com.amazon.ion.IonType
import com.amazon.ionschema.Schema
import com.amazon.ionschema.Type
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check

/**
 * Base class for commands that use a (possibly empty) set of [Authority][com.amazon.ionschema.Authority], load or
 * create a [Schema], and get or create a [Type].
 *
 * Use this as a base class so that we have consistently named options across all subcommands.
 */
abstract class UsesType(
    help: String = "",
    epilog: String = "",
    name: String? = null,
    invokeWithoutSubcommand: Boolean = false,
    printHelpOnEmptyArgs: Boolean = false,
    helpTags: Map<String, String> = emptyMap(),
    autoCompleteEnvvar: String? = "",
    allowMultipleSubcommands: Boolean = false,
    treatUnknownOptionsAsArgs: Boolean = false
): UsesSchema(help, epilog, name, invokeWithoutSubcommand, printHelpOnEmptyArgs, helpTags, autoCompleteEnvvar, allowMultipleSubcommands, treatUnknownOptionsAsArgs) {

    private val typeArg by argument(help = "An ISL type name or inline type definition.")
        .check(lazyMessage = { "Not a valid type reference: $it" }) {
            with(ion.singleValue(it)) {
                !isNullValue && type in listOf(IonType.SYMBOL, IonType.STRUCT)
            }
        }

    protected val type: Type by lazy {
        val typeIon = iss.ionSystem.singleValue(typeArg)
        if (typeIon is IonSymbol) {
            schema.getType(typeIon.stringValue()) ?: throw IllegalArgumentException("Type not found: $typeArg")
        } else {
            schema.newType(typeIon as IonStruct)
        }
    }
}