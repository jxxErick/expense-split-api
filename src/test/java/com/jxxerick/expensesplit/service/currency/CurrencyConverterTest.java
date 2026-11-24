package com.jxxerick.expensesplit.service.currency;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.service.split.Share;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyConverterTest {

    @Test
    void normalizesKnownCodes() {
        assertThat(CurrencyConverter.normalize(" usd ")).isEqualTo("USD");
        assertThat(CurrencyConverter.normalize("Eur")).isEqualTo("EUR");
    }

    @Test
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> CurrencyConverter.normalize("XYZ"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Moeda desconhecida: XYZ");
    }

    @Test
    void convertsWithBankersRounding() {
        // exact ties go to the even cent: 1.225 -> 1.22 and 1.235 -> 1.24
        assertThat(CurrencyConverter.convert(money("10.00"), new BigDecimal("0.1225"))).isEqualTo(money("1.22"));
        assertThat(CurrencyConverter.convert(money("10.00"), new BigDecimal("0.1235"))).isEqualTo(money("1.24"));
        assertThat(CurrencyConverter.convert(money("100.00"), new BigDecimal("5.4321"))).isEqualTo(money("543.21"));
    }

    @Test
    void rejectsConversionsBelowOneCent() {
        assertThatThrownBy(() -> CurrencyConverter.convert(money("0.01"), new BigDecimal("0.1")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void convertedSharesKeepProportionsAndAddUpToTheTotal() {
        List<Share> foreign = List.of(new Share(1L, money("33.34")), new Share(2L, money("33.33")),
                new Share(3L, money("33.33")));

        List<Share> converted = CurrencyConverter.convertShares(foreign, money("543.21"));

        assertThat(converted).extracting(Share::memberId).containsExactly(1L, 2L, 3L);
        assertThat(converted).extracting(Share::amount)
                .containsExactly(money("181.11"), money("181.05"), money("181.05"));
        assertThat(converted.stream().map(Share::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualTo(money("543.21"));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
