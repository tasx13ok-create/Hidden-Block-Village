package village.valerack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class RackLifecycle{
    @SubscribeEvent public void attach(AttachCapabilitiesEvent<Entity> event){if(event.getObject() instanceof EntityPlayer)event.addCapability(new ResourceLocation(RackMod.ID,"storage"),new RackData.Provider((EntityPlayer)event.getObject()));}
    @SubscribeEvent public void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event){if(event.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)event.player;RackSessions.reset(p);RackSessions.sync(p);}}
    @SubscribeEvent public void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event){RackSessions.Session s=RackSessions.SESSIONS.get(event.player.getUniqueID());if(s!=null&&s.owner==event.player)RackSessions.SESSIONS.remove(event.player.getUniqueID(),s);}
    @SubscribeEvent public void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event){if(event.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)event.player;RackSessions.reset(p);RackSessions.sync(p);}}
    @SubscribeEvent public void respawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event){if(event.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)event.player;RackSessions.reset(p);RackSessions.sync(p);}}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&!event.player.world.isRemote&&event.player instanceof EntityPlayerMP){
        EntityPlayerMP p=(EntityPlayerMP)event.player;RackData data=RackData.of(p);if(p.isEntityAlive()&&!p.isDead){data.deathSeen=false;data.deathHandled=false;}
        RackSessions.Session s=RackSessions.current(p);if(s!=null&&(data.dirty||RackSessions.now(p)>=s.nextSync))RackSessions.sync(p);
    }}
    @SubscribeEvent(priority=EventPriority.LOWEST)public void death(LivingDeathEvent event){if(event.getEntityLiving() instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)event.getEntityLiving();RackData d=RackData.of(p);d.deathSeen=true;d.deathKeep=p.world.getGameRules().getBoolean("keepInventory")||p.isSpectator();}}
    public static void consumeDeath(EntityPlayer p,java.util.Collection<EntityItem> drops){
        RackData d=RackData.of(p);if(d.deathHandled||d.locked)return;d.deathHandled=true;
        for(int i=0;i<6;i++){ItemStack stack=d.getStackInSlot(i);if(stack.isEmpty())continue;d.raw(i,ItemStack.EMPTY);if(!EnchantmentHelper.hasVanishingCurse(stack)){EntityItem item=new EntityItem(p.world,p.posX,p.posY,p.posZ,stack.copy());item.setDefaultPickupDelay();drops.add(item);}}
        d.markDirty();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void drops(PlayerDropsEvent event){EntityPlayer p=event.getEntityPlayer();if(!p.world.isRemote){RackData d=RackData.of(p);boolean keep=d.deathSeen?d.deathKeep:p.world.getGameRules().getBoolean("keepInventory")||p.isSpectator();if(!keep)consumeDeath(p,event.getDrops());}}
    @SubscribeEvent public void clone(PlayerEvent.Clone event){
        if(event.getEntityPlayer().world.isRemote)return;EntityPlayer old=event.getOriginal();RackData source=RackData.of(old),target=RackData.of(event.getEntityPlayer());
        boolean keep=source.deathSeen?source.deathKeep:old.world.getGameRules().getBoolean("keepInventory")||old.isSpectator();
        if(event.isWasDeath()&&!keep&&!source.deathHandled&&!source.locked){java.util.List<EntityItem> drops=new java.util.ArrayList<>();consumeDeath(old,drops);for(EntityItem item:drops)old.world.spawnEntity(item);}
        target.deserializeNBT(source.serializeNBT());source.detach();target.deathHandled=false;target.deathSeen=false;
        RackSessions.SESSIONS.remove(old.getUniqueID());
    }
}
