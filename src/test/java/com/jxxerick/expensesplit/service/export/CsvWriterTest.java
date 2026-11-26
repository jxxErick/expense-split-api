package com.jxxerick.expensesplit.service.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "Jantar|Jantar",
            "a;b|\"a;b\"",
            "diz \"oi\"|\"diz \"\"oi\"\"\"",
            "=1+1|'=1+1",
            "+55 11|'+55 11",
            "-2|'-2",
            "@SUM(A1)|'@SUM(A1)"
    })
    void textShouldQuoteAndNeutralizeFormulas(String raw, String expected) {
        assertThat(CsvWriter.text(raw)).isEqualTo(expected);
    }

    @Test
    void textShouldQuoteLineBreaks() {
        assertThat(CsvWriter.text("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
    }

    @Test
    void numberShouldUseDecimalCommaAndKeepTheSign() {
        assertThat(CsvWriter.number(new BigDecimal("-1234.50"))).isEqualTo("-1234,50");
        assertThat(CsvWriter.number(null)).isEmpty();
    }

    @Test
    void rowsShouldEndWithCrlf() {
        assertThat(new CsvWriter().row(List.of("a", "b")).row(List.of("c")).toString()).isEqualTo("a;b\r\nc\r\n");
    }
}
