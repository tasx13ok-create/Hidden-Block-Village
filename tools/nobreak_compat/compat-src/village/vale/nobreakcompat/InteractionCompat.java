package village.vale.nobreakcompat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;

/** Original 1.12 adapter: public native block activation override, then bounded vanilla fallback. */
public final class InteractionCompat {
    private InteractionCompat() {}
    private static final ConcurrentHashMap<Material,Boolean> CACHE=new ConcurrentHashMap<>();
    private static final Set<String> FALLBACK=new HashSet<>(Arrays.asList(
        "CHEST","TRAPPED_CHEST","ENDER_CHEST","FURNACE","BURNING_FURNACE","WORKBENCH","ANVIL",
        "ENCHANTMENT_TABLE","BREWING_STAND","DROPPER","DISPENSER","HOPPER","BEACON","JUKEBOX","NOTE_BLOCK",
        "WOODEN_DOOR","IRON_DOOR_BLOCK","SPRUCE_DOOR","BIRCH_DOOR","JUNGLE_DOOR","ACACIA_DOOR","DARK_OAK_DOOR",
        "TRAP_DOOR","IRON_TRAPDOOR","STONE_BUTTON","WOOD_BUTTON","LEVER","FENCE_GATE","SPRUCE_FENCE_GATE",
        "BIRCH_FENCE_GATE","JUNGLE_FENCE_GATE","ACACIA_FENCE_GATE","DARK_OAK_FENCE_GATE","BED_BLOCK",
        "DIODE_BLOCK_OFF","DIODE_BLOCK_ON","REDSTONE_COMPARATOR_OFF","REDSTONE_COMPARATOR_ON","DAYLIGHT_DETECTOR","DAYLIGHT_DETECTOR_INVERTED"));
    public static boolean isInteractable(Material material) {
        if(material==null||material==Material.AIR)return false;
        Boolean cached=CACHE.get(material);if(cached!=null)return cached;
        boolean answer=resolve(material);if(CACHE.size()<4096)CACHE.putIfAbsent(material,answer);return answer;
    }
    private static boolean resolve(Material material) {
        try {
            Class<?> magic=Class.forName("org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers");
            Method lookup=magic.getMethod("getBlock",Material.class);Object block=lookup.invoke(null,material);
            Class<?> base=lookup.getReturnType();
            Class<?>[] signature={Class.forName("net.minecraft.world.World"),Class.forName("net.minecraft.util.math.BlockPos"),
                Class.forName("net.minecraft.block.state.IBlockState"),Class.forName("net.minecraft.entity.player.EntityPlayer"),
                Class.forName("net.minecraft.util.EnumHand"),Class.forName("net.minecraft.util.EnumFacing"),float.class,float.class,float.class};
            if(block!=null)for(Method method:block.getClass().getMethods()) {
                // Magma remaps reflected NMS method/type names for Bukkit plugins.
                // Exact Class identities remain stable across that public remapping.
                if(method.getReturnType()==boolean.class&&Arrays.equals(method.getParameterTypes(),signature))
                    return method.getDeclaringClass()!=base;
            }
        } catch(ReflectiveOperationException|LinkageError|SecurityException unavailable) {
            // Exact-platform public reflection failed; this known vanilla list is the only fallback.
        }
        return FALLBACK.contains(material.name());
    }
}
