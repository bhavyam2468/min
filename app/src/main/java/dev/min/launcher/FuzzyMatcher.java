package dev.min.launcher;

import java.util.Locale;

/** Strict substring-first matcher with an edit-distance ceiling of one. */
public final class FuzzyMatcher {
    private FuzzyMatcher() { }

    public static final class Match {
        public final boolean exact;
        public final boolean startsAtName;
        public final boolean startsAtWord;
        public final int position;

        private Match(boolean exact, boolean startsAtName, boolean startsAtWord, int position) {
            this.exact = exact;
            this.startsAtName = startsAtName;
            this.startsAtWord = startsAtWord;
            this.position = position;
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static Match match(String query, String candidate) {
        String needle = normalize(query);
        String haystack = normalize(candidate);
        if (needle.isEmpty() || haystack.isEmpty()) {
            return needle.isEmpty() ? new Match(true, true, true, 0) : null;
        }
        int substring = haystack.indexOf(needle);
        if (substring >= 0) {
            return new Match(true, substring == 0, isWordStart(haystack, substring), substring);
        }
        if (oneEditAway(needle, haystack)) {
            return new Match(false, true, true, 0);
        }
        for (int i = 0; i < haystack.length(); i++) {
            if (!isWordStart(haystack, i)) {
                continue;
            }
            if (oneEditAwayFromPrefix(needle, haystack, i)) {
                return new Match(false, false, true, i);
            }
        }
        return null;
    }

    private static boolean isWordStart(String text, int index) {
        return index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
    }

    private static boolean oneEditAwayFromPrefix(String query, String candidate, int start) {
        int available = candidate.length() - start;
        int lowerLength = Math.max(1, query.length() - 1);
        int upperLength = Math.min(available, query.length() + 1);
        for (int length = lowerLength; length <= upperLength; length++) {
            if (editDistanceAtMostOne(query, candidate.substring(start, start + length))) {
                return true;
            }
        }
        return false;
    }

    private static boolean oneEditAway(String first, String second) {
        if (Math.abs(first.length() - second.length()) > 1) {
            return false;
        }
        return editDistanceAtMostOne(first, second);
    }

    /** Returns true only for exactly one edit, never a loose subsequence. */
    private static boolean editDistanceAtMostOne(String first, String second) {
        int firstLength = first.length();
        int secondLength = second.length();
        if (Math.abs(firstLength - secondLength) > 1) {
            return false;
        }
        int firstIndex = 0;
        int secondIndex = 0;
        int edits = 0;
        while (firstIndex < firstLength && secondIndex < secondLength) {
            if (first.charAt(firstIndex) == second.charAt(secondIndex)) {
                firstIndex++;
                secondIndex++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (firstLength > secondLength) {
                firstIndex++;
            } else if (secondLength > firstLength) {
                secondIndex++;
            } else {
                firstIndex++;
                secondIndex++;
            }
        }
        return edits + (firstLength - firstIndex) + (secondLength - secondIndex) == 1;
    }
}
