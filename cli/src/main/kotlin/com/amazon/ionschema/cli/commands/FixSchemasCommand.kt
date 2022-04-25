package com.amazon.ionschema.cli.commands

import com.amazon.ionschema.cli.TransitiveImportRewriter2
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument

class FixSchemasCommand : NoOpCliktCommand(
    help = "Fixes schemas that are affected by a bug in some way."
) {
    init {
        context {
            subcommands(
                TransitiveImportsCommand()
            )
        }
    }

    private class TransitiveImportsCommand : CliktCommand(
        help = "Fixes schemas that are affected by the transitive import issue. See https://github.com/amzn/ion-schema/issues/39",
        epilog = """
        ```
        Example usage:   
        ```
        """.trimIndent()
    ) {
        val basePath by argument()

        override fun run() {
            TransitiveImportRewriter2.fixTransitiveImports(basePath)
        }
    }
}
