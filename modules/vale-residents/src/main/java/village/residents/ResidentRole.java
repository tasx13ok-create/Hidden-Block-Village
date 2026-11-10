package village.residents;

public enum ResidentRole {
    ACADEMY("academy", "Academy Guide", 3, 0),
    CONTRACTS("contracts", "Contracts Clerk", -3, 0),
    QUARTERMASTER("quartermaster", "Merit Quartermaster", 0, 3);

    public final String id;
    public final String title;
    public final int offsetX;
    public final int offsetZ;

    ResidentRole(String id, String title, int offsetX, int offsetZ) {
        this.id = id;
        this.title = title;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    public static ResidentRole fromId(String id) {
        for (ResidentRole role : values()) if (role.id.equals(id)) return role;
        return null;
    }
}
