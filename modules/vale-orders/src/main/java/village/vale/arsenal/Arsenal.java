package village.vale.arsenal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.Gear;

/** Integration entry points. This delta belongs inside Core, not a separate mod. */
public final class Arsenal {
    private static final Map<String,ArsenalBlade> ITEMS=new LinkedHashMap<>();
    private Arsenal() {}
    public static void register(RegistryEvent.Register<Item> event) {
        if(!ITEMS.isEmpty())throw new IllegalStateException("Arsenal registry called twice");
        for(ArsenalSpec spec:ArsenalSpec.values()) {
            if(Gear.ITEMS.containsKey(spec.key))throw new IllegalStateException("Duplicate arsenal key "+spec.key);
            ArsenalBlade blade=new ArsenalBlade(spec);
            blade.setRegistryName(new ResourceLocation("valeorders",spec.key));
            blade.setTranslationKey("valeorders."+spec.key);
            event.getRegistry().register(blade);
            Gear.ITEMS.put(spec.key,blade);
            ITEMS.put(spec.key,blade);
        }
    }
    /** -2 delegates non-Arsenal keys to Core; -1 is a locked specialist. */
    public static int price(net.minecraft.entity.player.EntityPlayerMP player,String key) {
        ArsenalSpec spec=ArsenalSpec.byKey(key);
        if(spec==null)return -2;
        net.minecraft.nbt.NBTTagCompound state=village.vale.PlayerState.data(player);
        String starter=state.getString("starterOrder");
        if(starter.isEmpty()||(!spec.order.key.equals(starter)&&!state.getBoolean("study_"+spec.order.key))
                ||village.vale.PlayerState.level(player)<spec.minimumLevel())return -1;
        return spec.meritPrice();
    }
    public static Map<String,ArsenalBlade> items() { return Collections.unmodifiableMap(ITEMS); }
    public static ArsenalBlade item(ArsenalSpec spec) { return spec==null?null:ITEMS.get(spec.key); }
    public static void registerEntities(Object mod) {
        ClawNetwork.register();
        EntityRegistry.registerModEntity(new ResourceLocation("valeorders","arsenal_shot"),ArsenalShot.class,
                "arsenal_shot",100,mod,64,2,true);
        EntityRegistry.registerModEntity(new ResourceLocation("valeorders","arsenal_field"),ArsenalField.class,
                "arsenal_field",101,mod,64,5,false);
        EntityRegistry.registerModEntity(new ResourceLocation("valeorders","weapon_pulse"),WeaponPulse.class,
                "weapon_pulse",122,mod,64,2,false);
    }
    @SideOnly(Side.CLIENT) public static void registerRenderers() { ArsenalClient.renderers(); }
}
