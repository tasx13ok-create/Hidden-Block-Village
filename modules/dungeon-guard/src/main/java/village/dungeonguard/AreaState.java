package village.dungeonguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Small immutable display value. It grants no world-editing permission. */
public final class AreaState {
    public static final List<String> ELEMENTS = Collections.unmodifiableList(Arrays.asList(
            "GENERAL", "FIRE", "WATER", "WIND", "EARTH", "LIGHTNING", "SAND", "ICE"));
    public static final AreaState EMPTY = new AreaState("", "GENERAL", "", "");
    public final String key, element, title, boss;

    public AreaState(String key, String element, String title, String boss) {
        requireText(key, 128, true, "area key");
        requireElement(element);
        requireText(title, 80, key.isEmpty(), "area title");
        requireText(boss, 80, true, "boss name");
        if (key.isEmpty() && (!title.isEmpty() || !boss.isEmpty())) throw new IllegalArgumentException("Empty area must have no labels.");
        this.key = key; this.element = element; this.title = title; this.boss = boss;
    }
    public boolean isEmpty() { return key.isEmpty(); }
    public boolean isBoss() { return !boss.isEmpty(); }
    public String heading() {
        return (element.equals("GENERAL") ? "" : element + " / ") + (isBoss() ? "BOSS AREA" : "DUNGEON");
    }
    public static String normalizeElement(String value) {
        if (value == null) throw new IllegalArgumentException("An element is required.");
        String normalized = value.toUpperCase(Locale.ROOT);
        requireElement(normalized); return normalized;
    }
    public static void requireElement(String element) {
        if (!ELEMENTS.contains(element)) throw new IllegalArgumentException("Element must be " + String.join(", ", ELEMENTS) + ".");
    }
    public static void requireText(String text, int limit, boolean emptyAllowed, String field) {
        if (text == null || text.length() > limit || (!emptyAllowed && text.trim().isEmpty()) || !text.equals(text.trim()))
            throw new IllegalArgumentException("Invalid " + field + "; use " + (emptyAllowed ? "0" : "1") + "-" + limit + " characters without outer spaces.");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || c == '\u00a7' || Character.getType(c) == Character.FORMAT)
                throw new IllegalArgumentException("Formatting and control characters are not allowed in " + field + ".");
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("Invalid Unicode in " + field + ".");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Unicode in " + field + ".");
        }
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof AreaState)) return false;
        AreaState a = (AreaState) other;
        return key.equals(a.key) && element.equals(a.element) && title.equals(a.title) && boss.equals(a.boss);
    }
    @Override public int hashCode() { return Objects.hash(key, element, title, boss); }
}
