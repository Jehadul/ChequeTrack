package com.chequetrack.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountInWords {

    private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
            "Nineteen" };
    private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
            "Ninety" };

    /** Bangla has its own word for every number from 1 to 99. */
    private static final String[] BN = { "", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
            "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ",
            "বিশ", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আঠাশ", "ঊনত্রিশ",
            "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "ঊনচল্লিশ",
            "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "ঊনপঞ্চাশ",
            "পঞ্চাশ", "একান্ন", "বায়ান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "ঊনষাট",
            "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "ঊনসত্তর",
            "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "ঊনআশি",
            "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "ঊননব্বই",
            "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই" };

    private static final Units EN_UNITS = new Units("Crore", "Lakh", "Thousand", "Hundred");
    private static final Units BN_UNITS = new Units("কোটি", "লক্ষ", "হাজার", "শত");

    private AmountInWords() {
    }

    public enum Language { ENGLISH, BANGLA }

    public static String of(BigDecimal amount, Language language) {
        return language == Language.BANGLA ? bangla(amount) : taka(amount);
    }

    public static String taka(BigDecimal amount) {
        BigDecimal scaled = scaled(amount);
        long taka = scaled.longValue();
        int paisa = paisa(scaled);
        StringBuilder sb = new StringBuilder("Taka ");
        sb.append(taka == 0 ? "Zero" : words(taka, EN_UNITS, AmountInWords::english));
        if (paisa > 0) {
            sb.append(" and Paisa ").append(english(paisa));
        }
        return sb.append(" Only").toString();
    }

    public static String bangla(BigDecimal amount) {
        BigDecimal scaled = scaled(amount);
        long taka = scaled.longValue();
        int paisa = paisa(scaled);
        StringBuilder sb = new StringBuilder("টাকা ");
        sb.append(taka == 0 ? "শূন্য" : words(taka, BN_UNITS, n -> BN[n]));
        if (paisa > 0) {
            sb.append(" এবং ").append(BN[paisa]).append(" পয়সা");
        }
        return sb.append(" মাত্র").toString();
    }

    /** Words for a whole number: crore groups can themselves be large ("One Hundred Crore"). */
    private static String words(long n, Units units, BelowHundred below100) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        long rest = n % 10_000_000;
        if (crore > 0) {
            append(sb, words(crore, units, below100) + " " + units.crore());
        }
        int lakh = (int) (rest / 100_000);
        int thousand = (int) (rest / 1000 % 100);
        int hundred = (int) (rest / 100 % 10);
        int below = (int) (rest % 100);
        if (lakh > 0) {
            append(sb, below100.words(lakh) + " " + units.lakh());
        }
        if (thousand > 0) {
            append(sb, below100.words(thousand) + " " + units.thousand());
        }
        if (hundred > 0) {
            append(sb, below100.words(hundred) + " " + units.hundred());
        }
        if (below > 0) {
            append(sb, below100.words(below));
        }
        return sb.toString();
    }

    private static String english(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return n % 10 == 0 ? TENS[n / 10] : TENS[n / 10] + " " + ONES[n % 10];
    }

    private static BigDecimal scaled(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        return scaled;
    }

    private static int paisa(BigDecimal scaled) {
        return scaled.remainder(BigDecimal.ONE).movePointRight(2).intValue();
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(part);
    }

    private record Units(String crore, String lakh, String thousand, String hundred) {
    }

    @FunctionalInterface
    private interface BelowHundred {
        String words(int n);
    }
}
