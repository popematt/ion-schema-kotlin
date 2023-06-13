package com.amazon.ionschema.cli.commands.generate

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.cli.generator.KotlinGenerator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.writeText

class KotlinCommand: CliktCommand() {
    private val ion = IonSystemBuilder.standard().build()
    private val typeDomainGenerator = TypeDomainReader(this, ion)

    private val outputDir by option("-o", "--output-dir", metavar = "PATH", help = "A path for the generated code to be written to. Default is current working directory.")
        .file(canBeFile = false)
        .default(File(System.getProperty("user.dir")))

    private val generatedKotlinVersion by option("-v", "--generated-kotlin-version", help = "The kotlin version to use for the generated code. Default is Kotlin 1.5.")
        .convert { val (major, minor) = it.split("."); KotlinVersion(major.toInt(), minor.toInt()) }
        .default(KotlinVersion(1, 5))

    private val packagePathPrefix by option("-p", "--package-prefix", help = "The root package for all of the generated code").default("")
    override fun run() {
        val typeDomain = typeDomainGenerator.readTypeDomain()


        val kotlinGenerator = KotlinGenerator(
            typeDomain = typeDomain,
            options = KotlinGenerator.Options(
                kotlinVersion = generatedKotlinVersion,
                outputDir = outputDir.toPath(),
                rootPackage = packagePathPrefix,
            )
        )

        val result = kotlinGenerator.generateTypeDomain()

        result.forEach {
            val path = outputDir.absolutePath + "/" + it.rootPackage.split(".").joinToString("/") + "/"
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()
            require(dir.canWrite()) { "Cannot write to ${dir.canonicalPath}" }
            echo("Writing to $path${it.fileName} ...", trailingNewline = false)
            Path(path + it.fileName).writeText(it.content)
            echo("Done")
        }
    }


}