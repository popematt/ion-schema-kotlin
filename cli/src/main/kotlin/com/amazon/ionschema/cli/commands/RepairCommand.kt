package com.amazon.ionschema.cli.commands

import com.amazon.ionschema.cli.commands.repair.FixTransitiveImportsCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument

class RepairCommand : NoOpCliktCommand(
    help = "Fixes schemas that are affected by a bug in some way."
) {
    init {
        context {
            subcommands(
                FixTransitiveImportsCommand()
            )
        }
    }

    private class TransitiveImportsCommand : CliktCommand(

    ) {
        val basePath by argument()


        override fun run() {
            //TransitiveImportRewriter2().fixTransitiveImports(basePath)
        }
    }
}
