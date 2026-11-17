package village.dungeonguard.client;

import java.util.Locale;
import java.util.regex.Pattern;

public final class QuietFireWarnings {
    private static final Pattern FORMATTING = Pattern.compile("(?i)\u00a7[0-9a-fk-or]");

    private QuietFireWarnings() {
    }

    public static boolean shouldSuppress(boolean systemMessage, String text) {
        if (!systemMessage || text == null || text.length() > 128) {
            return false;
        }
        String plain = FORMATTING.matcher(text).replaceAll("").trim().toLowerCase(Locale.ROOT);
        return plain.equals("you cannot light fires") || plain.equals("you cannot light fires.") || plain.equals("you cannot light fires!");
    }
}

