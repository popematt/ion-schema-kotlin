package com.amazon.ionschema.cli.commands.repair

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.cli.TransitiveImportRewriter3
import com.amazon.ionschema.cli.commands.UsesIonSchemaSystem
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum

class FixTransitiveImportsCommand: UsesIonSchemaSystem(
    help = "Fixes schemas that are affected by the transitive import issue. See https://github.com/amzn/ion-schema/issues/39",
    epilog = """
        ```
        Example usage:   
        ```
        """.trimIndent()
) {

    override val ion = IonSystemBuilder.standard().build()

    private val strategy by option("-s", "--strategy", help = "Import resolution strategy")
        .enum<TransitiveImportRewriter3.ImportStrategy>()
        .default(TransitiveImportRewriter3.ImportStrategy.KeepExistingWildcardImports)

    private val verbose by option("-v", "--verbose").flag()

    override fun run() {

        val rewriter = TransitiveImportRewriter3(
            importStrategy = strategy,
            ION = this.ion,
            echo = if (verbose) ({ echo(it) }) else ({})
        )
        rewriter.fixTransitiveImports(fileSystemAuthorityRoots.single().path, authorities)
    }
}