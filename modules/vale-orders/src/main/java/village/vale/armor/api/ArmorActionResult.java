package village.vale.armor.api;

/** Stable machine-readable outcomes; a GUI can refresh its owner-only snapshot after every request. */
public final class ArmorActionResult {
    public enum Code { ACCEPTED, ALREADY_RECORDED, BLOCKED, INVALID_ACTION, INVALID_REWARD, RECEIPT_CONFLICT, WRONG_CONTEXT }
    public final Code code;
    public final boolean accepted;
    public final boolean successful;
    public final String message;
    private ArmorActionResult(Code code,String message){this.code=code;this.accepted=code==Code.ACCEPTED;this.successful=accepted||code==Code.ALREADY_RECORDED;this.message=message;}
    public static ArmorActionResult of(Code code,String message){return new ArmorActionResult(code,message);}
}
