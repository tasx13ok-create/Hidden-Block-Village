package village.vale.arsenal;

/** Server-independent decision used by the native ledger adapter. */
public final class CraftedBindingRules {
    private CraftedBindingRules() {}
    public enum Decision { REJECT, ALREADY_BOUND, BIND }
    public static Decision decide(boolean certified, boolean exactItem, int count,
            boolean reservedSerialPresent, boolean validExisting, boolean receiptUsed) {
        if (!certified || !exactItem || count != 1) return Decision.REJECT;
        if (reservedSerialPresent) return validExisting ? Decision.ALREADY_BOUND : Decision.REJECT;
        return receiptUsed ? Decision.REJECT : Decision.BIND;
    }
}
