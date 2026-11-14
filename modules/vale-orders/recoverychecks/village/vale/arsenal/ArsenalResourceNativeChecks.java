package village.vale.arsenal;

import java.io.File;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.*;
import village.vale.bridge.*;

/** Actual caller acceptance in the isolated full-pack bridge fixture, never an owner world. */
public final class ArsenalResourceNativeChecks {
    private static int assertions;
    private static void check(boolean yes,String message){assertions++;if(!yes)throw new AssertionError("Shared caller: "+message);}
    private final EntityPlayerMP player;private final WorldServer world;private final Gate gate=new Gate();
    private final List<Entity> actors=new ArrayList<>();private final String receipt="native-resource/"+UUID.randomUUID()+"/";
    private final double factor;private double baseline;
    private ArsenalResourceNativeChecks(EntityPlayerMP p){player=p;world=p.getServerWorld();factor=MoveCost.external()?MoveCost.scale():1;gate.player=p;}
    public static int run(EntityPlayerMP p)throws Exception{
        if(!Boolean.getBoolean("vale.bridge.nativeFixture")||p==null||!p.getServer().isCallingFromMinecraftThread()||!"bridge-native-flat".equals(p.getServer().getFolderName())||!"ValeBridgeNative".equals(p.getName())||!new File("PRIVATE-DISPOSABLE-BRIDGE-NATIVE.json").isFile())throw new IllegalStateException("Disposable full-pack bridge fixture required");
        check(p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p,"real current synthetic player must be logged in");
        check(MoveCost.view(p).ready(),"provider admission required before callers");
        assertions=0;ArsenalResourceNativeChecks c=new ArsenalResourceNativeChecks(p);c.exercise();return assertions;
    }
    private double amount(){ChakraLink.View v=MoveCost.view(player);check(v.ready(),"resource provider remained ready");return v.amount;}
    private void full(){MoveCost.restore(player,Integer.MAX_VALUE);check(MoveCost.availableUnits(player)>=50,"native max must support actual techniques");baseline=amount();}
    private void debit(int units,String why){check(Math.abs(amount()-(baseline-units*factor))<1.0e-4,why+" exact provider debit");try(MoveCost.Ticket t=MoveCost.reserve(player,0)){check(t!=null,"caller released its resource reservation");}}
    private ItemStack issue(String key){RewardLedger.get(player).issue(player,key,1,receipt+key);for(ItemStack s:player.inventory.mainInventory)if(Gear.key(s).equals("valeorders:"+key)&&RewardLedger.get(player).valid(player,s))return s;throw new AssertionError("Issued stack missing "+key);}
    private void held(ItemStack item){for(int n=0;n<36;n++)if(player.inventory.mainInventory.get(n)==item)player.inventory.mainInventory.set(n,ItemStack.EMPTY);player.inventory.mainInventory.set(0,item);player.inventory.currentItem=0;player.resetActiveHand();}
    private void learn(String key){NBTTagCompound r=Scrolls.row(player,key);r.setBoolean("learned",true);r.setInteger("practice",60);r.setLong("next",0);}
    private EntityZombie target(float health){EntityZombie t=new EntityZombie(world);t.setNoAI(true);t.enablePersistence();t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200);t.setHealth(health);t.setPosition(player.posX,player.posY,player.posZ+3);t.motionX=t.motionY=t.motionZ=0;check(world.spawnEntity(t),"native practice target admission");actors.add(t);gate.target=t;return t;}
    private void exercise()throws Exception{
        NBTTagCompound data=player.getEntityData().copy();NBTTagList inventory=player.inventory.writeToNBT(new NBTTagList());int selected=player.inventory.currentItem;GameType mode=player.interactionManager.getGameType();Vec3d position=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch,health=player.getHealth(),absorption=player.getAbsorptionAmount();boolean ground=player.onGround;
        Set<Entity> before=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());before.addAll(world.loadedEntityList);
        MinecraftForge.EVENT_BUS.register(gate);
        try{
            player.setGameType(GameType.SURVIVAL);player.rotationYaw=0;player.rotationPitch=0;player.onGround=true;player.inventory.clear();
            NBTTagCompound account=PlayerState.data(player);account.setInteger("xp",4900);account.setString("starterOrder","storm");account.setTag("arsenal",new NBTTagCompound());account.setTag("originalFocus",new NBTTagCompound());
            String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
            for(int n=0;n<4;n++){ItemStack piece=issue("storm_"+parts[n]);for(int i=0;i<36;i++)if(player.inventory.mainInventory.get(i)==piece)player.inventory.mainInventory.set(i,ItemStack.EMPTY);player.setItemStackToSlot(slots[n],piece);}
            learn("breeze_burst");learn("spiral_palm");ItemStack spiral=issue("technique_spiral_palm");held(spiral);full();check(spiral.getItem().onItemRightClick(world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"real mastered Rasengan cast");debit(24,"Rasengan");check(Scrolls.state(player).getString("mode").equals("spiral"),"accepted gameplay mode retained");Scrolls.state(player).setString("mode","");Scrolls.state(player).setLong("modeUntil",0);
            learn("clay_warden");ItemStack summon=issue("technique_clay_warden");held(summon);full();gate.cancelSpawn=true;check(summon.getItem().onItemRightClick(world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"canceled native actor cast rejected");check(gate.spawnSeen,"native spawn cancellation reached");gate.cancelSpawn=false;debit(0,"canceled actor");
            ItemStack weapon=issue("thunder_edge");held(weapon);player.getCooldownTracker().setCooldown(weapon.getItem(),0);account.setTag("arsenal",new NBTTagCompound());EntityZombie t=target(100);full();gate.cancelHurt=true;ArsenalLogic.release(player,weapon,ArsenalSpec.THUNDER_EDGE,80);check(gate.hurtSeen,"native hurt cancellation reached");gate.cancelHurt=false;debit(0,"canceled specialist hit");check(t.getHealth()==100,"canceled hit kept native health");t.setDead();world.removeEntity(t);
            player.getCooldownTracker().setCooldown(weapon.getItem(),0);account.setTag("arsenal",new NBTTagCompound());t=target(1);full();gate.throwDeath=true;boolean threw=false;try{ArsenalLogic.release(player,weapon,ArsenalSpec.THUNDER_EDGE,80);}catch(RuntimeException expected){threw=true;}gate.throwDeath=false;check(threw&&gate.deathSeen,"accepted damage callback exception reached");debit(20,"accepted specialist hit then exception");check(ArsenalCooldowns.readyAt(ArsenalLogic.state(player),ArsenalSpec.THUNDER_EDGE)>ArsenalAccess.now(player),"accepted throw retained persistent deadline");t.setDead();world.removeEntity(t);
            OriginalFocusGear.Spec ward=null;for(OriginalFocusGear.Spec s:OriginalFocusGear.SPECS)if(s.role.equals("ward")){ward=s;break;}check(ward!=null,"ward focus exists");ItemStack focus=issue(ward.key);for(int i=0;i<36;i++)if(player.inventory.mainInventory.get(i)==focus)player.inventory.mainInventory.set(i,ItemStack.EMPTY);player.setItemStackToSlot(ward.slot,focus);NBTTagCompound f=OriginalFocusGear.state(player);f.setBoolean("active",true);f.setString("key",ward.key);f.setLong("ready",0);t=target(100);player.setHealth(player.getMaxHealth());player.hurtResistantTime=0;player.setAbsorptionAmount(10);full();gate.cancelHurt=true;gate.target=player;player.attackEntityFrom(DamageSource.causeMobDamage(t),8);gate.cancelHurt=false;FocusTransactions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));debit(0,"late-canceled ward");check(player.getAbsorptionAmount()==10,"canceled ward did not consume absorption");
            f.setLong("ready",0);player.hurtResistantTime=0;float hp=player.getHealth();full();player.attackEntityFrom(DamageSource.causeMobDamage(t),8);check(player.getHealth()==hp&&player.getAbsorptionAmount()<10,"real native fully absorbed ward contact");FocusTransactions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));debit(5,"fully absorbed accepted ward");
            Item pill=Gear.ITEMS.get("kitchen_resolve_pill_1");check(pill!=null,"registered recovery pill");ItemStack potion=new ItemStack(pill);held(potion);full();if(MoveCost.external())PlayerState.setResolve(player,0);check(pill.onItemRightClick(world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"server full-provider pill gate ignores dormant resolve");check(potion.getCount()==1,"full-provider recovery item kept");
            try(MoveCost.Ticket spent=MoveCost.reserve(player,20)){check(spent!=null&&spent.commit(),"prepare actual recovery deficit");}account.setLong("kitchenPillReady",0);int count=potion.getCount();pill.onItemUseFinish(potion,world,player);check(potion.getCount()==count-1,"actual recovery pill consumed once");debit(5,"provider recovery pill credited15");
        }finally{
            gate.cancelSpawn=gate.cancelHurt=gate.throwDeath=false;FocusTransactions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));MinecraftForge.EVENT_BUS.unregister(gate);
            for(Entity e:new ArrayList<>(world.loadedEntityList))if(!before.contains(e)&&(actors.contains(e)||e instanceof HandTechnique||e instanceof ScrollActor||e instanceof WeaponPulse)){e.setDead();world.removeEntity(e);}
            player.inventory.readFromNBT(inventory);player.inventory.currentItem=selected;for(String key:new HashSet<>(player.getEntityData().getKeySet()))player.getEntityData().removeTag(key);for(String key:data.getKeySet())player.getEntityData().setTag(key,data.getTag(key).copy());player.setGameType(mode);player.setPositionAndRotation(position.x,position.y,position.z,yaw,pitch);player.setHealth(health);player.setAbsorptionAmount(absorption);player.onGround=ground;
        }
    }
    public static final class Gate {
        EntityPlayerMP player;EntityLivingBase target;boolean cancelSpawn,cancelHurt,throwDeath,spawnSeen,hurtSeen,deathSeen;
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void spawn(EntityJoinWorldEvent e){if(cancelSpawn&&e.getEntity() instanceof ScrollActor){spawnSeen=true;e.setCanceled(true);}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){if(cancelHurt&&e.getEntityLiving()==target){hurtSeen=true;e.setCanceled(true);}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){if(throwDeath&&e.getEntityLiving()==target){deathSeen=true;throw new IllegalStateException("intentional accepted-death callback failure");}}
    }
}
