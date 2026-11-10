package village.vale.arsenal;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Explicitly guarded private native tests for original companion interactions. */
public final class ScrollNativeChecks038 {
    private static int assertions;
    private static void check(boolean condition,String why){assertions++;if(!condition)throw new AssertionError(why);}
    private static void clear(EntityPlayerMP p){p.inventory.setItemStack(ItemStack.EMPTY);for(int i=0;i<p.inventory.getSizeInventory();i++)p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);p.inventory.currentItem=0;}
    private static ItemStack issue(EntityPlayerMP p,String key,String suffix){
        RewardLedger.get(p).issue(p,key,1,"native038-scroll/"+p.getUniqueID()+"/"+suffix);
        for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Gear.ITEMS.get(key))return stack;
        throw new AssertionError("Missing issued fixture item: "+key);
    }
    private static EnumActionResult use(EntityPlayerMP p,ItemStack item,BlockPos ground){return item.getItem().onItemUse(p,p.world,ground,EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f);}
    private static void tick(ScrollActor actor,int tick){actor.ticksExisted=tick;actor.onLivingUpdate();}
    private static void cycle(EntityPlayerMP p,ItemStack item,String expected){
        p.setSneaking(true);check(item.getItem().onItemRightClick(p.world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Native order cycle failed");p.setSneaking(false);
        ScrollActor actor=ScrollActor.find(p,ScrollSupport.companion(p,item).getUniqueId("actor"));
        check(actor!=null&&actor.order().key.equals(expected),"Native order cycle selected the wrong order: "+expected);
    }
    public static int run(EntityPlayerMP p){
        MinecraftServer server=p.getServer();
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(server.getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Scroll native checks require the isolated recovery fixture");
        assertions=0;WorldServer world=p.getServerWorld();List<Entity> fixtures=new ArrayList<>();List<EntityPlayerMP> allies=new ArrayList<>();
        Scoreboard board=world.getScoreboard();String previousTeam=p.getTeam()==null?null:p.getTeam().getName();ScorePlayerTeam team=board.getTeam("scroll038");
        if(team!=null)throw new IllegalStateException("Scroll fixture team already exists");
        CancelAttacks cancellation=new CancelAttacks();MinecraftForge.EVENT_BUS.register(cancellation);
        try{
            ScrollEvents.cleanup(p,false);clear(p);p.setSneaking(false);p.setHealth(p.getMaxHealth());PlayerState.data(p).setInteger("xp",1000000);PlayerState.setResolve(p,100);
            p.setPosition(.5,65,.5);p.ticksExisted=200;
            for(int x=-1;x<=10;x++)for(int z=-2;z<=3;z++){BlockPos floor=new BlockPos(x,64,z);world.getChunk(floor);world.setBlockState(floor,Blocks.STONE.getDefaultState());world.setBlockState(floor.up(),Blocks.AIR.getDefaultState());world.setBlockState(floor.up(2),Blocks.AIR.getDefaultState());}
            BlockPos ground=new BlockPos(3,64,0);ItemStack item=issue(p,"puppet_spindle","puppet");p.setHeldItem(EnumHand.MAIN_HAND,item);
            world.setBlockState(ground.up(),Blocks.STONE.getDefaultState());
            check(use(p,item,ground)==EnumActionResult.FAIL&&PlayerState.resolve(p)==100,"Blocked companion placement spent resolve or succeeded inside solid geometry");world.setBlockState(ground.up(),Blocks.AIR.getDefaultState());
            check(use(p,item,ground)==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==80,"Native companion deployment did not charge exactly once");
            ScrollJournal journal=ScrollJournal.get(p);String serial=item.getTagCompound().getString("valeSerial");NBTTagCompound row=journal.companion(serial);
            ScrollActor actor=ScrollActor.find(p,row.getUniqueId("actor"));check(actor!=null,"Deployed companion is missing");fixtures.add(actor);
            check(journal.activeCompanion(serial,actor.getUniqueID(),p.getUniqueID(),p.dimension)&&actor.order()==ScrollCompanionOrders.Order.ASSIST,"Native deployment did not bind owner/dimension/default order");
            cycle(p,item,"follow");cycle(p,item,"guard");cycle(p,item,"hold");
            check(!actor.command(p,p),"Companion accepted its owner as an attack target");
            NBTTagCompound saved=new NBTTagCompound();actor.writeToNBT(saved);ScrollActor restored=new ScrollActor(world);restored.readFromNBT(saved);
            check(restored.order()==ScrollCompanionOrders.Order.HOLD&&restored.sourceSerial.equals(serial),"Native actor NBT lost held order or contract");

            EntityZombie enemy=new EntityZombie(world);enemy.setNoAI(true);enemy.setPosition(5.5,65,.5);check(world.spawnEntity(enemy),"Could not spawn native hostile fixture");fixtures.add(enemy);
            EntityCow cow=new EntityCow(world);cow.setNoAI(true);cow.setPosition(4.5,65,1.5);check(world.spawnEntity(cow),"Could not spawn native livestock fixture");fixtures.add(cow);
            p.setLastAttackedEntity(enemy);float before=enemy.getHealth();tick(actor,40);
            check(enemy.getHealth()==before,"Hold order attacked automatically");
            item.getItem().onLeftClickEntity(item,p,enemy);enemy.hurtResistantTime=0;tick(actor,60);
            check(enemy.getHealth()<before,"Explicit native target command did not attack a permitted enemy");
            cancellation.target=enemy;before=enemy.getHealth();enemy.hurtResistantTime=0;tick(actor,80);
            check(enemy.getHealth()==before,"Companion damage ignored native LivingAttack cancellation");cancellation.target=null;
            p.setSneaking(true);item.getItem().onLeftClickEntity(item,p,enemy);p.setSneaking(false);before=enemy.getHealth();enemy.hurtResistantTime=0;tick(actor,100);
            check(enemy.getHealth()==before,"Cleared Hold command resumed attacking");
            cycle(p,item,"assist");p.setLastAttackedEntity(enemy);before=enemy.getHealth();enemy.hurtResistantTime=0;tick(actor,120);
            check(enemy.getHealth()<before,"Assist did not join the owner's recent actual attack");
            cycle(p,item,"follow");p.setLastAttackedEntity(enemy);before=enemy.getHealth();enemy.hurtResistantTime=0;tick(actor,140);
            check(enemy.getHealth()==before,"Follow order initiated combat without an explicit command");
            cycle(p,item,"guard");p.setLastAttackedEntity(null);before=enemy.getHealth();enemy.hurtResistantTime=0;tick(actor,160);
            check(enemy.getHealth()<before,"Guard did not defend its anchor from a permitted hostile");
            check(cow.getHealth()==cow.getMaxHealth()&&!actor.command(p,cow),"Guard or explicit commands permitted livestock damage");
            cancellation.deathTarget=actor;actor.setHealth(10);actor.hurtResistantTime=0;actor.attackEntityFrom(DamageSource.causeMobDamage(enemy),40);
            check(actor.isEntityAlive()&&row.getBoolean("deployed")&&row.getFloat("health")==7,"Canceled native death closed the deployment or lost rescued health");cancellation.deathTarget=null;
            actor.setHealth(17);actor.writeToNBT(new NBTTagCompound());
            check(row.getFloat("health")==17,"Entity save did not snapshot the current companion health");world.loadedEntityList.remove(actor);
            check(item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&!row.getBoolean("deployed")&&row.getFloat("health")==17,"Unloaded recall failed or destroyed saved health");
            check(use(p,item,ground)==EnumActionResult.SUCCESS,"Recalled unloaded companion could not redeploy immediately");
            ScrollActor replacement=ScrollActor.find(p,row.getUniqueId("actor"));check(replacement!=null&&replacement.getHealth()==17&&replacement.order()==ScrollCompanionOrders.Order.GUARD,"Redeploy lost saved health or orders");fixtures.add(replacement);
            world.loadedEntityList.add(actor);tick(actor,180);
            check(actor.isDead&&row.getBoolean("deployed")&&row.getUniqueId("actor").equals(replacement.getUniqueID()),"Stale unloaded actor retained authority or closed its replacement");
            replacement.setHealth(13);replacement.recordCompanion(false);Scrolls.state(p).setInteger("markDim",p.dimension);ScrollEvents.cleanup(p,true);
            check(replacement.isDead&&!row.getBoolean("deployed")&&row.getFloat("health")==13&&!Scrolls.state(p).hasKey("markDim"),"Dimension cleanup lost health or retained an old deployment/waymark");
            row.setFloat("health",0);p.inventory.setInventorySlotContents(1,new ItemStack(Items.IRON_INGOT,2));p.inventory.setInventorySlotContents(2,new ItemStack(Items.IRON_INGOT,1));
            check(item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL&&p.inventory.getStackInSlot(1).getCount()==2&&p.inventory.getStackInSlot(2).getCount()==1,"Incomplete repair consumed partial materials");
            p.inventory.setInventorySlotContents(3,new ItemStack(Items.IRON_INGOT,1));
            check(item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&row.getFloat("health")==30,"Split-stack repair did not restore companion");
            check(p.inventory.getStackInSlot(1).isEmpty()&&p.inventory.getStackInSlot(2).isEmpty()&&p.inventory.getStackInSlot(3).isEmpty(),"Repair did not consume exactly four split-stack ingots");
            check(use(p,item,ground)==EnumActionResult.SUCCESS,"Repaired companion could not deploy for confirmed-death regression");
            ScrollActor deathActor=ScrollActor.find(p,row.getUniqueId("actor"));check(deathActor!=null,"Confirmed-death companion is missing");fixtures.add(deathActor);deathActor.setHealth(16);
            Scrolls.state(p).setString("mode","fireedge");Scrolls.state(p).setLong("modeUntil",village.vale.ArsenalAccess.now(p)+1000);
            cancellation.deathTarget=p;LivingDeathEvent attemptedDeath=new LivingDeathEvent(p,DamageSource.causeMobDamage(enemy));MinecraftForge.EVENT_BUS.post(attemptedDeath);cancellation.deathTarget=null;
            check(attemptedDeath.isCanceled()&&row.getBoolean("deployed")&&!deathActor.isDead&&Scrolls.state(p).getString("mode").equals("fireedge"),"A late canceled player death closed companions or cleared the active scroll mode");
            EntityPlayerMP respawn=player(server,world,p.getName(),p.getUniqueID().toString());MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawn,p,true));
            check(deathActor.isDead&&!row.getBoolean("deployed")&&row.getFloat("health")==16&&Scrolls.state(p).getString("mode").isEmpty()&&Scrolls.state(respawn).getString("mode").isEmpty(),"Confirmed death clone retained a deployment/mode or lost companion health");
            cow.setDead();

            clear(p);PlayerState.setResolve(p,100);ItemStack seal=issue(p,"ancestral_seal","medic");p.setHeldItem(EnumHand.MAIN_HAND,seal);ScrollSupport.companion(p,seal).setInteger("variant",3);
            check(use(p,seal,ground)==EnumActionResult.SUCCESS,"Native medic deployment failed");
            NBTTagCompound medicRow=ScrollSupport.companion(p,seal);ScrollActor medic=ScrollActor.find(p,medicRow.getUniqueId("actor"));check(medic!=null&&medic.kind.equals("summon_3"),"Selected medic role was not deployed");fixtures.add(medic);
            check(village.vale.ArsenalAccess.allowed(p,enemy)&&p.canEntityBeSeen(enemy)&&!medic.command(p,enemy),"Medic accepted an attack command against a valid visible hostile");
            team=board.createTeam("scroll038");team.setAllowFriendlyFire(false);board.addPlayerToTeam(p.getName(),team.getName());
            EntityPlayerMP ally=player(server,world,"ScrollAlly038","38000000-0000-0000-0000-000000000011");ally.setPosition(4.5,65,.5);ally.setHealth(6);world.playerEntities.add(ally);allies.add(ally);board.addPlayerToTeam(ally.getName(),team.getName());
            EntityPlayerMP outsider=player(server,world,"ScrollOther038","38000000-0000-0000-0000-000000000012");outsider.setPosition(3.5,65,1.5);outsider.setHealth(2);world.playerEntities.add(outsider);allies.add(outsider);
            p.setHealth(p.getMaxHealth());float allyBefore=ally.getHealth(),otherBefore=outsider.getHealth();tick(medic,40);
            check(ally.getHealth()>allyBefore&&outsider.getHealth()==otherBefore,"Expedition medic failed to heal the injured ally or healed an unrelated player");
            ally.setPosition(8.5,65,.5);allyBefore=ally.getHealth();tick(medic,80);
            check(ally.getHealth()==allyBefore,"Medic healed outside its actual treatment range");
            ScrollSupport.closeOwner(p);check(!medicRow.getBoolean("deployed")&&medic.isDead,"Owner cleanup left the medic deployed");
            System.out.println("VALE_SCROLL038_NATIVE_PASS: "+assertions+" companion order, protection, recall, repair and expedition-support assertions");return assertions;
        }finally{
            cancellation.target=null;cancellation.deathTarget=null;MinecraftForge.EVENT_BUS.unregister(cancellation);p.setSneaking(false);ScrollSupport.closeOwner(p);
            for(Entity entity:fixtures){entity.setDead();world.removeEntity(entity);}
            for(EntityPlayerMP ally:allies){world.playerEntities.remove(ally);board.removePlayerFromTeams(ally.getName());}
            board.removePlayerFromTeams(p.getName());ScorePlayerTeam temporary=board.getTeam("scroll038");if(temporary!=null)board.removeTeam(temporary);
            if(previousTeam!=null&&board.getTeam(previousTeam)!=null)board.addPlayerToTeam(p.getName(),previousTeam);
        }
    }
    private static EntityPlayerMP player(MinecraftServer server,WorldServer world,String name,String uuid){
        EntityPlayerMP player=new EntityPlayerMP(server,world,new GameProfile(UUID.fromString(uuid),name),new PlayerInteractionManager(world));
        NetworkManager network=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(network);
        player.connection=new NetHandlerPlayServer(server,network,player){@Override public void sendPacket(Packet<?> packet){}};return player;
    }
    public static final class CancelAttacks{
        Entity target,deathTarget;
        @SubscribeEvent(priority=EventPriority.LOWEST) public void death(LivingDeathEvent event){if(event.getEntityLiving()==deathTarget){event.getEntityLiving().setHealth(7);event.setCanceled(true);}}
        @SubscribeEvent public void attack(LivingAttackEvent event){if(event.getEntityLiving()==target)event.setCanceled(true);}
    }
}
