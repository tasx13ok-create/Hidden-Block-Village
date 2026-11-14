package village.fieldhud;
import net.minecraft.client.gui.GuiScreen;
/** Compatibility entry redirects into the single Naruto settings hub. */
@Deprecated public final class AdvancedHudScreen extends GuiScreen {
    private final GuiScreen parent;
    public AdvancedHudScreen(){this(null);}public AdvancedHudScreen(GuiScreen parent){this.parent=parent;}
    @Override public void initGui(){mc.displayGuiScreen(new HudScreen(parent));}
}
