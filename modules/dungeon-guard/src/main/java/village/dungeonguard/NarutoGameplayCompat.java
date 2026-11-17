package village.dungeonguard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.OperatorAccess;

public final class NarutoGameplayCompat {
    private static final String KEEP_XP_RULE = "keepNinjaXp";
    private static final Map<UUID, Integer> LAST_KG_ATTEMPT = new HashMap<UUID, Integer>();
    private static boolean registered;
    private static Api api;
    private static Double originalMultiplier;
    private static Double appliedMultiplier;

    private NarutoGameplayCompat() {
    }

    public static synchronized void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register((Object)new NarutoGameplayCompat());
            registered = true;
        }
    }

    public static void started(MinecraftServer server) {
        LAST_KG_ATTEMPT.clear();
        api = null;
        appliedMultiplier = null;
        originalMultiplier = null;
        if (NarutoGameplayCompat.active() && NarutoGameplayCompat.provider() != null) {
            NarutoGameplayCompat.applySettings(server);
        }
    }

    public static void stopped() {
        if (api != null && originalMultiplier != null && appliedMultiplier != null) {
            try {
                if (Double.compare(NarutoGameplayCompat.api.xpMultiplier.getDouble(null), appliedMultiplier) == 0) {
                    NarutoGameplayCompat.api.xpMultiplier.setDouble(null, originalMultiplier);
                }
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot restore Naruto XP multiplier", e);
            }
        }
        LAST_KG_ATTEMPT.clear();
        api = null;
        appliedMultiplier = null;
        originalMultiplier = null;
    }

    public static void applySettings(MinecraftServer server) {
        if (!NarutoGameplayCompat.active() || NarutoGameplayCompat.provider() == null) {
            return;
        }
        Api methods = NarutoGameplayCompat.api();
        if (GuardRuntime.policy().preserveNinjaXpOnDeath && server != null) {
            for (WorldServer world : server.worlds) {
                if (world == null) continue;
                NarutoGameplayCompat.keepXp((World)world);
            }
        }
        double value = GuardRuntime.policy().ninjaXpGainMultiplier;
        try {
            if (value >= 0.0) {
                if (originalMultiplier == null) {
                    originalMultiplier = methods.xpMultiplier.getDouble(null);
                }
                methods.xpMultiplier.setDouble(null, value);
                appliedMultiplier = value;
            } else if (originalMultiplier != null && appliedMultiplier != null) {
                if (Double.compare(methods.xpMultiplier.getDouble(null), appliedMultiplier) == 0) {
                    methods.xpMultiplier.setDouble(null, originalMultiplier);
                }
                appliedMultiplier = null;
                originalMultiplier = null;
            }
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot apply Naruto XP multiplier", e);
        }
    }

    public static double nativeXpMultiplier() {
        if (NarutoGameplayCompat.provider() == null) {
            throw new IllegalStateException("AHZNB NarutoMod is not installed.");
        }
        try {
            return NarutoGameplayCompat.api().xpMultiplier.getDouble(null);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Naruto XP multiplier", e);
        }
    }

    public static void givePuppetKit(EntityPlayerMP player) {
        if (player == null || player.world.isRemote || player.getServer() == null || !player.getServer().isCallingFromMinecraftThread() || !OperatorAccess.isOperator((EntityPlayer)player)) {
            throw new IllegalStateException("Only a connected operator can receive the puppet setup kit.");
        }
        if (NarutoGameplayCompat.provider() == null) {
            throw new IllegalStateException("AHZNB NarutoMod is not installed.");
        }
        NarutoGameplayCompat.api();
        String[] names = new String[]{"scroll_puppet", "scroll_karasu", "scroll_sanshouo"};
        ItemStack[] kit = new ItemStack[names.length];
        int[] slots = new int[names.length];
        for (int i = 0; i < names.length; ++i) {
            ResourceLocation id = new ResourceLocation("narutomod", names[i]);
            if (!Item.REGISTRY.containsKey(id)) {
                throw new IllegalStateException("Missing native puppet item: " + id);
            }
            kit[i] = new ItemStack(Item.REGISTRY.getObject(id));
            if (!kit[i].isEmpty()) continue;
            throw new IllegalStateException("Unavailable native puppet item: " + id);
        }
        int count = 0;
        for (int slot = 0; slot < player.inventory.mainInventory.size() && count < kit.length; ++slot) {
            if (!((ItemStack)player.inventory.mainInventory.get(slot)).isEmpty()) continue;
            slots[count++] = slot;
        }
        if (count < kit.length) {
            throw new IllegalStateException("Free three main-inventory slots for the puppet kit.");
        }
        for (int i = 0; i < kit.length; ++i) {
            player.inventory.mainInventory.set(slots[i], kit[i]);
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void worldLoaded(WorldEvent.Load event) {
        if (NarutoGameplayCompat.active() && GuardRuntime.policy().preserveNinjaXpOnDeath && NarutoGameplayCompat.provider() != null) {
            NarutoGameplayCompat.keepXp(event.getWorld());
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (NarutoGameplayCompat.active() && GuardRuntime.policy().preserveNinjaXpOnDeath && NarutoGameplayCompat.provider() != null) {
            NarutoGameplayCompat.keepXp(event.player.world);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void death(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP && NarutoGameplayCompat.active() && GuardRuntime.policy().preserveNinjaXpOnDeath && NarutoGameplayCompat.provider() != null) {
            NarutoGameplayCompat.keepXp(event.getEntityLiving().world);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_KG_ATTEMPT.remove(event.player.getUniqueID());
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void attack(LivingAttackEvent event) {
        if (event.getEntityLiving().world.isRemote || !NarutoGameplayCompat.active() || !GuardRuntime.policy().preventSusanooSelfDamage || NarutoGameplayCompat.provider() == null || event.getAmount() <= 0.0f) {
            return;
        }
        if (NarutoGameplayCompat.isSusanooSelfHit(event.getEntityLiving(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    public static boolean isSusanooSelfHit(EntityLivingBase victim, DamageSource source) {
        if (victim == null || source == null || victim.world == null || victim.world.isRemote || NarutoGameplayCompat.provider() == null) {
            return false;
        }
        Api methods = NarutoGameplayCompat.api();
        EntityPlayer victimOwner = NarutoGameplayCompat.ownerOfAvatar((Entity)victim, methods);
        if (victimOwner == null && victim instanceof EntityPlayer && NarutoGameplayCompat.hasOwnAvatar((EntityPlayer)victim, methods)) {
            victimOwner = (EntityPlayer)victim;
        }
        if (victimOwner == null) {
            return false;
        }
        Entity sourceEntity = source.getTrueSource();
        EntityPlayer attacker = sourceEntity instanceof EntityPlayer ? (EntityPlayer)sourceEntity : NarutoGameplayCompat.ownerOfAvatar(sourceEntity, methods);
        return attacker != null && attacker.world == victim.world && attacker.getUniqueID().equals(victimOwner.getUniqueID());
    }

    public static boolean allowKgDistribution(Map<?, ?> dependencies) {
        if (!NarutoGameplayCompat.active() || !GuardRuntime.policy().enforceKgXpRequirement) {
            return true;
        }
        if (dependencies == null || !(dependencies.get("entity") instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP player = (EntityPlayerMP)dependencies.get("entity");
        if (player.world == null || player.world.isRemote || dependencies.get("world") != player.world || !player.isEntityAlive() || player.isSpectator() || NarutoGameplayCompat.provider() == null) {
            return false;
        }
        double xp = NarutoGameplayCompat.battleXp((EntityPlayer)player);
        if (!Double.isFinite(xp) || xp < 300.0) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !server.isCallingFromMinecraftThread()) {
            return false;
        }
        int tick = server.getTickCounter();
        Integer previous = LAST_KG_ATTEMPT.get(player.getUniqueID());
        if (previous != null && tick - previous < 20) {
            return false;
        }
        LAST_KG_ATTEMPT.put(player.getUniqueID(), tick);
        return true;
    }

    private static double battleXp(EntityPlayer player) {
        try {
            return ((Number)NarutoGameplayCompat.api().battleXp.invoke(null, player)).doubleValue();
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read native Naruto XP", e);
        }
    }

    private static boolean hasOwnAvatar(EntityPlayer player, Api methods) {
        Entity riding = player.getRidingEntity();
        if (NarutoGameplayCompat.samePlayer(NarutoGameplayCompat.ownerOfAvatar(riding, methods), player)) {
            return true;
        }
        try {
            int id = ((Number)methods.summonedId.invoke(null, player)).intValue();
            Entity summoned = player.world.getEntityByID(id);
            return summoned != null && summoned.isEntityAlive() && NarutoGameplayCompat.samePlayer(NarutoGameplayCompat.ownerOfAvatar(summoned, methods), player);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot resolve native Susanoo", e);
        }
    }

    private static EntityPlayer ownerOfAvatar(Entity entity, Api methods) {
        if (entity == null || !methods.susanoo.isInstance(entity)) {
            return null;
        }
        try {
            Object owner = methods.owner.invoke((Object)entity, new Object[0]);
            return owner instanceof EntityPlayer ? (EntityPlayer)owner : null;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot resolve native Susanoo owner", e);
        }
    }

    private static boolean samePlayer(EntityPlayer first, EntityPlayer second) {
        return first != null && second != null && first.world == second.world && first.getUniqueID().equals(second.getUniqueID());
    }

    private static void keepXp(World world) {
        if (world != null && !world.isRemote && !world.getGameRules().getBoolean(KEEP_XP_RULE)) {
            world.getGameRules().setOrCreateGameRule(KEEP_XP_RULE, "true");
        }
    }

    private static boolean active() {
        return GuardRuntime.ready() && GuardRuntime.policy().enabled;
    }

    private static ModContainer provider() {
        return (ModContainer)Loader.instance().getIndexedModList().get("narutomod");
    }

    private static Api api() {
        if (api == null) {
            ModContainer mod = NarutoGameplayCompat.provider();
            if (mod == null || !"0.3.1-beta".equals(mod.getVersion())) {
                throw new IllegalStateException("Dungeon Guard gameplay compatibility requires the verified AHZNB NarutoMod 0.3.1-beta API.");
            }
            try {
                api = new Api();
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Naruto gameplay API", e);
            }
        }
        return api;
    }

    private static final class Api {
        final Method battleXp;
        final Method owner;
        final Method summonedId;
        final Class<?> susanoo;
        final Field xpMultiplier;

        Api() throws ReflectiveOperationException {
            ClassLoader loader = NarutoGameplayCompat.class.getClassLoader();
            this.battleXp = Class.forName("net.narutomod.PlayerTracker", true, loader).getMethod("getBattleXp", EntityPlayer.class);
            this.susanoo = Class.forName("net.narutomod.entity.EntitySusanooBase", true, loader);
            this.owner = this.susanoo.getMethod("getOwnerPlayer", new Class[0]);
            this.summonedId = Class.forName("net.narutomod.procedure.ProcedureSusanoo", true, loader).getMethod("getSummonedSusanooId", Entity.class);
            this.xpMultiplier = Class.forName("net.narutomod.ModConfig", true, loader).getField("NINJAXP_MULTIPLIER");
            if (this.battleXp.getReturnType() != Double.TYPE || this.owner.getReturnType() != EntityLivingBase.class || this.summonedId.getReturnType() != Integer.TYPE || this.xpMultiplier.getType() != Double.TYPE) {
                throw new NoSuchMethodException("Unexpected Naruto gameplay API types");
            }
        }
    }
}

