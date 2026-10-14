package fr.fruityhedgeh0g.utilities;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Finds a person from a name typed freely ("je viens avec"), so that nobody needs the list of who is signed up: the
 * typed name is compared with each candidate's, without accents, case or punctuation, first name and last name in
 * either order. A match needs a likeness of at least {@link #THRESHOLD}, and no other candidate nearly as alike
 * (within {@link #MARGIN}), unless it bears the very name typed.
 */
public final class NameMatching {

    /** How alike the typed name must be to the candidate's, from 0 (nothing in common) to 1 (the same). */
    public static final double THRESHOLD = 0.8;
    /** Two candidates closer than this to each other are too alike to choose between. */
    public static final double MARGIN = 0.1;

    private NameMatching() {
    }

    /** What the typed name finds among the candidates. */
    public sealed interface Outcome<T> {
        record Found<T>(T match) implements Outcome<T> {
        }

        record NotFound<T>() implements Outcome<T> {
        }

        /** Several candidates match about as well: the typed name must be more precise. */
        record Ambiguous<T>() implements Outcome<T> {
        }
    }

    /** The candidate whose first and last names are most like the typed name, if alike enough and unambiguous. */
    public static <T> Outcome<T> find(String typed, List<T> candidates, Function<T, String> firstName, Function<T, String> lastName) {
        List<Scored<T>> scored = candidates.stream()
                .map(c -> new Scored<>(c, likeness(typed, firstName.apply(c), lastName.apply(c))))
                .filter(s -> s.likeness() >= THRESHOLD)
                .sorted(Comparator.comparingDouble(Scored<T>::likeness).reversed())
                .toList();
        if (scored.isEmpty()) return new Outcome.NotFound<>();
        // The very name typed wins, unless two people bear it
        boolean exact = scored.getFirst().likeness() == 1 && (scored.size() == 1 || scored.get(1).likeness() < 1);
        if (!exact && scored.size() > 1 && scored.get(0).likeness() - scored.get(1).likeness() < MARGIN) return new Outcome.Ambiguous<>();
        return new Outcome.Found<>(scored.getFirst().candidate());
    }

    /** How alike a typed name is to a person's, first and last names in either order. */
    public static double likeness(String typed, String firstName, String lastName) {
        String wanted = normalized(typed);
        if (wanted.isEmpty()) return 0;
        return Math.max(ratio(wanted, normalized(firstName + " " + lastName)), ratio(wanted, normalized(lastName + " " + firstName)));
    }

    /** Lower case, without accents, punctuation turned to spaces, spaces collapsed. */
    static String normalized(String name) {
        return Optional.ofNullable(name).map(n -> Normalizer.normalize(n, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .strip()).orElse("");
    }

    /** 1 minus the edit distance relative to the longer text. */
    private static double ratio(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 1 : 1 - (double) distance(a, b) / longest;
    }

    /** Levenshtein distance: the fewest insertions, deletions and substitutions turning one text into the other. */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private record Scored<T>(T candidate, double likeness) {
    }
}
