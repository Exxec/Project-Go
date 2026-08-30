package com.ssmt.tm;

import java.util.Locale;

/**
 * Deterministic normalized text similarity.
 */
final class TextSimilarity {
    private TextSimilarity() {
    }

    static double score(String left, String right) {
        int[] first = normalize(left).codePoints().toArray();
        int[] second = normalize(right).codePoints().toArray();
        int maximum = Math.max(first.length, second.length);
        if (maximum == 0) {
            return 1.0;
        }
        return 1.0 - (double) distance(first, second) / maximum;
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static int distance(int[] left, int[] right) {
        int[] previous = new int[right.length + 1];
        int[] current = new int[right.length + 1];
        for (int index = 0; index <= right.length; index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length; leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length; rightIndex++) {
                int replacement = left[leftIndex - 1] == right[rightIndex - 1] ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + replacement);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length];
    }
}
