package com.amazon.ionschema.cli.commands.generate

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.cli.generator.toIon
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class DumpTypeDomainCommand : CliktCommand() {
    private val ion = IonSystemBuilder.standard().build()
    private val typeDomainGenerator = TypeDomainReader(this, ion)

    private val pretty by option().flag()

    override fun run() {
        val typeDomain = typeDomainGenerator.readTypeDomain()
        val typeDomainIon = typeDomain.entities.mapTo(ion.newList()) { it.toIon() }
        if (pretty) {
            echo(typeDomainIon.toPrettyString())
        } else {
            echo(typeDomainIon.toString())
        }
    }
}
