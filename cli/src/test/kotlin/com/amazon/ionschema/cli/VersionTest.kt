package com.amazon.ionschema.cli

import com.github.ajalt.clikt.core.PrintMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldMatch
import org.junit.Test

class VersionTest {

    @Test
    fun testVersionCommand() {
        shouldThrow<PrintMessage> {
            IonSchemaCli().parse(arrayOf("--version"))
        }.should {
            it.message shouldMatch """isl-cli version \d+\.\d+\.\d+(-SNAPSHOT)?-[0-9a-f]{7}"""
        }
    }
}
