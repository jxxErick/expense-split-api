package com.jxxerick.expensesplit.service.export;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds a CSV the way Excel in pt-BR opens it: {@code ;} between cells, decimal comma,
 * CRLF line breaks. Text cells are quoted when needed and protected against formula injection.
 */
public final class CsvWriter {

    private static final char SEPARATOR = ';';
    private static final Pattern NEEDS_QUOTES = Pattern.compile("[;\"\r\n]");
    // a cell starting with one of these is run as a formula by spreadsheet apps
    private static final Pattern FORMULA_START = Pattern.compile("^[=+\\-@\t\r].*", Pattern.DOTALL);

    private final StringBuilder out = new StringBuilder();

    public CsvWriter row(List<String> cells) {
        out.append(String.join(String.valueOf(SEPARATOR), cells)).append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    /** User-typed text: never evaluated as a formula, quoted when it holds separators. */
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_START.matcher(value).matches() ? "'" + value : value;
        return NEEDS_QUOTES.matcher(safe).find() ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }

    /** "1234.50" becomes "1234,50"; null becomes an empty cell. */
    public static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }
}
