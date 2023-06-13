package com.amazon.ionschema.cli.util

import com.amazon.ionschema.cli.generator.toPascalCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StringsTest {

    @Test
    fun testToPascalCase() {
        Assertions.assertEquals("AlphabetSoup", "alphabet_soup".toPascalCase())
    }

}