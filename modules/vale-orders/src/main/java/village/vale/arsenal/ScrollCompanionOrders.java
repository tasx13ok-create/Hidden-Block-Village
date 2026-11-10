package village.vale.arsenal;

import java.util.Locale;

/** Original companion orders; saved keys stay stable across item and actor reloads. */
public final class ScrollCompanionOrders {
    private ScrollCompanionOrders() {}

    public enum Order {
        ASSIST("assist", "Assist"), FOLLOW("follow", "Follow"),
        GUARD("guard", "Guard"), HOLD("hold", "Hold");
        public final String key, title;
        Order(String key, String title) { this.key = key; this.title = title; }
        public boolean anchored() { return this == GUARD || this == HOLD; }
    }

    public static Order read(String key) {
        if (key != null) for (Order order : Order.values())
            if (order.key.equals(key.toLowerCase(Locale.ROOT))) return order;
        return Order.ASSIST;
    }

    public static Order next(String key) {
        Order[] values = Order.values();
        return values[(read(key).ordinal() + 1) % values.length];
    }

    /** Automatic assistance is restricted to a recent actual owner combat event. */
    public static boolean recentCombat(int now, int eventTick) {
        long age = (long) now - eventTick;
        return eventTick >= 0 && age >= 0 && age <= 200;
    }

    public static long commandDeadline(long now) {
        return now < 0 ? 0 : now > Long.MAX_VALUE - 200 ? Long.MAX_VALUE : now + 200;
    }

    /** Explicit target commands remain short-lived even if a saved clock moves. */
    public static boolean commandActive(long now, long deadline) {
        return now >= 0 && deadline >= now && deadline - now <= 200;
    }
}
