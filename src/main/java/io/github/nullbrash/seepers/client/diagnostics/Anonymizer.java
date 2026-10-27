package io.github.nullbrash.seepers.client.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Strips personal data from text that a tester may send (owner's rule): the player's name and UUID, the OS
 * account name and home folder, the server address and non-local IP addresses become placeholders.
 */
final class Anonymizer {
    /** Network addresses only: after a slash or with a port. Version numbers like 21.0.12.1 stay. */
    private static final Pattern IPV4 = Pattern.compile("(?<=/)\\d{1,3}(?:\\.\\d{1,3}){3}|\\d{1,3}(?:\\.\\d{1,3}){3}(?=:\\d)");
    private static final Pattern SERVER = Pattern.compile("(Connecting to )[^,\\s]+(, \\d+)");

    private record Rule(Pattern pattern, String replacement) {}

    private static volatile List<Rule> rules;

    static String clean(String text) {
        List<Rule> list = rules();
        for (Rule rule : list) text = rule.pattern().matcher(text).replaceAll(rule.replacement());
        text = SERVER.matcher(text).replaceAll("$1<server>$2");
        Matcher ip = IPV4.matcher(text);
        StringBuilder out = new StringBuilder();
        while (ip.find()) {
            String found = ip.group();
            boolean local = found.startsWith("127.") || found.equals("0.0.0.0") || found.startsWith("192.168.") || found.startsWith("10.");
            ip.appendReplacement(out, local ? Matcher.quoteReplacement(found) : "<ip>");
        }
        ip.appendTail(out);
        return out.toString();
    }

    private static List<Rule> rules() {
        List<Rule> list = rules;
        if (list != null) return list;
        list = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home != null && home.length() > 3) {
            // Longest first: the home folder contains the account name.
            list.add(new Rule(Pattern.compile(Pattern.quote(home), Pattern.CASE_INSENSITIVE), "<home>"));
            list.add(new Rule(Pattern.compile(Pattern.quote(home.replace('\\', '/')), Pattern.CASE_INSENSITIVE), "<home>"));
        }
        User user = Minecraft.getInstance().getUser();
        UUID uuid = user.getProfileId();
        if (uuid != null) {
            list.add(new Rule(Pattern.compile(Pattern.quote(uuid.toString()), Pattern.CASE_INSENSITIVE), "<player-uuid>"));
            list.add(new Rule(Pattern.compile(Pattern.quote(uuid.toString().replace("-", "")), Pattern.CASE_INSENSITIVE), "<player-uuid>"));
        }
        word(list, user.getName(), "<player>");
        word(list, System.getProperty("user.name"), "<user>");
        rules = list;
        return list;
    }

    /** Whole words only: a short name must not eat parts of other words. */
    private static void word(List<Rule> list, String value, String replacement) {
        if (value == null || value.isBlank()) return;
        list.add(new Rule(Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(value) + "(?![\\p{L}\\p{N}_])"), replacement));
    }

    private Anonymizer() {}
}
