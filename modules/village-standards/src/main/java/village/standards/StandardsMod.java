package village.standards;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

@Mod(modid = StandardsMod.ID, name = "Village Standards", version = StandardsMod.VERSION,
     acceptedMinecraftVersions = "[1.12.2]")
@Mod.EventBusSubscriber(modid = StandardsMod.ID)
public final class StandardsMod {
    public static final String ID = "hvdungeonpictures";
    public static final String VERSION = "0.3.0";
    @Mod.Instance(ID) public static StandardsMod instance;
    @SidedProxy(clientSide = "village.standards.client.ClientProxy", serverSide = "village.standards.CommonProxy")
    public static CommonProxy proxy;
    private static final Map<StandardVariant, ItemStandard> ITEMS = new EnumMap<>(StandardVariant.class);
    private static ItemStandard unassigned;

    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) {
        for (StandardVariant variant : StandardVariant.values()) {
            ItemStandard item = new ItemStandard(variant);
            event.getRegistry().register(item); ITEMS.put(variant, item);
        }
        unassigned = new ItemStandard(null);
        event.getRegistry().register(unassigned);
    }
    public static ItemStandard item(StandardVariant variant) { return variant == null ? unassigned : ITEMS.get(variant); }
    public static Map<StandardVariant, ItemStandard> items() { return Collections.unmodifiableMap(ITEMS); }
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        EntityRegistry.registerModEntity(new ResourceLocation(ID, "dungeon_picture"), EntityStandard.class,
            "dungeon_picture", 0, instance, 64, 20, false);
        proxy.initialize();
    }
    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        event.registerServerCommand(new StandardsCommand());
    }
}
