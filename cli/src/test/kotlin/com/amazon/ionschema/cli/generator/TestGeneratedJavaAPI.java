package com.amazon.ionschema.cli.generator;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.kitchen.products.AlphabetSoup;
import com.amazon.kitchen.products.Condiment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

public class TestGeneratedJavaAPI {

    private static IonSystem ION = IonSystemBuilder.standard().build();

    @Test
    public void testGeneratedAPI() {
        AlphabetSoup alphabetSoup = AlphabetSoup.builder()
                .withName("Spicy Vowel Soup")
                .withDescription("Includes the spicy opinion that Y is a vowel.")
                .withBroth(AlphabetSoup.Broth.builder()
                        .withTemp(BigInteger.TEN)
                        .withFlavor(AlphabetSoup.Broth.Flavor.CHICKEN)
                        .build())
                .withCondiment(Condiment.KETCHUP)
                .withLetters(Arrays.asList("a", "e", "i", "o", "u", "y"))
                .build();

        StringBuilder sb = new StringBuilder();
        IonWriter ionWriter = IonTextWriterBuilder.pretty().build(sb);
        alphabetSoup.writeTo(ionWriter);
        System.out.println(sb);

        IonReader ionReader = IonReaderBuilder.standard().build(sb.toString());

        AlphabetSoup alphabetSoup2 = AlphabetSoup.readFrom(ionReader);

        Assertions.assertEquals(alphabetSoup, alphabetSoup2);
    }
}
