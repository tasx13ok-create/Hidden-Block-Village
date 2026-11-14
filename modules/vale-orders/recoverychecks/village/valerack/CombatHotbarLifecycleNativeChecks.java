package village.valerack;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.stats.StatBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.eventhandler.*;

/** Future Core 0.3.14 combat-UI QA only. Never included in a production artifact. */
public final class CombatHotbarLifecycleNativeChecks {
    private final MinecraftServer server;
    private final WorldServer world;
    private final EntityPlayerMP context;
    private final String prefix="CBL0314:"+UUID.randomUUID()+":";
    private final String namePrefix="CBL"+UUID.randomUUID().toString().substring(0,6);
    private final List<Probe> players=new ArrayList<>();
    private final List<EmbeddedChannel> channels=new ArrayList<>();
    private final Events events=new Events();
    private final String oldKeep,oldMessages;
    private int checks,serial;

    private CombatHotbarLifecycleNativeChecks(EntityPlayerMP context){
        this.context=context;server=context.getServer();world=context.getServerWorld();
        oldKeep=world.getGameRules().getString("keepInventory");
        oldMessages=world.getGameRules().getString("showDeathMessages");
    }
    public static int run(EntityPlayerMP context){
        if(context==null||context.getServer()==null||!context.getServer().isCallingFromMinecraftThread()
            ||!Boolean.getBoolean("valeorders.recoverycheck")
            ||!"vale-recovery-test".equals(context.getServer().getFolderName())
            ||!new File(".vale-recovery-test").isFile())
            throw new IllegalStateException("Future 0314 combat lifecycle QA requires the explicit disposable recovery fixture");
        CombatHotbarLifecycleNativeChecks c=new CombatHotbarLifecycleNativeChecks(context);
        // Registered after RackLifecycle: the cancellation below is deliberately later LOWEST.
        MinecraftForge.EVENT_BUS.register(c.events);
        try{
            c.world.getGameRules().setOrCreateGameRule("showDeathMessages","false");
            c.cancelledDeath();c.keptDeathClone();c.droppedDeathClone();c.endClone();
            System.out.println("COMBAT_HOTBAR_LIFECYCLE0314_NATIVE_PASS: "+c.checks);
            return c.checks;
        }finally{MinecraftForge.EVENT_BUS.unregister(c.events);c.close();}
    }
    private void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError("Combat lifecycle0314: "+text);}
    private static byte[] encoded(NBTTagCompound tag){
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);CompressedStreamTools.write(tag,out);out.flush();return bytes.toByteArray();}
        catch(IOException e){throw new AssertionError("Cannot encode native fixture inventory",e);}
    }
    private static NBTTagCompound inventory(EntityPlayerMP p){NBTTagCompound tag=new NBTTagCompound();tag.setTag("Inventory",p.inventory.writeToNBT(new NBTTagList()));tag.setInteger("Selected",p.inventory.currentItem);return tag;}
    private Probe player(GameProfile profile){
        Probe p=new Probe(server,world,profile==null?new GameProfile(UUID.randomUUID(),namePrefix+(++serial)):profile);
        players.add(p);NetworkManager network=new NetworkManager(EnumPacketDirection.SERVERBOUND);
        channels.add(new EmbeddedChannel(network));
        p.connection=new NetHandlerPlayServer(server,network,p){@Override public void sendPacket(Packet<?> packet){}};
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setPosition(context.posX,context.posY,context.posZ);p.setHealth(p.getMaxHealth());
        p.openContainer=p.inventoryContainer;world.getChunk(p.getPosition());
        check(p.hasCapability(RackData.CAP,null),"synthetic player lacks real rack capability");
        return p;
    }
    private Layout layout(){
        Probe p=player(null);RackData rack=RackData.of(p);Layout result=new Layout(p);
        p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);rack.detach();
        for(int i=0;i<9;i++){
            ItemStack stack=new ItemStack(Items.APPLE,i+1);stack.setStackDisplayName(prefix+p.getName()+":normal:"+i);
            p.inventory.mainInventory.set(i,stack);result.normal.add(stack.writeToNBT(new NBTTagCompound()));
        }
        for(int i=0;i<6;i++){
            ItemStack stack=new ItemStack(Items.IRON_SWORD);stack.setItemDamage(i+11);stack.setStackDisplayName(prefix+p.getName()+":combat:"+i);
            rack.raw(i,stack);result.combat.add(stack.writeToNBT(new NBTTagCompound()));
        }
        p.inventory.currentItem=8;
        check(rack.combat.toggle(p,rack).isEmpty()&&rack.combat.active,"native physical combat-bank activation failed");
        active(result,p);return result;
    }
    private void active(Layout layout,EntityPlayerMP p){
        RackData rack=RackData.of(p);check(!rack.locked&&rack.combat.active,"active bank did not survive native lifecycle");
        for(int i=0;i<6;i++)check(layout.combat.get(i).equals(p.inventory.mainInventory.get(i).writeToNBT(new NBTTagCompound()))&&rack.getStackInSlot(i).isEmpty(),"active combat stack changed/duplicated "+i);
        for(int i=0;i<9;i++)check(layout.normal.get(i).equals(rack.combat.parked(i).writeToNBT(new NBTTagCompound())),"parked normal stack changed/lost "+i);
        for(int i=6;i<9;i++)check(p.inventory.mainInventory.get(i).isEmpty(),"inactive native cells unexpectedly populated "+i);
    }
    private void restored(Layout layout,EntityPlayerMP p){
        RackData rack=RackData.of(p);check(rack.combat.toggle(p,rack).isEmpty()&&!rack.combat.active,"cloned normal bank could not restore");
        check(p.inventory.currentItem==8,"cloned normal selection was lost");
        for(int i=0;i<9;i++)check(layout.normal.get(i).equals(p.inventory.mainInventory.get(i).writeToNBT(new NBTTagCompound()))&&rack.combat.parked(i).isEmpty(),"restored normal stack changed/duplicated "+i);
        for(int i=0;i<6;i++)check(layout.combat.get(i).equals(rack.getStackInSlot(i).writeToNBT(new NBTTagCompound())),"combat bank lost after clone restoration "+i);
    }
    private void detached(EntityPlayerMP p){
        RackData rack=RackData.of(p);check(rack.isEmpty()&&!rack.combat.active,"old player retains live bank ownership after native clone");
        for(int i=0;i<9;i++)check(rack.combat.parked(i).isEmpty(),"old player retains parked stack after clone "+i);
    }
    private void cancelledDeath(){
        world.getGameRules().setOrCreateGameRule("keepInventory","false");Layout l=layout();Probe p=l.player;
        byte[] inv=encoded(inventory(p)),rack=encoded(RackData.of(p).serializeNBT());int spawned=events.spawned.size();
        events.watch=p;events.cancelDeath=p;events.dropEvents=0;events.sawEarlierRackDeath=false;
        p.setHealth(0);p.onDeath(DamageSource.OUT_OF_WORLD);
        check(events.sawEarlierRackDeath,"fixture did not cancel after the real RackLifecycle LOWEST observer");
        check(p.getHealth()==7&&!p.isDead,"later native death cancellation did not restore player health");
        check(events.dropEvents==0&&events.spawned.size()==spawned,"final canceled death reached native drop/spawn path");
        check(Arrays.equals(inv,encoded(inventory(p)))&&Arrays.equals(rack,encoded(RackData.of(p).serializeNBT())),"final canceled native death changed serialized active inventory/parked bank bytes");
        active(l,p);events.cancelDeath=null;
    }
    private void keptDeathClone(){
        world.getGameRules().setOrCreateGameRule("keepInventory","true");Layout l=layout();Probe old=l.player;
        events.watch=old;events.dropEvents=0;int spawned=events.spawned.size();
        old.setHealth(0);old.onDeath(DamageSource.OUT_OF_WORLD);
        check(events.dropEvents==0&&events.spawned.size()==spawned,"keepInventory native death dropped a bank");
        Probe next=player(old.getGameProfile());next.copyFrom(old,false);
        active(l,next);detached(old);restored(l,next);
        check(events.spawned.size()==spawned,"keepInventory native copyFrom spawned an extra stack");
    }
    private void droppedDeathClone(){
        world.getGameRules().setOrCreateGameRule("keepInventory","false");Layout l=layout();Probe old=l.player;
        events.watch=old;events.dropEvents=0;events.lastDrops.clear();int before=events.spawned.size();
        old.setHealth(0);old.onDeath(DamageSource.OUT_OF_WORLD);
        check(events.dropEvents==1,"native death did not issue exactly one PlayerDropsEvent");
        check(events.lastDrops.size()==15&&events.spawned.size()-before==15,"native death did not actually spawn the active six plus parked nine exactly once");
        Map<String,NBTTagCompound> expected=new HashMap<>();
        for(NBTTagCompound stack:l.normal)expected.put(new ItemStack(stack).getDisplayName(),stack);
        for(NBTTagCompound stack:l.combat)expected.put(new ItemStack(stack).getDisplayName(),stack);
        Set<UUID> identities=new HashSet<>();
        for(EntityItem item:events.lastDrops){
            NBTTagCompound match=expected.remove(item.getItem().getDisplayName());
            check(match!=null&&match.equals(item.getItem().writeToNBT(new NBTTagCompound())),"native drop lost count/damage/NBT or duplicated a bank item");
            check(identities.add(item.getUniqueID())&&!item.isDead&&world.loadedEntityList.contains(item),"drop was not a unique actually spawned native entity");
        }
        check(expected.isEmpty()&&old.inventory.isEmpty(),"native death retained inventory or omitted a bank item");
        Probe next=player(old.getGameProfile());next.copyFrom(old,false);
        check(next.inventory.isEmpty()&&RackData.of(next).isEmpty()&&!RackData.of(next).combat.active,"no-keep native clone retained a live bank");
        detached(old);detached(next);
        check(events.spawned.size()-before==15,"clone fallback duplicated already emitted native drops");
        Probe replay=player(old.getGameProfile());replay.copyFrom(old,false);
        check(replay.inventory.isEmpty()&&RackData.of(replay).isEmpty()&&events.spawned.size()-before==15,"replayed actual copyFrom duplicated handled death drops");
    }
    private void endClone(){
        world.getGameRules().setOrCreateGameRule("keepInventory","false");Layout l=layout();Probe old=l.player;
        int before=events.spawned.size();Probe next=player(old.getGameProfile());next.copyFrom(old,true);
        active(l,next);detached(old);restored(l,next);
        check(events.spawned.size()==before,"non-death End copyFrom dropped a parked bank");
    }
    private void close(){
        world.getGameRules().setOrCreateGameRule("keepInventory",oldKeep);
        world.getGameRules().setOrCreateGameRule("showDeathMessages",oldMessages);
        for(EntityItem item:new ArrayList<>(events.spawned))if(item.world==world)world.removeEntityDangerously(item);
        // Include an item if a callback caused spawn bookkeeping to be interrupted.
        for(Entity entity:new ArrayList<>(world.loadedEntityList))if(entity instanceof EntityItem&&((EntityItem)entity).getItem().getDisplayName().startsWith(prefix))world.removeEntityDangerously(entity);
        for(Probe p:players){
            RackSessions.Session session=RackSessions.SESSIONS.get(p.getUniqueID());if(session!=null&&session.owner==p)RackSessions.SESSIONS.remove(p.getUniqueID(),session);
            world.playerEntities.remove(p);if(world.loadedEntityList.contains(p))world.removeEntityDangerously(p);
            world.getScoreboard().removeObjectiveFromEntity(p.getName(),null);p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);RackData.of(p).detach();
        }
        for(EmbeddedChannel channel:channels)channel.finishAndReleaseAll();
    }
    private static final class Layout {
        final Probe player;final List<NBTTagCompound> normal=new ArrayList<>(),combat=new ArrayList<>();
        Layout(Probe player){this.player=player;}
    }
    /** Do not write statistics or send real network traffic for synthetic profiles. */
    private static final class Probe extends EntityPlayerMP {
        Probe(MinecraftServer server,WorldServer world,GameProfile profile){super(server,world,profile,new PlayerInteractionManager(world));}
        @Override public void addStat(StatBase stat,int amount){}
        @Override public void takeStat(StatBase stat){}
    }
    public final class Events {
        EntityPlayerMP watch,cancelDeath;boolean sawEarlierRackDeath;int dropEvents;
        final List<EntityItem> lastDrops=new ArrayList<>(),spawned=new ArrayList<>();
        @SubscribeEvent(priority=EventPriority.LOWEST)public void death(LivingDeathEvent event){
            if(event.getEntityLiving()==cancelDeath){sawEarlierRackDeath=RackData.of(cancelDeath).deathSeen;cancelDeath.setHealth(7);event.setCanceled(true);}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void drops(PlayerDropsEvent event){
            if(event.getEntityPlayer()==watch){dropEvents++;lastDrops.clear();lastDrops.addAll(event.getDrops());}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void join(EntityJoinWorldEvent event){
            if(event.getWorld()==world&&event.getEntity() instanceof EntityItem){EntityItem item=(EntityItem)event.getEntity();if(item.getItem().getDisplayName().startsWith(prefix))spawned.add(item);}
        }
    }
}
