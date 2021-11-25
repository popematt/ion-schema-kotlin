package com.amazon.ionschema.cli

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.versionOption
import java.util.Properties

fun main(args: Array<String>) = IonSchemaCli().main(args)

/**
 * This is the root command. It doesn't run anything on its own—you must invoke a subcommand.
 */
class IonSchemaCli : NoOpCliktCommand(
    name = "isl-cli",
    help = ""
) {
    init {
        context {
            subcommands(
                NoOpCliktCommand(name = "no-op")
                // TODO: Add real subcommands
            )
            versionOption(getVersionString())
            helpFormatter = CliktHelpFormatter(showRequiredTag = true, showDefaultValues = true)
        }
    }

    private fun getVersionString(): String {
        val propertiesStream = this.javaClass.getResourceAsStream("/cli.properties")
        val properties = Properties().apply { load(propertiesStream) }
        return "${properties.getProperty("version")}-${properties.getProperty("commit")}"
    }
}
