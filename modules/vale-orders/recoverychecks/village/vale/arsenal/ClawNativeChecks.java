package village.vale.arsenal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;
import village.vale.Targets;

/** Actual native items, damage events, world occlusion and actor ticks. Private QA only. */
public final class ClawNativeChecks {
    private final EntityPlayerMP p;
    private final List<Entity> actors=new ArrayList<>();
    private int assertions,receipt;
    private ItemStack claw;
    private long clock;
    private ClawNativeChecks(EntityPlayerMP p) {this.p=p;clock=ArsenalAccess.now(p)+200;}
    public static int run(EntityPlayerMP p) {
        ClawNativeChecks checks=new ClawNativeChecks(p);
        try {checks.exercise();return checks.assertions;}
        finally {checks.cleanup();}
    }
    private void check(boolean condition,String message) {
        assertions++;if(!condition)throw new AssertionError("Crescent native: "+message);
    }
    private void at(long value) {clock=value;p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(clock);}
    private void advance(int ticks) {at(clock+ticks);}
    private void placeOwner() {
        p.setPositionAndRotation(256.5,90,256.5,0,0);p.rotationYawHead=0;
        p.setSneaking(false);p.setSprinting(false);p.fallDistance=0;p.onGround=false;
        p.motionX=p.motionY=p.motionZ=0;p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);
    }
    private void ready() {
        ClawCombat.clear(p);ArsenalLogic.state(p).removeTag("cooldowns");ArsenalLogic.state(p).setLong("ready",0);ArsenalLogic.state(p).removeTag("busy");
        for(ArsenalBlade item:Arsenal.items().values())p.getCooldownTracker().setCooldown(item,0);
        p.getCooldownTracker().tick();PlayerState.setResolve(p,100);p.resetActiveHand();placeOwner();advance(200);
    }
    private void eraseActors() {
        for(Entity actor:actors)p.world.removeEntityDangerously(actor);
        actors.clear();
    }
    private void cleanup() {ClawCombat.clear(p);eraseActors();p.resetActiveHand();p.setSneaking(false);}
    private ItemStack issue(String key) {
        RewardLedger ledger=RewardLedger.get(p);ledger.issue(p,key,1,"weapons038/native/"+(receipt++));
        for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&ledger.valid(p,stack))return stack;
        throw new AssertionError("Cannot issue native fixture item "+key);
    }
    private void take(ItemStack stack) {
        for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i)==stack)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
    }
    private void hold(ItemStack stack) {take(stack);p.inventory.currentItem=0;p.inventory.mainInventory.set(0,stack);p.inventory.setItemStack(ItemStack.EMPTY);}
    private ItemStack equip(ArsenalSpec spec) {
        for(EntityEquipmentSlot slot:EntityEquipmentSlot.values())p.setItemStackToSlot(slot,ItemStack.EMPTY);
        for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
        String[] parts={"helmet","chestplate","leggings","boots"};
        EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<slots.length;i++) {ItemStack armor=issue(spec.order.key+"_"+parts[i]);take(armor);p.setItemStackToSlot(slots[i],armor);}
        ItemStack weapon=issue(spec.key);hold(weapon);
        check(ArsenalLogic.proof(p,weapon,spec),"issued armor/weapon fixture is invalid for "+spec.key);
        return weapon;
    }
    private EntityZombie zombie(double x,double z) {
        EntityZombie target=new EntityZombie(p.world);target.setNoAI(true);target.setSilent(true);
        target.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        target.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);
        target.setHealth(1000);target.setPositionAndRotation(256.5+x,90,256.5+z,0,0);target.rotationYawHead=0;
        target.hurtResistantTime=0;
        check(p.world.spawnEntity(target),"test target was not spawned");actors.add(target);return target;
    }
    private EnumActionResult use() {return p.interactionManager.processRightClick(p,p.world,p.getHeldItemMainhand(),EnumHand.MAIN_HAND);}
    private void lockAndStart() {
        check(use()==EnumActionResult.SUCCESS&&ClawCombat.locked(p),"native right-click failed to acquire lock");
        check(use()==EnumActionResult.SUCCESS,"native cooldown swallowed the second right-click");
    }
    private void melee(EntityZombie target) {
        target.hurtResistantTime=0;p.resetCooldown();p.fallDistance=0;p.onGround=false;
        p.attackTargetEntityWithCurrentItem(target);
    }
    private void exercise() {
        // Disposable fixture arena: all motion and occlusion happen in actual loaded chunks.
        for(int x=253;x<=263;x++)for(int z=253;z<=266;z++)for(int y=89;y<=94;y++) {
            BlockPos pos=new BlockPos(x,y,z);p.world.getChunk(pos);
            p.world.setBlockState(pos,y==89?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        at(clock);claw=equip(ArsenalSpec.CRESCENT_TALONS);ready();
        EntityZombie target=zombie(0,4);float before=target.getHealth();long lockedAt=clock;
        check(use()==EnumActionResult.SUCCESS,"native lock failed");
        check(target.isPotionActive(MobEffects.BLINDNESS)&&target.getActivePotionEffect(MobEffects.BLINDNESS).getDuration()==100,"rear lock did not immediately blind for five seconds");
        check(target.getHealth()==before&&PlayerState.resolve(p)==82,"lock damaged its target or charged wrong resolve");
        check(ArsenalCooldowns.readyAt(ArsenalLogic.state(p),ArsenalSpec.CRESCENT_TALONS)==lockedAt+100,"lock recovery is not persistent");
        check(use()==EnumActionResult.SUCCESS,"second native use could not start lunge");
        advance(19);ClawCombat.tick(p);check(target.getHealth()==before,"lunge bypassed the one-second dodge window");
        advance(1);ClawCombat.tick(p);
        check(Math.abs((before-target.getHealth())-8.8f)<.001f,"rear lunge did not deal exactly eight plus ten percent");
        check(!ClawCombat.locked(p)&&ClawCombat.hits(p,target.getUniqueID())==1,"successful lunge was not consumed/countable exactly once");
        check(p.posZ>256.5&&p.posZ<=260&&PlayerState.resolve(p)==82,"lunge movement or cost exceeded its bound");

        eraseActors();ready();target=zombie(0,4);before=target.getHealth();lockAndStart();
        target.setPosition(258.5,90,260.5);advance(20);ClawCombat.tick(p);
        check(target.getHealth()==before&&p.posZ==256.5&&!ClawCombat.locked(p),"moving out of the marked strike did not dodge");

        eraseActors();ready();target=zombie(0,4);lockAndStart();
        p.inventory.mainInventory.set(1,claw);p.inventory.mainInventory.set(0,ItemStack.EMPTY);advance(1);ClawCombat.tick(p);
        hold(claw);advance(19);ClawCombat.tick(p);
        check(target.getHealth()==1000&&!ClawCombat.locked(p),"stowed lunge survived a weapon swap");

        eraseActors();ready();target=zombie(0,4);check(use()==EnumActionResult.SUCCESS,"expiry setup lock failed");
        advance(100);ClawCombat.tick(p);
        check(!ClawCombat.locked(p)&&target.getHealth()==1000,"uncommitted lock survived five seconds");
        eraseActors();ready();target=zombie(0,4);check(use()==EnumActionResult.SUCCESS,"late-start setup failed");
        advance(99);check(use()==EnumActionResult.SUCCESS,"lunge could not start during the last valid tick");
        advance(19);ClawCombat.tick(p);check(target.getHealth()==1000,"late lunge landed before its dodge window");
        advance(1);ClawCombat.tick(p);check(target.getHealth()<1000,"valid late lunge was canceled at the acquisition deadline");

        rejectedLocks();canceledDamage();bleedAndRearMelee();projectiles();
        System.out.println("VALE_CLAW038_NATIVE_PASS: "+assertions+" assertions (locks, windup, dodge, protection, rear melee, bleed, swept projectiles and ownership)");
    }
    private void rejectedLocks() {
        eraseActors();ready();EntityZombie target=zombie(0,5.01);
        check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100&&!target.isPotionActive(MobEffects.BLINDNESS),"out-of-range lock charged or blinded");
        target.setPosition(256.5,90,260.5);
        BlockPos wall=new BlockPos(256,91,258);p.world.setBlockState(wall,Blocks.STONE.getDefaultState(),2);
        try {check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100&&!ClawCombat.locked(p),"lock crossed a solid wall");}
        finally {p.world.setBlockState(wall,Blocks.AIR.getDefaultState(),2);}
        target.setEntityInvulnerable(true);
        check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100,"invulnerable target accepted a lock");target.setEntityInvulnerable(false);
        target.getEntityData().setInteger(Targets.KEY,-1);
        check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100,"friendly/custom protected target accepted a lock");target.getEntityData().setInteger(Targets.KEY,0);
        String oldTeam=p.getTeam()==null?null:p.getTeam().getName();
        ScorePlayerTeam team=p.world.getScoreboard().createTeam("claw038");team.setAllowFriendlyFire(false);
        p.world.getScoreboard().addPlayerToTeam(p.getName(),team.getName());p.world.getScoreboard().addPlayerToTeam(target.getCachedUniqueIdString(),team.getName());
        try {check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100,"party protection allowed a lock");}
        finally {
            p.world.getScoreboard().removeTeam(team);
            if(oldTeam!=null)p.world.getScoreboard().addPlayerToTeam(p.getName(),oldTeam);
        }
        ItemStack helmet=p.getItemStackFromSlot(EntityEquipmentSlot.HEAD);p.setItemStackToSlot(EntityEquipmentSlot.HEAD,ItemStack.EMPTY);
        check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100,"missing issued armor accepted a lock");p.setItemStackToSlot(EntityEquipmentSlot.HEAD,helmet);
        hold(new ItemStack(Arsenal.item(ArsenalSpec.CRESCENT_TALONS)));
        check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100,"unissued talons accepted a lock");hold(claw);
        Gate gate=new Gate(target);gate.cancelUse=true;MinecraftForge.EVENT_BUS.register(gate);
        try {
            check(!ClawNetwork.useFromPacket(p)&&gate.useSeen&&PlayerState.resolve(p)==100&&!ClawCombat.locked(p),"middle-click routing bypassed native RightClickItem cancellation");
            gate.cancelUse=false;gate.cancelLock=true;
            check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100&&!target.isPotionActive(MobEffects.BLINDNESS),"canceled lock spent resolve or blinded");
            gate.cancelLock=false;gate.denyBlind=true;
            check(use()!=EnumActionResult.SUCCESS&&PlayerState.resolve(p)==100&&!ClawCombat.locked(p),"denied blindness still charged a rear lock");
            gate.denyBlind=false;target.addPotionEffect(new net.minecraft.potion.PotionEffect(MobEffects.BLINDNESS,200,0));
            gate.denyBlind=true;gate.potionSeen=0;
            check(use()!=EnumActionResult.SUCCESS&&gate.potionSeen==1&&PlayerState.resolve(p)==100&&!ClawCombat.locked(p)
                    &&target.getActivePotionEffect(MobEffects.BLINDNESS).getDuration()==200,"existing blindness hid DENY or was modified by a failed lock");
            gate.denyBlind=false;target.removePotionEffect(MobEffects.BLINDNESS);gate.potionSeen=0;
            check(use()==EnumActionResult.SUCCESS&&gate.potionSeen==1&&ClawCombat.locked(p)&&PlayerState.resolve(p)==82,"one rear lock did not produce exactly one native applicability callback");
        } finally {MinecraftForge.EVENT_BUS.unregister(gate);}
    }
    private void canceledDamage() {
        eraseActors();ready();EntityZombie target=zombie(0,4);lockAndStart();target.setAbsorptionAmount(1);
        Gate gate=new Gate(target);gate.cancelDamage=true;MinecraftForge.EVENT_BUS.register(gate);
        try {
            advance(20);ClawCombat.tick(p);
            check(gate.damageSeen&&target.getHealth()==1000&&ClawCombat.hits(p,target.getUniqueID())==0,"canceled lunge granted on-hit effects from consumed absorption");
        } finally {MinecraftForge.EVENT_BUS.unregister(gate);}
        ready();target.setPosition(256.5,90,258.5);target.setAbsorptionAmount(.05f);gate=new Gate(target);gate.cancelDamage=true;MinecraftForge.EVENT_BUS.register(gate);
        try {
            melee(target);
            check(gate.damageSeen&&target.getHealth()==1000&&ClawCombat.hits(p,target.getUniqueID())==0,"native melee counted a canceled final damage event");
        } finally {MinecraftForge.EVENT_BUS.unregister(gate);}
        target.setAbsorptionAmount(0);gate=new Gate(target);gate.cancelAttack=true;MinecraftForge.EVENT_BUS.register(gate);
        try {melee(target);check(target.getHealth()==1000&&ClawCombat.hits(p,target.getUniqueID())==0,"canceled attack counted as a landed hit");}
        finally {MinecraftForge.EVENT_BUS.unregister(gate);}
        gate=new Gate(target);gate.cancelHurt=true;MinecraftForge.EVENT_BUS.register(gate);
        try {melee(target);check(target.getHealth()==1000&&ClawCombat.hits(p,target.getUniqueID())==0,"canceled hurt counted as a landed hit");}
        finally {MinecraftForge.EVENT_BUS.unregister(gate);}
    }
    private void bleedAndRearMelee() {
        eraseActors();ready();EntityZombie target=zombie(0,2);Gate gate=new Gate(target);MinecraftForge.EVENT_BUS.register(gate);
        double base=p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue();
        p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(120);
        try {
            target.rotationYaw=target.rotationYawHead=180;melee(target);float front=gate.amount;
            target.rotationYaw=target.rotationYawHead=0;melee(target);
            check(front>16&&Math.abs(gate.amount-front*1.1f)<.001f,"ordinary rear melee does not receive exactly ten percent above the spell cap");
        } finally {p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(base);MinecraftForge.EVENT_BUS.unregister(gate);}
        ClawCombat.clear(p);
        for(int i=0;i<4;i++)melee(target);
        check(ClawCombat.stacks(p,target.getUniqueID())==0&&ClawCombat.hits(p,target.getUniqueID())==4,"bleed activated before five successful hits");
        melee(target);check(ClawCombat.stacks(p,target.getUniqueID())==1&&ClawCombat.hits(p,target.getUniqueID())==0,"fifth hit did not create the first bleed stack");
        for(int i=0;i<10;i++)melee(target);
        check(ClawCombat.stacks(p,target.getUniqueID())==3,"successive groups of five did not accumulate bleed");
        for(int i=0;i<5;i++)melee(target);
        check(ClawCombat.stacks(p,target.getUniqueID())==3,"bleed exceeded three stacks");
        float before=target.getHealth();advance(19);ClawCombat.tick(p);check(target.getHealth()==before,"bleed pulsed early");
        advance(1);target.hurtResistantTime=0;ClawCombat.tick(p);
        check(Math.abs(before-target.getHealth()-3)<.001f&&ClawCombat.hits(p,target.getUniqueID())==0&&PlayerState.resolve(p)==100,"bleed damage/counting/resource rules are wrong");
        Gate cancel=new Gate(target);cancel.cancelDamage=true;MinecraftForge.EVENT_BUS.register(cancel);
        try {
            before=target.getHealth();advance(20);target.hurtResistantTime=0;ClawCombat.tick(p);
            check(target.getHealth()==before&&ClawCombat.stacks(p,target.getUniqueID())==0,"protected bleed kept dealing damage or retained its stack");
        } finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
        for(int i=0;i<5;i++)melee(target);before=target.getHealth();
        for(int i=0;i<17;i++) {advance(20);target.hurtResistantTime=0;ClawCombat.tick(p);}
        check(Math.abs(before-target.getHealth()-17)<.01f,"seventeen-second bleed did not produce seventeen bounded pulses");
        before=target.getHealth();advance(1);ClawCombat.tick(p);advance(20);target.hurtResistantTime=0;ClawCombat.tick(p);
        check(target.getHealth()==before&&ClawCombat.stacks(p,target.getUniqueID())==0,"bleed continued after seventeen seconds");
        for(int i=0;i<5;i++)melee(target);
        p.inventory.mainInventory.set(1,claw);p.inventory.mainInventory.set(0,ItemStack.EMPTY);before=target.getHealth();advance(20);target.hurtResistantTime=0;ClawCombat.tick(p);hold(claw);
        check(target.getHealth()==before&&ClawCombat.stacks(p,target.getUniqueID())==0,"bleed survived stowing its initiating weapon");
    }
    private ArsenalShot shot(ItemStack weapon,ArsenalSpec spec,int mode) {
        ArsenalShot shot=new ArsenalShot(p,weapon,spec,mode,1,0,new Vec3d(p.posX,p.posY+.9,p.posZ+.4));
        shot.motionX=shot.motionY=0;shot.motionZ=1.8;check(p.world.spawnEntity(shot),"test projectile was not spawned");actors.add(shot);return shot;
    }
    private void projectiles() {
        eraseActors();ready();ItemStack spear=equip(ArsenalSpec.BINDER_SPEAR);
        EntityZombie a=zombie(0,.75),b=zombie(0,1.35),c=zombie(0,1.95);
        ArsenalShot shot=shot(spear,ArsenalSpec.BINDER_SPEAR,ArsenalShot.BIND);shot.onUpdate();
        check(a.getHealth()<1000&&b.getHealth()<1000&&c.getHealth()<1000&&shot.isDead,"piercing did not process all three swept contacts in one step");
        eraseActors();ready();a=zombie(0,.75);b=zombie(0,1.35);c=zombie(0,1.95);b.getEntityData().setInteger(Targets.KEY,-1);
        shot=shot(spear,ArsenalSpec.BINDER_SPEAR,ArsenalShot.BIND);shot.onUpdate();
        check(a.getHealth()<1000&&b.getHealth()==1000&&c.getHealth()==1000&&shot.isDead,"piercing passed through a protected body");
        eraseActors();ready();a=zombie(0,1);
        shot=shot(spear,ArsenalSpec.BINDER_SPEAR,ArsenalShot.BIND);p.inventory.mainInventory.set(1,spear);p.inventory.mainInventory.set(0,ItemStack.EMPTY);shot.onUpdate();
        check(shot.isDead&&a.getHealth()==1000,"stowed projectile still hit a target");hold(spear);
        eraseActors();ready();ItemStack sickle=equip(ArsenalSpec.RETURNING_SICKLE);a=zombie(0,3.3);
        shot=shot(sickle,ArsenalSpec.RETURNING_SICKLE,ArsenalShot.RETURN);shot.onUpdate();shot.onUpdate();float outbound=a.getHealth();
        check(outbound<1000&&!shot.isDead,"returning sickle did not hit and recall");
        b=zombie(0,1.6);shot.onUpdate();
        check(b.getHealth()<1000&&a.getHealth()==outbound&&sickle.getCount()==1,"return flight failed distinct contact or duplicated its weapon");
        eraseActors();ready();ItemStack spool=equip(ArsenalSpec.NEEDLE_SPOOL);
        ArsenalField field=new ArsenalField(p,spool,ArsenalSpec.NEEDLE_SPOOL,p.getPositionVector().add(0,.8,0),ArsenalField.EMITTER,1);
        check(p.world.spawnEntity(field),"test emitter was not spawned");actors.add(field);
        p.inventory.mainInventory.set(1,spool);p.inventory.mainInventory.set(0,ItemStack.EMPTY);field.onUpdate();
        check(field.isDead&&ArsenalLogic.shotCount(p)==0,"stowed emitter spawned delayed projectiles");hold(spool);
    }
    public static final class Gate {
        final EntityLivingBase target;boolean cancelUse,useSeen,cancelLock,denyBlind,cancelAttack,cancelHurt,cancelDamage,damageSeen;float amount;int potionSeen;
        Gate(EntityLivingBase target) {this.target=target;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void use(PlayerInteractEvent.RightClickItem event) {
            if(event.getWorld()==target.world) {useSeen=true;if(cancelUse) {event.setCanceled(true);event.setCancellationResult(EnumActionResult.FAIL);}}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void lock(ClawLockEvent event) {if(event.getTarget()==target&&cancelLock)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void potion(PotionEvent.PotionApplicableEvent event) {
            if(event.getEntityLiving()==target&&event.getPotionEffect().getPotion()==MobEffects.BLINDNESS) {
                potionSeen++;if(denyBlind)event.setResult(Event.Result.DENY);
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void attack(LivingAttackEvent event) {if(event.getEntityLiving()==target&&cancelAttack)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent event) {
            if(event.getEntityLiving()==target) {amount=event.getAmount();if(cancelHurt)event.setCanceled(true);}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent event) {
            if(event.getEntityLiving()==target) {damageSeen=true;if(cancelDamage)event.setCanceled(true);}
        }
    }
}
