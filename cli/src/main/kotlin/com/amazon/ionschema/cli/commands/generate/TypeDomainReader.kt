package com.amazon.ionschema.cli.commands.generate

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.AuthorityFilesystem
import com.amazon.ionschema.IonSchemaSystem
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.cli.commands.authoritiesOption
import com.amazon.ionschema.cli.generator.Converter
import com.amazon.ionschema.cli.generator.TypeDomain
import com.amazon.ionschema.model.ExperimentalIonSchemaModel
import com.amazon.ionschema.reader.IonSchemaReaderV2_0
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.GroupableOption
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.output.TermUi.echo
import java.io.File

class TypeDomainReader(private val delegate: CliktCommand, private val ion: IonSystem): ParameterHolder {

    override fun registerOption(option: GroupableOption) {
        delegate.registerOption(option)
    }

    val fileSystemAuthorityRoots by delegate.authoritiesOption()

    val iss by lazy {
        val authorities = fileSystemAuthorityRoots.map { AuthorityFilesystem(it.path) }
        IonSchemaSystemBuilder.standard()
            .withIonSystem(ion)
            .withAuthorities(authorities)
            .allowTransitiveImports(false)
            .build()
    }

    @OptIn(ExperimentalIonSchemaModel::class)
    fun readTypeDomain(): TypeDomain {
        val reader = IonSchemaReaderV2_0()
        echo("Finding schemas...")
        val schemaDocuments = fileSystemAuthorityRoots.flatMap {
            it.walk()
                .filter { it.isFile }
                .onEach { echo("Attempting to load $it") }
                .mapNotNull { f ->
                    try {
                        val schemaId = f.relativeTo(it).path
                        val schema = iss.loadSchema(schemaId)
                        reader.readSchemaOrThrow(schema.isl)
                            .copy(id = schemaId)
                    } catch (e: Exception) {
                        echo(e.message, err = true)
                        null
                    }
                }
                .toList()

        }


        val converter = Converter(
            Converter.Options(
                // TODO: make this configurable somehow
                schemaIdToModuleNamespaceStrategy = {
                    (if (it.endsWith(".isl")) it.dropLast(4) else it)
                        .split("/", "\\")
                        .map { it.replace(Regex("[^\\w\\d_]"), "") }
                }
            )
        )

        return converter.toTypeDomain(schemaDocuments)
    }
}