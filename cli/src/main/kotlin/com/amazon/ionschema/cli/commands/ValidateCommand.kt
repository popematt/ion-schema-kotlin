package com.amazon.ionschema.cli.commands

import com.amazon.ion.IonStruct
import com.amazon.ion.IonText
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.ResourceAuthority
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

class ValidateCommand : CliktCommand(
    help = "Validate Ion data against a schema.",
    epilog = """
        ```
        Example usage:
            Validate against a new type:
                ion-schema-cli validate --type '{ codepoint_length: range::[min, 10] }' --value 'hello'
            Validate against a type from a schema:
                ion-schema-cli validate -a file-system --base-dir ~/my_schemas/ --schema 'Customers.isl' --type 'online_customer' --value '{ foo: bar }'    
        ```
    """.trimIndent()
) {

    private val ion = IonSystemBuilder.standard().build()

    sealed class AuthorityConfig : OptionGroup() {
        class FileSystem : AuthorityConfig() {
            val baseDir by option().file().multiple(required = true)
        }
        class IonSchemaSchemas : AuthorityConfig()
        object None : AuthorityConfig()
    }

    val authorityConfig by option("-a", "--authority").groupChoice(
        "file-system" to AuthorityConfig.FileSystem(),
        "isl" to AuthorityConfig.IonSchemaSchemas(),
        "none" to AuthorityConfig.None
    ).defaultByName("none")

    val schema by option("-s", "--schema")

    val type by option("-t", "--type", help = "An ISL type reference.").required().check {
        with(ion.singleValue(it)) {
            !isNullValue && type in listOf(IonType.SYMBOL, IonType.STRUCT, IonType.STRING)
        }
    }
    val ionValue by argument("ion-value").check { runCatching { ion.singleValue(it) }.isSuccess }

    override fun run() {
        val authorities = when (authorityConfig) {
            is AuthorityConfig.FileSystem -> (authorityConfig as AuthorityConfig.FileSystem).baseDir
                .map { AuthorityFilesystem(it.absolutePath) }
            is AuthorityConfig.IonSchemaSchemas -> listOf(ResourceAuthority.forIonSchemaSchemas())
            is AuthorityConfig.None -> emptyList()
        }

        val iss = IonSchemaSystemBuilder.standard()
            .withIonSystem(ion)
            .withAuthorities(authorities)
            .allowTransitiveImports(false)
            .build()

        val islSchema = when (authorityConfig) {
            is AuthorityConfig.None -> iss.newSchema()
            is AuthorityConfig.FileSystem -> iss.loadSchema(schema!!)
            is AuthorityConfig.IonSchemaSchemas -> iss.loadSchema(schema!!)
        }

        val typeIon = iss.ionSystem.singleValue(type)
        val islType = if (typeIon is IonText) {
            islSchema.getType(typeIon.stringValue()) ?: throw IllegalArgumentException("No such type: $type")
        } else {
            islSchema.newType(typeIon as IonStruct)
        }

        val violations = islType.validate(ion.singleValue(ionValue))

        if (violations.isValid()) {
            echo("Valid")
        } else {
            echo(violations.toString().dropWhile { it != '\n' }.trim())
        }
    }
}
