package com.amazon.ionschema.cli.generator

import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.amazon.kitchen.products.AlphabetSoup
import com.amazon.kitchen.products.Condiment
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigInteger

class TestGeneratedAPI {
    val ION = IonSystemBuilder.standard().build()

    @Test
    fun testGeneratedAPI() {
        val alphabetSoup = AlphabetSoup.Builder()
            .withName("Vowel Soup")
            .withBroth(AlphabetSoup.Broth.builder()
                .withTemp(BigInteger.TEN)
                .withFlavor(AlphabetSoup.Broth.Flavor.CHICKEN)
                .build())
            .withLetters(listOf("a", "e", "i", "o", "u"))
            .withCondiment(Condiment.BUTTER)
            .build()
        val sb = StringBuilder()
        val ionWriter = IonTextWriterBuilder.pretty().build(sb)

        alphabetSoup.writeTo(ionWriter)

        println(sb.toString())

        val ionReader = IonReaderBuilder.standard().build(sb.toString())

        ionReader.next()
        val alphabetSoup2 = AlphabetSoup.readFrom(ionReader)

        Assertions.assertEquals(alphabetSoup, alphabetSoup2)
    }
}