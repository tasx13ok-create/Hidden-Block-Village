package village.bijuu;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-side follow-through for provider-owned hits. Never casts or pays for the native art. */
@Mod.EventBusSubscriber(modid="hvbijuu")
public final class NativeArmorArts {
    private static final int INTERVAL = 160;
    private static final String LAST = "nativeArtLastHit";
    private static final Map<EntityPlayerMP, Hit> pending = new HashMap<>();

    private static final Map<EntityPlayerMP,AttackEntityEvent> swings = new HashMap<>();

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void rememberSwing(AttackEntityEvent event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP && !(event.getEntityPlayer() instanceof FakePlayer))
            swings.put((EntityPlayerMP)event.getEntityPlayer(),event);
    }

    private static final class Hit {
        final LivingDamageEvent event;
        final int beast;
        Hit(LivingDamageEvent event, int beast) { this.event=event; this.beast=beast; }
    }

    private static boolean recognized(EntityPlayerMP player, DamageSource source, EntityLivingBase victim) {
        String type=source.func_76355_l();
        if ("ninjutsu".equals(type) || "senjutsu".equals(type)) return true;
        if (!"player".equals(type) || source.func_76364_f()!=player) return false;
        // Other abilities can also emit vanilla "player" damage while a sword is held.
        AttackEntityEvent swing=swings.get(player);
        if (swing==null || swing.isCanceled() || swing.getTarget()!=victim) return false;
        swings.remove(player);
        Item item=player.func_184614_ca().func_77973_b();
        ResourceLocation id=Item.field_150901_e.func_177774_c(item);
        if (!(item instanceof ItemSword) || id==null) return false;
        switch (id.func_110624_b()) {
            case "narutomod": case "ahznbswordsplus": case "narutomodaddononero": case "narutomodaddon":
            case "borutomodaddononero": case "kabutoaddon": case "ahznbcursemarkaddon":
                return true;
            default: return false;
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        Entity owner=event.getSource().func_76346_g();
        if (!(owner instanceof EntityPlayerMP) || owner instanceof FakePlayer || !(event.getAmount()>0)) return;
        EntityPlayerMP player=(EntityPlayerMP)owner;
        EntityLivingBase target=event.getEntityLiving();
        if (player.field_70170_p!=target.field_70170_p || player.func_175149_v()
                || !MatatabiPowers.allowed(player,target) || !recognized(player,event.getSource(),target)) return;
        Hit previous=pending.get(player);
        if (previous!=null && !previous.event.isCanceled() && previous.event.getAmount()>0) return;
        int beast=BijuuArmor.full(player);
        if (beast>=0) pending.put(player,new Hit(event,beast));
    }

    @SubscribeEvent
    public static void finish(TickEvent.ServerTickEvent tick) {
        if (tick.phase!=TickEvent.Phase.END) return;
        swings.clear();
        if (pending.isEmpty()) return;
        Map<EntityPlayerMP,Hit> hits=new HashMap<>(pending);
        pending.clear();
        for (Map.Entry<EntityPlayerMP,Hit> entry:hits.entrySet()) settle(entry.getKey(),entry.getValue());
    }

    private static void settle(EntityPlayerMP player, Hit hit) {
        EntityLivingBase target=hit.event.getEntityLiving();
        if (hit.event.isCanceled() || !(hit.event.getAmount()>0) || !player.func_70089_S()
                || player.field_70170_p!=target.field_70170_p || BijuuArmor.full(player)!=hit.beast) return;
        NBTTagCompound state=BijuuArmor.state(player);
        long now=player.field_70170_p.func_82737_E();
        long elapsed=now-state.func_74763_f(LAST);
        if (state.func_74764_b(LAST) && elapsed>=0 && elapsed<INTERVAL) return;
        state.func_74772_a(LAST,now);
        state.func_74768_a("energy",Math.min(100,state.func_74762_e("energy")+8));
        String name;
        switch (hit.beast) {
            case 0: name="Sand Guard"; apply(player,MobEffects.field_76444_x,60,0); break;
            case 1: name="Flame Step"; apply(player,MobEffects.field_76424_c,50,1); break;
            case 2: name="Shell Guard"; apply(player,MobEffects.field_76429_m,60,0); break;
            case 3: name="Lava Mantle"; apply(player,MobEffects.field_76426_n,100,0); break;
            case 4: name="Boil Rush"; apply(player,MobEffects.field_76424_c,35,2); break;
            case 5: name="Slime Bind"; apply(target,MobEffects.field_76421_d,50,1); break;
            case 6: name="Wing Lift"; apply(player,MobEffects.field_76430_j,70,1); break;
            case 7: name="Ink Veil"; apply(target,MobEffects.field_76440_q,40,0); break;
            case 8: name="Chakra Recovery"; apply(player,MobEffects.field_76428_l,65,0); break;
            default: throw new IllegalStateException("Unknown equipped beast "+hit.beast);
        }
        player.func_146105_b(new TextComponentString(name+" | Resonance "+state.func_74762_e("energy")+"/100"),true);
    }

    private static void apply(EntityLivingBase target, Potion effect, int ticks, int strength) {
        if (target.func_70089_S()) target.func_70690_d(new PotionEffect(effect,ticks,strength));
    }
}
