package com.jxxerick.expensesplit.service.currency;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.service.split.MoneyAllocator;
import com.jxxerick.expensesplit.service.split.Share;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Currency codes and conversion of foreign expenses into the group currency. */
public final class CurrencyConverter {

    private CurrencyConverter() {
    }

    /** Upper-cases and checks an ISO 4217 code ("usd" → "USD"). */
    public static String normalize(String code) {
        String upper = code.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(upper).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Moeda desconhecida: " + code.trim());
        }
    }

    /** Banker's rounding to the cent, so repeated conversions don't drift up. */
    public static BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        if (converted.signum() <= 0) {
            throw new BusinessException("O valor convertido ficou abaixo de um centavo");
        }
        return converted;
    }

    /** Largest foreign amount, in cents, whose conversion does not exceed {@code limit}. */
    public static BigDecimal maxForeignAmount(BigDecimal limit, BigDecimal rate) {
        return limit.divide(rate, 2, RoundingMode.DOWN);
    }

    /** True when {@code converted} is closer to {@code target} than one cent of the foreign currency is worth. */
    public static boolean withinOneForeignCent(BigDecimal converted, BigDecimal target, BigDecimal rate) {
        BigDecimal oneForeignCent = rate.movePointLeft(2);
        return converted.subtract(target).abs().compareTo(oneForeignCent) < 0;
    }

    /**
     * Turns shares computed in the foreign currency into shares of the converted total,
     * keeping the proportions and making them add up to the total exactly.
     */
    public static List<Share> convertShares(List<Share> foreignShares, BigDecimal convertedTotal) {
        List<BigDecimal> converted = MoneyAllocator.allocate(convertedTotal,
                foreignShares.stream().map(Share::amount).toList());
        return IntStream.range(0, foreignShares.size())
                .mapToObj(i -> new Share(foreignShares.get(i).memberId(), converted.get(i)))
                .toList();
    }
}
