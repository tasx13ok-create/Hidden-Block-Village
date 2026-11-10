package village.vale;

public enum Order {
    EMBER("ember", "Ember Ward", 0xd7733f, "Cinder Arc"),
    CURRENT("current", "Current Ward", 0x37adba, "Flowstep"),
    GALE("gale", "Gale Ward", 0xb8cd86, "Windstride"),
    FLINT("flint", "Flint Ward", 0xb99d72, "Stoneguard"),
    BLOOM("bloom", "Bloom Ward", 0x6ebd81, "Mending Breath"),
    RIME("rime", "Rime Ward", 0x8fcde2, "Stillwinter"),
    STORM("storm", "Storm Ward", 0xa394df, "Skybreak"),
    DUSK("dusk", "Dusk Ward", 0x887b9c, "Quiet Veil"),
    DAWN("dawn", "Dawn Ward", 0xe4c576, "Firstlight");

    public final String key;
    public final String title;
    public final int color;
    public final String skill;
    Order(String key, String title, int color, String skill) {
        this.key = key; this.title = title; this.color = color; this.skill = skill;
    }
}
