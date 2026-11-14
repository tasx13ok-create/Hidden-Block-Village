package village.vale.melee;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;

/** Private disposable-world acceptance; never ship this harness as gameplay. */
public final class MeleeNativeChecks {
    private final EntityPlayerMP p;
    private final List<EntityZombie> actors=new ArrayList<>();
    private final Gate gate=new Gate();
    private int assertions,receipt;private long clock;
    private static final Field COOLED=ReflectionHelper.findField(EntityLivingBase.class,"ticksSinceLastSwing","field_184617_aD");
    private MeleeNativeChecks(EntityPlayerMP p){this.p=p;clock=ArsenalAccess.now(p)+200;}
    public static int run(EntityPlayerMP p){MeleeNativeChecks c=new MeleeNativeChecks(p);MinecraftForge.EVENT_BUS.register(c.gate);
        try{c.exercise();return c.assertions;}finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.cleanup();}}
    private void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError("Native melee: "+why);}
    private void advance(int ticks){clock+=ticks;p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(clock);}
    private void cooled(int value){try{COOLED.setAccessible(true);COOLED.setInt(p,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private void place(){p.setPositionAndRotation(256.5,90,256.5,0,0);p.rotationYawHead=0;p.setSneaking(false);p.setSprinting(false);
        p.onGround=false;p.fallDistance=0;p.motionX=p.motionY=p.motionZ=0;p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);}
    private ItemStack issue(String key){RewardLedger ledger=RewardLedger.get(p);ledger.issue(p,key,1,"melee/native/"+(receipt++));
        for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&ledger.valid(p,stack))return stack;
        throw new AssertionError("Missing issued fixture "+key);}
    private void take(ItemStack stack){for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i)==stack)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private void equip(String kind){
        for(EntityZombie actor:actors)p.world.removeEntityDangerously(actor);actors.clear();
        MeleeCombos.clear(p);PlayerState.data(p).removeTag("meleeReady");advance(200);
        for(EntityEquipmentSlot slot:EntityEquipmentSlot.values())p.setItemStackToSlot(slot,ItemStack.EMPTY);
        for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
        String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<parts.length;i++){ItemStack stack=issue("ember_"+parts[i]);take(stack);p.setItemStackToSlot(slots[i],stack);}
        ItemStack weapon=issue("ember_"+kind);take(weapon);p.inventory.currentItem=0;p.inventory.mainInventory.set(0,weapon);
        p.removePotionEffect(MobEffects.RESISTANCE);p.removePotionEffect(MobEffects.SPEED);p.onUpdate();place();
        check(ArsenalAccess.issued(p,weapon)&&ArsenalAccess.fullSet(p)!=null,"Issued fixture invalid");
    }
    private EntityZombie zombie(double x,double z){EntityZombie t=new EntityZombie(p.world);t.setNoAI(true);t.setSilent(true);
        t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);t.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);
        t.setHealth(1000);t.setPositionAndRotation(256.5+x,90,256.5+z,180,0);t.rotationYawHead=180;
        check(p.world.spawnEntity(t),"Target spawn rejected");actors.add(t);return t;}
    private void contact(EntityZombie t){place();t.setPosition(256.5,90,258.5);t.hurtResistantTime=0;cooled(100);
        p.attackTargetEntityWithCurrentItem(t);MeleeCombos.tickPlayer(p);advance(24);}
    private void cleanup(){MeleeCombos.clear(p);PlayerState.data(p).removeTag("meleeReady");
        for(EntityZombie t:actors)p.world.removeEntityDangerously(t);actors.clear();p.removePotionEffect(MobEffects.RESISTANCE);p.removePotionEffect(MobEffects.SPEED);}
    private void exercise(){
        for(int x=253;x<=261;x++)for(int z=253;z<=263;z++)for(int y=89;y<=94;y++)p.world.setBlockState(new BlockPos(x,y,z),y==89?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        String[] kinds={"saber","spear","dagger","glaive","staff","axe"};
        for(String kind:kinds){equip(kind);EntityZombie t=zombie(0,2);EntityZombie extra="glaive".equals(kind)?zombie(1.5,2):null;
            PlayerState.setResolve(p,50);float extraBefore=extra==null?0:extra.getHealth();
            contact(t);check(MeleeCombos.chain(p)==1,"First native contact did not advance "+kind);
            contact(t);check(MeleeCombos.chain(p)==2,"Second native contact did not advance "+kind);
            float before=t.getHealth();contact(t);check(t.getHealth()<before&&MeleeCombos.chain(p)==0,"Third native contact failed "+kind);
            check(PlayerState.data(p).getLong("meleeReady")==clock-24+80,"Finisher deadline wrong "+kind);
            if("saber".equals(kind))check(p.isPotionActive(MobEffects.RESISTANCE),"Saber guard missing");
            if("spear".equals(kind))check(t.motionZ>0,"Spear did not displace native target");
            if("dagger".equals(kind))check(p.isPotionActive(MobEffects.SPEED),"Dagger footwork missing");
            if("glaive".equals(kind))check(extra.getHealth()<extraBefore,"Glaive cleave missing");
            if("staff".equals(kind))check(t.isPotionActive(MobEffects.WEAKNESS)&&PlayerState.resolve(p)==54,"Staff control/recovery missing");
            if("axe".equals(kind))check(t.isPotionActive(MobEffects.SLOWNESS),"Axe pressure missing");
            MeleeCombos.clear(p);contact(t);check(MeleeCombos.chain(p)==0,"Reconnect bypassed recovery "+kind);
        }
        for(int stage=1;stage<=4;stage++){
            equip("saber");EntityZombie t=zombie(0,2);contact(t);contact(t);float before=t.getHealth();gate.target=t;gate.stage=stage;
            contact(t);check(t.getHealth()==before&&MeleeCombos.chain(p)==0&&!p.isPotionActive(MobEffects.RESISTANCE),"Cancellation credited third contact at stage "+stage);
            gate.stage=0;gate.target=null;
        }
        equip("saber");EntityZombie t=zombie(0,2);cooled(0);p.attackTargetEntityWithCurrentItem(t);MeleeCombos.tickPlayer(p);
        check(MeleeCombos.chain(p)==0,"Uncooled spam advanced combo");
        advance(24);contact(t);EntityZombie other=zombie(0,2);contact(other);check(MeleeCombos.chain(p)==1,"Target swap retained progress");
        ItemStack old=p.getHeldItemMainhand();p.inventory.mainInventory.set(0,old.copy());MeleeCombos.tickPlayer(p);
        check(MeleeCombos.chain(p)==0,"Stack replacement retained chain");
        equip("saber");t=zombie(0,2);contact(t);float unchanged=t.getHealth();cooled(100);t.hurtResistantTime=0;
        p.attackTargetEntityWithCurrentItem(t);t.setHealth(unchanged);MeleeCombos.tickPlayer(p);
        check(MeleeCombos.chain(p)==0,"Damage with no final health loss earned credit");
        advance(24);contact(t);advance(24);cooled(100);t.hurtResistantTime=0;p.attackTargetEntityWithCurrentItem(t);
        p.inventory.mainInventory.set(0,p.getHeldItemMainhand().copy());MeleeCombos.tickPlayer(p);
        check(MeleeCombos.chain(p)==0,"Equipment changed during callback earned credit");
        advance(100);MeleeCombos.clear(p);t.hurtResistantTime=0;t.attackEntityFrom(DamageSource.causePlayerDamage(p),2);MeleeCombos.tickPlayer(p);
        check(MeleeCombos.chain(p)==0,"Synthetic player damage advanced combo");
        p.inventory.mainInventory.set(0,new ItemStack(Gear.ITEMS.get("ember_saber")));advance(24);contact(t);
        check(MeleeCombos.chain(p)==0,"Unissued blade advanced combo");
        equip("saber");t=zombie(0,2);t.setAbsorptionAmount(100);float health=t.getHealth();contact(t);
        check(MeleeCombos.chain(p)==1&&t.getHealth()==health&&t.getAbsorptionAmount()<100,"Real absorption-only contact was lost");
        equip("saber");t=zombie(0,2);ScorePlayerTeam team=p.world.getScoreboard().createTeam("melee_native");
        try{
            p.world.getScoreboard().addPlayerToTeam(p.getName(),team.getName());
            p.world.getScoreboard().addPlayerToTeam(t.getCachedUniqueIdString(),team.getName());
            contact(t);check(MeleeCombos.chain(p)==0,"Friendly target advanced combo");
        }finally{p.world.getScoreboard().removeTeam(team);}
    }
    public static final class Gate {
        EntityZombie target;int stage;
        @SubscribeEvent(priority=EventPriority.LOWEST)public void intent(AttackEntityEvent e){if(stage==1&&e.getTarget()==target)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void attack(LivingAttackEvent e){if(stage==2&&e.getEntityLiving()==target)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void hurt(LivingHurtEvent e){if(stage==3&&e.getEntityLiving()==target)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void damage(LivingDamageEvent e){if(stage==4&&e.getEntityLiving()==target)e.setCanceled(true);}
    }
}
