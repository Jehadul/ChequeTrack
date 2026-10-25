package com.chequetrack.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amount in English words with the Bangladeshi number system (crore, lakh, thousand),
 * as written on cheques: 125000.50 → "Taka One Lakh Twenty Five Thousand and Paisa Fifty Only".
 * The Bangla version is added with printing (M10).
 */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    public static String taka(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        long taka = scaled.longValue();
        int paisa = scaled.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder("Taka ");
        sb.append(taka == 0 ? "Zero" : words(taka));
        if (paisa > 0) {
            sb.append(" and Paisa ").append(belowHundred(paisa));
        }
        return sb.append(" Only").toString();
    }

    /** Words for a whole number: crore groups can themselves be large ("One Hundred Crore"). */
    static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        long rest = n % 10_000_000;
        if (crore > 0) {
            append(sb, words(crore) + " Crore");
        }
        int lakh = (int) (rest / 100_000);
        int thousand = (int) (rest / 1000 % 100);
        int hundred = (int) (rest / 100 % 10);
        int below = (int) (rest % 100);
        if (lakh > 0) {
            append(sb, belowHundred(lakh) + " Lakh");
        }
        if (thousand > 0) {
            append(sb, belowHundred(thousand) + " Thousand");
        }
        if (hundred > 0) {
            append(sb, ONES[hundred] + " Hundred");
        }
        if (below > 0) {
            append(sb, belowHundred(below));
        }
        return sb.toString();
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return n % 10 == 0 ? TENS[n / 10] : TENS[n / 10] + " " + ONES[n % 10];
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(part);
    }
}
