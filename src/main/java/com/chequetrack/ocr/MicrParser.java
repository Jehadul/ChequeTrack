package com.chequetrack.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MicrParser {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final int ROUTING_LENGTH = 9;

    private MicrParser() {
    }

    /**
     * Reads a BACH MICR line: cheque number, routing number (9 digits), account number, transaction code.
     * The routing number is the anchor: a 9-digit group that is a known bank branch wins; failing that,
     * the first 9-digit group on the longest line of digits is taken as the routing number.
     */
    public static Optional<MicrLine> parse(String text, Predicate<String> knownRouting) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<List<String>> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            List<String> tokens = tokens(line);
            if (!tokens.isEmpty()) {
                lines.add(tokens);
            }
        }
        // 1. a group that is exactly a known routing number
        for (List<String> tokens : lines) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).length() == ROUTING_LENGTH && knownRouting.test(tokens.get(i))) {
                    return Optional.of(build(tokens, i, true));
                }
            }
        }
        // 2. a known routing number run together with its neighbours
        for (List<String> tokens : lines) {
            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                for (int off = 0; t.length() > ROUTING_LENGTH && off + ROUTING_LENGTH <= t.length(); off++) {
                    String sub = t.substring(off, off + ROUTING_LENGTH);
                    if (knownRouting.test(sub)) {
                        List<String> split = new ArrayList<>(tokens.subList(0, i));
                        if (off > 0) {
                            split.add(t.substring(0, off));
                        }
                        int at = split.size();
                        split.add(sub);
                        if (off + ROUTING_LENGTH < t.length()) {
                            split.add(t.substring(off + ROUTING_LENGTH));
                        }
                        split.addAll(tokens.subList(i + 1, tokens.size()));
                        return Optional.of(build(split, at, true));
                    }
                }
            }
        }
        // 3. no known branch: best guess on the line with the most digits
        List<String> longest = lines.stream()
                .max((a, b) -> Integer.compare(digitCount(a), digitCount(b)))
                .orElse(List.of());
        for (int i = 0; i < longest.size(); i++) {
            if (longest.get(i).length() == ROUTING_LENGTH) {
                return Optional.of(build(longest, i, false));
            }
        }
        return Optional.empty();
    }

    private static MicrLine build(List<String> tokens, int routingAt, boolean known) {
        String cheque = null;
        if (routingAt > 0) {
            String before = tokens.get(routingAt - 1);
            if (before.length() >= 4 && before.length() <= 12) {
                cheque = before;
            }
        }
        List<String> after = new ArrayList<>(tokens.subList(routingAt + 1, tokens.size()));
        String tx = null;
        if (after.size() >= 2 && after.get(after.size() - 1).length() <= 2) {
            tx = after.remove(after.size() - 1);
        }
        String account = String.join("", after);
        if (account.length() < 4 || account.length() > 30) {
            account = null;
        }
        return new MicrLine(cheque, tokens.get(routingAt), account, tx, known);
    }

    private static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = DIGITS.matcher(line);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static int digitCount(List<String> tokens) {
        return tokens.stream().mapToInt(String::length).sum();
    }
}
