package village.vale;

/** Shared, Minecraft-independent rules for authoritative server state. */
public final class Rules {
    private Rules() {}
    public static int clampResolve(int value) { return Math.max(0, Math.min(100, value)); }
    public static int levelForXp(int xp) { return Math.min(20, 1 + (int)Math.sqrt(Math.max(0, xp) / 100.0)); }
    public static int spendResolve(int current, int cost) {
        if (cost < 1 || cost > 100) throw new IllegalArgumentException("Resolve cost must be between 1 and 100.");
        int available = clampResolve(current);
        return available < cost ? -1 : available - cost;
    }
    public static long cooldownSeconds(long now, long next) { return Math.max(0, (next - now + 19) / 20); }
    public static boolean recordReceipt(java.util.Map<String, String> receipts, String receipt, String request) {
        if (receipt.isEmpty()) return true;
        String previous = receipts.get(receipt);
        if (previous != null) {
            if (!previous.equals(request)) throw new IllegalArgumentException("Receipt already belongs to a different grant.");
            return false;
        }
        receipts.put(receipt, request);
        return true;
    }
}
