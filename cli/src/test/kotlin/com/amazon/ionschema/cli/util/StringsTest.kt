package com.amazon.ionschema.cli.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StringsTest {

    @Test
    fun testToPascalCase() {
        Assertions.assertEquals("AlphabetSoup", "alphabet_soup".toPascalCase())
    }

}