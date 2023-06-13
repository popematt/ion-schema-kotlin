package com.amazon.ionschema.cli.commands

import com.amazon.ionschema.cli.commands.generate.DumpTypeDomainCommand
import com.amazon.ionschema.cli.commands.generate.KotlinCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands


class GenerateCommand: NoOpCliktCommand(help = "Generate code or code-adjacent things based on a set of schemas.") {
    init {
        context {
            subcommands(
                DumpTypeDomainCommand(),
                KotlinCommand(),
            )
        }
    }
}
