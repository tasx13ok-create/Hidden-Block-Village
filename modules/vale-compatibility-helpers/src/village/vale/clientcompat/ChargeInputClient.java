package village.vale.clientcompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Client input observation only. The installed native method still performs its original action. */
@SideOnly(Side.CLIENT)
public final class ChargeInputClient {
    private ChargeInputClient() {}
    public static boolean allow(Object binding){
        if(!(binding instanceof KeyBinding)||!Keyboard.isCreated())return false;
        Minecraft mc=Minecraft.getMinecraft();
        boolean playing=mc!=null&&mc.player!=null&&mc.world!=null&&mc.player.world==mc.world
            &&mc.getConnection()!=null&&mc.currentScreen==null;
        return PhysicalChargeKey.allow(((KeyBinding)binding).getKeyCode(),Keyboard.getEventKey(),
            Keyboard.getEventKeyState(),Keyboard.isRepeatEvent(),playing);
    }
}
