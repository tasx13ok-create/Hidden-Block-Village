package village.vale;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Actual survival player, resident interaction, recipe and damage acceptance. Private fixture only. */
public final class AcademyNativeChecks {
    private static int assertions;private static Learner p;private static WorldServer world;private static AcademyCourse course;
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    public static int run(MinecraftServer server){
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(server.getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Academy fixture isolation required");
        assertions=0;world=server.getWorld(0);course=AcademyCourse.get(world);BlockPos anchor=new BlockPos(256,100,256);
        for(int x=13;x<=18;x++)for(int z=14;z<=20;z++)world.getChunk(x,z);
        check(course.build(world,anchor),"Academy course build failed: "+course.problem);
        check(!course.build(world,anchor),"Course duplicate build allowed");
        AcademyCourse loaded=new AcademyCourse();loaded.readFromNBT(course.writeToNBT(new NBTTagCompound()));check(loaded.active&&loaded.guides.equals(course.guides),"Course guide identity reload");
        p=login(server,false);Gate gate=new Gate();MinecraftForge.EVENT_BUS.register(gate);
        try{
            check(!Missions.accept(p,"academy_dispatch"),"Unenrolled dispatch accepted");
            for(int i=0;i<36;i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.DIRT,64));
            String[] enrollment=offer("arrival","enroll");check(!AcademyCampaign.choose(p,"bad",enrollment[1]),"Forged dialogue accepted");
            check(AcademyCampaign.choose(p,enrollment[0],enrollment[1]),"Enrollment failed");
            check(!AcademyCampaign.choose(p,enrollment[0],enrollment[1])&&PlayerState.data(p).getInteger("xp")==25,"Enrollment replay duplicated XP");
            int queued=RewardLedger.get(p).pendingCount(p.getUniqueID());AcademyCampaign.settle(p);check(queued==3&&RewardLedger.get(p).pendingCount(p.getUniqueID())==queued,"Full inventory academy outbox duplicated: "+queued);
            clear();RewardLedger.get(p).deliver(p);AcademyCampaign.settle(p);check(count("mission_journal")==1&&count("travel_ration")==2,"Queued enrollment kit delivery");
            choose("mentor","wind");check(PlayerState.data(p).getString("starterOrder").equals("gale"),"Nature selection");
            for(EntityEquipmentSlot slot:new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET}){
                String part=slot==EntityEquipmentSlot.HEAD?"helmet":slot==EntityEquipmentSlot.CHEST?"chestplate":slot==EntityEquipmentSlot.LEGS?"leggings":"boots";
                ItemStack armor=find("gale_"+part);remove(armor);p.setItemStackToSlot(slot,armor);
            }
            check(Combat.fullSet(p)==Order.GALE,"Issued academy armor unavailable");
            move(-20,12);AcademyCampaign.tick(p);check(AcademyCampaign.state(p).getInteger("objective")==0,"Teleport credited walking");
            move(-23,12);step(2);AcademyCampaign.tick(p);for(int[] point:new int[][]{{-20,12},{-12,24},{0,24}})walk(point[0],point[1]);
            check(AcademyProgress.ready(AcademyCampaign.state(p)),"Walking route failed");choose("mentor","claim");check(PlayerState.level(p)>=2,"Route cannot unlock first art");
            choose("mentor","practice");EntityIronGolem dummy=AcademyCampaign.target(p);check(dummy!=null,"Training construct missing");
            check(!dummy.attackEntityFrom(DamageSource.GENERIC,100),"Environment damaged personal construct");
            move(0,5);AcademyCampaign.tick(p);move(3,5);step(40);AcademyCampaign.tick(p);check(AcademyCampaign.state(p).getInteger("objective")==1,"Evade strike failed");
            hold(find("gale_saber"));p.setSneaking(true);PlayerState.setResolve(p,100);move(0,5);AcademyCampaign.tick(p);
            check(p.getHeldItemMainhand().getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Issued guard activation failed");
            p.setSneaking(false);p.hurtResistantTime=0;step(40);AcademyCampaign.tick(p);check(AcademyCampaign.state(p).getInteger("objective")==3,"Actual guarded strike failed");
            hold(find("scroll_reed_escape"));use();hold(find("technique_reed_escape"));p.setSneaking(true);use();step(31);use();step(31);p.setSneaking(false);use();
            move(0,5);AcademyCampaign.tick(p);p.hurtResistantTime=0;step(40);AcademyCampaign.tick(p);check(AcademyProgress.ready(AcademyCampaign.state(p)),"Actual Reed Escape did not satisfy substitution");
            choose("mentor","claim");hold(find("technique_codex"));move(0,4);p.rotationYaw=0;p.rotationPitch=0;PlayerState.setResolve(p,100);
            gate.block=dummy;int resolve=PlayerState.resolve(p);dummy.hurtResistantTime=0;check(!Arts.cast(p)&&PlayerState.resolve(p)==resolve&&AcademyCampaign.state(p).getInteger("objective")==0,"Canceled cast credited academy or spent resolve");gate.block=null;
            for(int i=0;i<3;i++){dummy.hurtResistantTime=0;PlayerState.setResolve(p,100);check(Arts.cast(p),"Actual academy lance failed "+i);step(161);}
            check(AcademyProgress.ready(AcademyCampaign.state(p)),"Three accepted target hits failed");choose("mentor","claim");AcademyCampaign.tick(p);check(AcademyCampaign.target(p)==null,"Completed training leaked construct");
            choose("supplies","kit");check(count(Items.BREAD)==1&&count(Items.COOKED_BEEF)==1&&count(Items.CARROT)==1,"Ingredient outbox failed");choose("supplies","kit");check(count(Items.BREAD)==1,"Ingredient kit replay");
            move(14,8);ContainerWorkbench table=new ContainerWorkbench(p.inventory,world,course.at(14,8));p.openContainer=table;
            net.minecraft.item.Item[] ingredients={Items.BREAD,Items.COOKED_BEEF,Items.CARROT};
            for(int i=0;i<3;i++){ItemStack ingredient=find(ingredients[i]);remove(ingredient);table.craftMatrix.setInventorySlotContents(i,ingredient);}
            check(table.craftResult.getStackInSlot(0).getItem()==Gear.ITEMS.get("travel_ration"),"Native ration recipe missing");table.slotClick(0,0,ClickType.PICKUP,p);
            ItemStack crafted=p.inventory.getItemStack();check(crafted.getCount()==2&&AcademyProgress.ready(AcademyCampaign.state(p)),"Actual recipe pickup did not record objective");p.inventory.setItemStack(ItemStack.EMPTY);p.inventory.addItemStackToInventory(crafted);p.openContainer=p.inventoryContainer;
            choose("supplies","ration");check(AcademyCampaign.basicsComplete(p)&&PlayerState.data(p).getInteger("xp")==300,"Academy completion XP mismatch");
            check(!Missions.accept(p,"academy_ambush"),"Ambush skipped dispatch prerequisite");choose("courier","dispatch");check(!attempt("courier","deliver"),"Delivery without pickup accepted");
            interact("contracts");choose("courier","deliver");choose("courier","missionclaim");int completedXp=PlayerState.data(p).getInteger("xp");check(completedXp==425&&!Missions.claim(p),"Dispatch reward mismatch/replay");
            choose("courier","ambush");check(actors()==2,"Ambush did not spawn two owned raiders");choose("courier","ambush");check(actors()==2,"Ambush resume duplicated actors");
            killOne();check(PlayerState.data(p).getInteger("missionProgress")==1,"Native raider kill not credited");choose("courier","ambush");check(actors()==1,"Partial ambush respawned a defeated raider");
            server.getPlayerList().playerLoggedOut(p);check(!AcademyCampaign.state(p).hasUniqueId("ambush0")&&!AcademyCampaign.state(p).hasUniqueId("ambush1")&&!AcademyCampaign.state(p).hasUniqueId("target"),"Logout retained UUID pair tags");p=login(server,true);check(PlayerState.data(p).getInteger("missionProgress")==1&&AcademyCampaign.basicsComplete(p),"Saved academy/mission reload failed");
            choose("courier","ambush");check(actors()==1,"Reconnect resume did not respect earned progress: live="+actors());killOne();choose("courier","missionclaim");AcademyCampaign.tick(p);
            check(actors()==0&&PlayerState.data(p).getInteger("missionsCompleted")==2&&PlayerState.data(p).getInteger("xp")==650,"Ambush settlement or cleanup failed");
            check(!Missions.claim(p)&&PlayerState.data(p).getInteger("xp")==650,"Final reward replay");
            assertions+=LightningNativeChecks.run(p);
            return assertions;
        }finally{MinecraftForge.EVENT_BUS.unregister(gate);if(p!=null&&server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p)server.getPlayerList().playerLoggedOut(p);}
    }
    private static Learner login(MinecraftServer server,boolean restore){Learner n=new Learner(server,world);n.connection=new Quiet(server,n);if(restore)server.getPlayerList().readPlayerDataFromFile(n);n.interactionManager.setGameType(GameType.SURVIVAL);server.getPlayerList().playerLoggedIn(n);FMLCommonHandler.instance().firePlayerLoggedIn(n);ReflectionHelper.setPrivateValue(EntityPlayerMP.class,n,0,"respawnInvulnerabilityTicks","field_147101_bU");return n;}
    private static void step(int ticks){world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+ticks);for(int i=0;i<ticks;i++)p.getCooldownTracker().tick();}
    private static void move(double x,double z){p.setPosition(course.origin.getX()+x+.5,course.origin.getY()+1,course.origin.getZ()+z+.5);p.onGround=true;}
    private static void walk(double x,double z){Vec3d target=new Vec3d(course.origin.getX()+x+.5,course.origin.getY()+1,course.origin.getZ()+z+.5);for(int i=0;i<200;i++){Vec3d delta=target.subtract(p.getPositionVector());if(delta.lengthSquared()<.01)break;Vec3d next=p.getPositionVector().add(delta.normalize().scale(Math.min(.7,Math.sqrt(delta.lengthSquared()))));p.setPosition(next.x,next.y,next.z);step(2);AcademyCampaign.tick(p);}step(2);AcademyCampaign.tick(p);}
    private static void interact(String role){Entity guide=world.getEntityFromUuid(course.guides.get(role));check(guide!=null,"Guide missing "+role);p.setPosition(guide.posX+1,guide.posY,guide.posZ);p.messages.clear();check(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,EnumHand.MAIN_HAND,guide)),"Guide trade was not intercepted");}
    private static String[] offer(String role,String action){interact(role);for(ITextComponent message:p.messages){ClickEvent click=message.getStyle().getClickEvent();if(click!=null&&click.getValue().endsWith(" "+action)){String[] parts=click.getValue().split(" ");return new String[]{parts[2],parts[3]};}}throw new AssertionError("No dialogue action "+role+"/"+action+": "+p.messages);}
    private static boolean attempt(String role,String action){String[] choice=offer(role,action);return AcademyCampaign.choose(p,choice[0],choice[1]);}
    private static void choose(String role,String action){check(attempt(role,action),"Dialogue failed "+role+"/"+action+": "+p.messages);}
    private static void clear(){for(int i=0;i<36;i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private static int count(String key){return count(Gear.ITEMS.get(key));}private static int count(net.minecraft.item.Item item){int count=0;for(ItemStack s:p.inventory.mainInventory)if(s.getItem()==item)count+=s.getCount();return count;}
    private static ItemStack find(String key){return find(Gear.ITEMS.get(key));}private static ItemStack find(net.minecraft.item.Item item){for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==item)return stack;throw new AssertionError("Missing "+item.getRegistryName());}
    private static void remove(ItemStack stack){for(int i=0;i<36;i++)if(p.inventory.mainInventory.get(i)==stack)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private static void hold(ItemStack stack){remove(stack);ItemStack previous=p.getHeldItemMainhand();p.inventory.mainInventory.set(p.inventory.currentItem,ItemStack.EMPTY);p.inventory.addItemStackToInventory(previous);p.inventory.currentItem=0;ItemStack displaced=p.inventory.mainInventory.get(0);p.inventory.mainInventory.set(0,stack);if(!displaced.isEmpty())p.inventory.addItemStackToInventory(displaced);}
    private static void use(){check(p.getHeldItemMainhand().getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Technique use failed: "+p.messages);}
    private static int actors(){int count=0;for(Entity e:new ArrayList<>(world.loadedEntityList))if(e.isEntityAlive()&&e.getEntityData().hasUniqueId(AcademyCampaign.ACTOR_OWNER)&&e.getEntityData().getUniqueId(AcademyCampaign.ACTOR_OWNER).equals(p.getUniqueID())&&"ambush".equals(e.getEntityData().getString(AcademyCampaign.ACTOR_KIND)))count++;return count;}
    private static void killOne(){for(Entity e:new ArrayList<>(world.loadedEntityList))if(e instanceof FieldRogue&&e.isEntityAlive()&&AcademyCampaign.ambushMatches(p,(FieldRogue)e)){check(e.attackEntityFrom(DamageSource.causePlayerDamage(p),1000),"Native owned raider damage rejected");((FieldRogue)e).deathTime=1;Missions.flushDeaths(p.getServer());Adventures.save(p);return;}throw new AssertionError("No owned raider");}
    public static final class Gate {@SubscribeEvent(priority=EventPriority.HIGHEST)public void attack(LivingAttackEvent e){if(e.getEntityLiving()==block)e.setCanceled(true);}Entity block;}
    private static final class Learner extends EntityPlayerMP {final List<ITextComponent> messages=new ArrayList<>();Learner(MinecraftServer s,WorldServer w){super(s,w,new GameProfile(UUID.fromString("43ec7807-821a-4a56-9c03-070031000001"),"Academy0310"),new PlayerInteractionManager(w));}@Override public void sendMessage(ITextComponent value){if(messages!=null)messages.add(value.createCopy());}@Override public void sendStatusMessage(ITextComponent value,boolean bar){if(messages!=null)messages.add(value.createCopy());}}
    private static final class Quiet extends NetHandlerPlayServer {Quiet(MinecraftServer s,EntityPlayerMP p){super(s,network(),p);}private static NetworkManager network(){NetworkManager n=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(n);return n;}@Override public void sendPacket(Packet<?> packet){}}
}
