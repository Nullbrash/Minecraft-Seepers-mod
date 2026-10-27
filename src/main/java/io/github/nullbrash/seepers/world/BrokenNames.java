package io.github.nullbrash.seepers.world;

/**
 * Broken but stable biome names for distorted twins (owner's rule: readable and memorable — forrest, frrrst, fofofo).
 * Zone 1 doubles a letter, zone 2 drops vowels and drags a consonant, zone 3 stutters on the first syllable.
 */
public final class BrokenNames {
    private static final String VOWELS = "aeiouy";

    /** zone: the twin's folder (seams, repeats, far_lands); path: the original biome path (dark_forest). */
    public static String of(String zone, String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (out.length() > 0) out.append('_');
            out.append(switch (zone) {
                case "seams" -> doubled(word);
                case "repeats" -> dragged(word);
                case "far_lands" -> stuttered(word);
                default -> word;
            });
        }
        return out.toString();
    }

    /** forest -> forrest: the first consonant after the first vowel, twice. */
    private static String doubled(String word) {
        int vowel = firstVowel(word);
        for (int i = Math.max(vowel, 0) + 1; i < word.length(); i++) {
            if (!isVowel(word.charAt(i))) return word.substring(0, i + 1) + word.charAt(i) + word.substring(i + 1);
        }
        return word + word.charAt(word.length() - 1);
    }

    /** forest -> frrrst: vowels gone (but the first letter), the second consonant three times. */
    private static String dragged(String word) {
        StringBuilder bare = new StringBuilder().append(word.charAt(0));
        for (int i = 1; i < word.length(); i++) {
            if (!isVowel(word.charAt(i))) bare.append(word.charAt(i));
        }
        if (bare.length() < 2) return word + word.charAt(word.length() - 1) + word.charAt(word.length() - 1);
        char drag = bare.charAt(1);
        return bare.charAt(0) + String.valueOf(drag).repeat(3) + bare.substring(2);
    }

    /** forest -> fofofo: the first syllable, three times. */
    private static String stuttered(String word) {
        int vowel = firstVowel(word);
        int end = vowel < 0 ? Math.min(2, word.length()) : vowel == 0 ? Math.min(2, word.length()) : vowel + 1;
        return word.substring(0, end).repeat(3);
    }

    private static int firstVowel(String word) {
        for (int i = 0; i < word.length(); i++) if (isVowel(word.charAt(i))) return i;
        return -1;
    }

    private static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }

    private BrokenNames() {}
}
