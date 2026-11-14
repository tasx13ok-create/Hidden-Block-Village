package village.vale.expedition;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.network.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.*;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.*;

/** Private native acceptance: isolated synthetic measured route, never a live placement. */
public final class GateNativeChecks {
    private EntityPlayerMP p;private final EntityPlayerMP outsider;private final MinecraftServer server;private final WorldServer world;private final UUID owner=UUID.fromString("43ec7807-821a-4a56-9c03-070031300001");private final Gate gate=new Gate();private final List<Entity> cleanup=new ArrayList<>();private int assertions;
    private final NBTTagCompound original;private final List<ItemStack> inventory=new ArrayList<>();private Object previousBook;private EntityLiving giver;
    private final Vec3d originalPosition;private final float yaw,pitch;private File definitions,sites;
    private GateNativeChecks(EntityPlayerMP context){outsider=context;server=context.getServer();world=server.getWorld(0);p=login(false);original=PlayerState.data(p).copy();originalPosition=p.getPositionVector();yaw=p.rotationYaw;pitch=p.rotationPitch;for(ItemStack s:p.inventory.mainInventory)inventory.add(s.copy());}
    public static int run(EntityPlayerMP context){GateNativeChecks c=new GateNativeChecks(context);MinecraftForge.EVENT_BUS.register(c.gate);try{c.exercise();return c.assertions;}catch(Exception error){throw new AssertionError("Gate native fixture failed",error);}finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.restore();}}
    private void check(boolean ok,String text){assertions++;if(!ok)throw new AssertionError("Gate native: "+text);}
    private void step(){
        // The isolated fixture does not run World.updateEntities: drain exactly
        // its own already-dead actors using native removal before respawn checks.
        // Native removeEntity alone defers chunk detachment to that world loop.
        for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))if(entity.getEntityData().hasUniqueId(GateRuntime.OWNER)&&owner.equals(entity.getEntityData().getUniqueId(GateRuntime.OWNER))){
            // Native death animation still needs ticks after its objective UUID
            // is credited/forgotten. Never synthesize a corpse's dead outcome.
            if(!entity.isDead&&entity instanceof EntityLivingBase&&!((EntityLivingBase)entity).isEntityAlive())world.updateEntityWithOptionalForce(entity,true);
            if(entity.isDead)world.removeEntityDangerously(entity);
        }
        world.getWorldInfo().setWorldTotalTime(Adventures.now(p)+1);GateRuntime.tick(p);for(int slot=0;slot<9;slot++){Entity actor=GateRuntime.actor(p,slot);if(actor!=null&&!actor.isDead&&(!(actor instanceof EntityLivingBase)||((EntityLivingBase)actor).isEntityAlive())){world.updateEntityWithOptionalForce(actor,true);if(actor instanceof EntityLivingBase)((EntityLivingBase)actor).hurtResistantTime=0;}}GateEvents.flush();}
    private EntityPlayerMP login(boolean restore){Learner next=new Learner(server,world,owner);next.connection=new Quiet(server,next);if(restore)server.getPlayerList().readPlayerDataFromFile(next);next.interactionManager.setGameType(GameType.SURVIVAL);server.getPlayerList().playerLoggedIn(next);FMLCommonHandler.instance().firePlayerLoggedIn(next);ReflectionHelper.setPrivateValue(EntityPlayerMP.class,next,0,"respawnInvulnerabilityTicks","field_147101_bU");return next;}
    private void reconnect(){server.getPlayerList().playerLoggedOut(p);p=login(true);}
    private void clearInventory(){for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private NBTTagCompound s(){return GateRuntime.state(p);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private String token()throws Exception{@SuppressWarnings("unchecked")Map<UUID,Object> sessions=(Map<UUID,Object>)field(GateRuntime.class,"SESSIONS").get(null);Object session=sessions.get(p.getUniqueID());return (String)field(session.getClass(),"token").get(session);}
    private void click(int index,boolean cancel){BlockPos block=new BlockPos(741+index,79,744);gate.block=cancel?block:null;
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p,EnumHand.MAIN_HAND,block,EnumFacing.UP,new Vec3d(block.getX()+.5,80,block.getZ()+.5)));GateEvents.flush();gate.block=null;}
    private void moveToArrival(){p.setPositionAndRotation(740.5,80,740.5,0,0);p.onGround=true;p.capabilities.isFlying=false;p.dismountRidingEntity();step();
        for(int i=0;i<5&&!s().getString("stage").equals("choose");i++){p.move(MoverType.SELF,0,0,.6);p.onGround=true;step();}
        check(s().getString("stage").equals("choose"),"Native small-step walk did not cross arrival boundary");}
    private void kill(EntityLivingBase actor)throws Exception{int xp=PlayerState.data(p).getInteger("xp");actor.hurtResistantTime=0;check(actor.attackEntityFrom(DamageSource.causePlayerDamage(p),1000),"Owned native encounter rejected player damage");actor.deathTime=1;GateEvents.flush();Missions.flushDeaths(p.getServer());check(PlayerState.data(p).getInteger("xp")==xp,"Managed actor leaked generic contract kill XP");}
    private void supplies(){for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
        ItemStack named=new ItemStack(Items.BREAD,4);named.setStackDisplayName("Fixture named bread");p.inventory.mainInventory.set(0,named);
        check(!GateRuntime.claim(p,giver)&&named.getCount()==4,"Named delivery stack was accepted/consumed");
        p.inventory.mainInventory.set(1,new ItemStack(Items.BREAD,4));check(GateRuntime.claim(p,giver)&&s().getBoolean("done")&&named.getCount()==4&&p.inventory.mainInventory.get(1).isEmpty(),"Plain delivery did not consume exactly four and finish");}
    private void timedStages()throws Exception{
        check(s().getString("stage").equals("hold"),"Timed route did not start at real defense checkpoint");
        p.setPosition(745.5,80,746.5);for(int i=0;i<10;i++)step();
        check(s().getInteger("observed")>0&&s().getInteger("observed")<40,"Defend point did not observe real bounded ticks");
        p.setPosition(754.5,80,746.5);step();check(s().getString("stage").equals("hold")&&s().getInteger("observed")==0&&!s().hasUniqueId("actor0"),"Leaving defended point did not reset and clear its actual actor");
        p.setPosition(745.5,80,746.5);for(int i=0;i<10;i++)step();reconnect();
        check(s().getString("stage").equals("hold")&&s().getInteger("observed")==0&&!s().hasUniqueId("actor0"),"Defend reconnect retained offline timer or actor");
        // Long time without observed ticks must not complete the hold.
        world.getWorldInfo().setWorldTotalTime(Adventures.now(p)+200);step();check(s().getString("stage").equals("hold"),"Offline world-time gap completed defense");
        for(int i=0;i<45&&s().getString("stage").equals("hold");i++)step();check(s().getString("stage").equals("escort"),"Continuous native defend observations did not advance");
        // Inside the old32-block spawn trigger but outside approach8: no retry, timer or actor.
        p.setPosition(736.5,80,750.5);long run=s().getLong("run");for(int i=0;i<5;i++)step();
        check(s().getString("stage").equals("escort")&&s().getLong("run")==run&&!s().hasUniqueId("actor0")&&!s().hasKey("started")&&s().getInteger("observed")==0,"Far friendly approach spawned/retried/started a timer");
        p.setPosition(746.5,80,753.0);step();Entity first=GateRuntime.actor(p,0);check(first instanceof EntityVillager,"Escort did not create an actual native villager");cleanup.add(first);
        gate.escort=first;gate.escortEvent=null;net.minecraft.inventory.Container current=p.openContainer;p.interactOn(first,EnumHand.MAIN_HAND);
        check(gate.escortEvent!=null&&gate.escortEvent.isCanceled()&&p.openContainer==current&&!((EntityVillager)first).isTrading(),"Mission courier opened a native trade interaction");gate.escort=null;
        first.setDead();world.removeEntity(first);step();check(s().getString("stage").equals("escort")&&!s().hasUniqueId("actor0"),"Downed escort did not reset checkpoint");step();Entity replacement=GateRuntime.actor(p,0);check(replacement instanceof EntityVillager&&replacement!=first,"Escort retry reused lost actor");cleanup.add(replacement);
        int moves=0;for(;moves<100&&s().getString("stage").equals("escort");moves++){p.move(MoverType.SELF,0,0,.08);p.onGround=true;step();}
        check(s().getString("stage").equals("skiff"),"Actual pathfinding escort failed after "+moves+" walking steps: p="+p.getPositionVector()+" actor="+replacement.getPositionVector()+" observed="+s().getInteger("observed"));
        check(replacement.isDead&&s().getString("checkpoint").equals("escort_end"),"Reached escort checkpoint did not clear owned actor");
        p.setPosition(747.5,80,750.5);step();Entity boat=GateRuntime.actor(p,0);check(boat instanceof GateSkiff,"Protect stage did not create registered native skiff");cleanup.add(boat);
        Entity priorRide=outsider.getRidingEntity();outsider.interactOn(boat,EnumHand.MAIN_HAND);check(outsider.getRidingEntity()==priorRide&&!boat.getPassengers().contains(outsider),"Unrelated player boarded the owned native skiff");
        p.setSneaking(false);p.interactOn(boat,EnumHand.MAIN_HAND);check(p.getRidingEntity()==boat,"Matching owner could not board the native skiff");p.dismountRidingEntity();check(!p.isRiding(),"Owner could not dismount the native skiff");
        check(!boat.attackEntityFrom(DamageSource.causePlayerDamage(p),6),"Owner melee bypassed protected skiff authority");
        check(boat.attackEntityFrom(DamageSource.GENERIC,6)&&boat.isDead,"Native environmental damage did not down skiff");step();
        check(s().getString("stage").equals("skiff")&&!s().hasUniqueId("actor0")&&s().getInteger("observed")==0,"Downed skiff did not reset timer/checkpoint");step();
        Entity next=GateRuntime.actor(p,0);check(next instanceof GateSkiff&&next!=boat,"Skiff retry did not create fresh owned native boat: stage="+s().getString("stage")+" actor="+next+" collisions="+world.getCollisionBoxes(null,new AxisAlignedBB(750.8,80,749.8,752.2,80.6,751.2)).size());cleanup.add(next);
        for(int i=0;i<10;i++)step();reconnect();check(s().getString("stage").equals("skiff")&&s().getInteger("observed")==0&&!s().hasUniqueId("actor0"),"Skiff reconnect retained timer/actor");
        for(int i=0;i<45&&s().getString("stage").equals("skiff");i++)step();check(s().getString("stage").equals("supplies"),"Real protected skiff presence did not complete observed timer");
    }
    private void repeatRoute()throws Exception{
        world.getWorldInfo().setWorldTotalTime(Adventures.now(p)+6001);p.setPosition(740.5,80,740.5);check(GateRuntime.accept(p,"gate_native_validation",giver),"Repeat did not reopen after cooldown");moveToArrival();String branch=token();check(GateRuntime.choose(p,branch,"fight"),"Second real branch could not be chosen");step();
        for(int slot=0;slot<2;slot++){EntityLivingBase actor=(EntityLivingBase)GateRuntime.actor(p,slot);check(actor!=null,"Repeat missing actual native opponent");cleanup.add(actor);kill(actor);}step();timedStages();p.setPosition(740.5,80,740.5);supplies();
        int xp=PlayerState.data(p).getInteger("xp"),merit=PlayerState.data(p).getInteger("merit");check(GateRuntime.claim(p,giver)&&PlayerState.data(p).getInteger("xp")==xp+50&&PlayerState.data(p).getInteger("merit")==merit+1,"Repeat paid first-clear rewards or lost reduced reward");
        check(!GateRuntime.claim(p,giver)&&PlayerState.data(p).getInteger("xp")==xp+50,"Repeat claim replay duplicated XP");
    }
    private void exercise()throws Exception{
        previousBook=field(GateRuntime.class,"book").get(null);
        // Real World entity updates require a loaded32-block neighborhood.
        for(int x=44;x<=49;x++)for(int z=44;z<=49;z++)world.getChunk(x,z);
        // Deliberately isolated native-fixture platform, not a production placement proposal.
        for(int x=736;x<=758;x++)for(int z=736;z<=764;z++)for(int y=79;y<=83;y++)p.world.setBlockState(new BlockPos(x,y,z),y==79?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        for(int x=750;x<=752;x++)for(int z=749;z<=751;z++){p.world.setBlockState(new BlockPos(x,78,z),Blocks.STONE.getDefaultState(),2);p.world.setBlockState(new BlockPos(x,79,z),Blocks.WATER.getDefaultState(),2);}
        Class<?> resident=Class.forName("village.residents.EntityResident"),role=Class.forName("village.residents.ResidentRole");giver=(EntityLiving)resident.getConstructor(net.minecraft.world.World.class).newInstance(p.world);resident.getMethod("configure",role).invoke(giver,role.getField("CONTRACTS").get(null));giver.setNoAI(true);giver.setPosition(740.5,80,740.5);check(p.world.spawnEntity(giver),"Actual configured Residents giver did not spawn");cleanup.add(giver);
        File directory=Files.createTempDirectory("vale-native-gates-").toFile();new File(directory,"dialogue").mkdir();
        String dialogue="{\"schema\":1,\"resident\":\"native_keeper\",\"start\":\"welcome\",\"after_clear_start\":\"welcome\",\"pages\":[{\"id\":\"welcome\",\"lines\":[\"Native test only\"],\"choices\":[{\"id\":\"accept\",\"label\":\"Accept\",\"action\":\"ACCEPT_CONTRACT\",\"target\":\"gate_native_validation\"},{\"id\":\"claim\",\"label\":\"Claim\",\"action\":\"CLAIM_CONTRACT\"}]}]}";
        write(new File(directory,"dialogue/native_keeper.json"),dialogue);
        String content="{\"schema\":1,\"content_version\":\"native-gate-v1\",\"anchors\":{\"arrival\":{\"dungeon\":\"native_v17\",\"kind\":\"arrival\",\"local_pos\":[0,0,3]},\"first\":{\"dungeon\":\"native_v17\",\"kind\":\"marker\",\"local_pos\":[1,0,4]},\"second\":{\"dungeon\":\"native_v17\",\"kind\":\"marker\",\"local_pos\":[2,0,4]},\"yard\":{\"dungeon\":\"native_v17\",\"kind\":\"encounter\",\"local_pos\":[2,0,8]}},\"residents\":[{\"id\":\"native_keeper\",\"dialogue\":\"dialogue/native_keeper.json\"}],\"missions\":[{\"id\":\"gate_native_validation\",\"title\":\"Native route\",\"giver\":\"native_keeper\",\"level\":1,\"prerequisites\":{\"contracts_completed\":[\"academy_ambush\"]},\"start_stage\":\"arrive\",\"stages\":[{\"id\":\"arrive\",\"text\":\"Walk\",\"objective\":{\"type\":\"reach_marker\",\"anchor\":\"arrival\",\"radius\":1},\"next\":\"choose\"},{\"id\":\"choose\",\"text\":\"Choose\",\"objective\":{\"type\":\"choose_branch\"},\"branches\":[{\"id\":\"read\",\"label\":\"Markers\",\"next\":\"markers\"},{\"id\":\"fight\",\"label\":\"Fight\",\"next\":\"fight\"}]},{\"id\":\"markers\",\"text\":\"Click\",\"objective\":{\"type\":\"interact_sequence\",\"anchors\":[\"first\",\"second\"],\"ordered\":true},\"on_fail\":{\"kind\":\"wrong_order\",\"effect\":\"reset\",\"retry_from\":\"markers\"},\"next\":\"fight\"},{\"id\":\"fight\",\"text\":\"Defeat\",\"objective\":{\"type\":\"defeat_group\",\"anchor\":\"yard\",\"profiles\":[{\"key\":\"mob_moss_sentry\",\"count\":2}]},\"next\":\"supplies\"},{\"id\":\"supplies\",\"text\":\"Bread\",\"objective\":{\"type\":\"deliver_items\",\"item\":\"minecraft:bread\",\"count\":4,\"plain_only\":true},\"next\":null}],\"failure\":{},\"rewards\":{\"first_clear\":{\"xp\":100,\"merit\":3,\"ledger_items\":[{\"key\":\"travel_ration\",\"count\":2}],\"unlocks\":[\"expedition_native_entry\"],\"lesson_preview\":\"native_preview\"},\"repeat\":{\"xp\":50,\"merit\":1,\"ledger_items\":[{\"key\":\"travel_ration\",\"count\":1}]},\"receipt\":\"gate/{player}/{mission}/{run}\",\"cooldown_ticks\":6000}}]}";
        definitions=new File(directory,"gates.json");sites=new File(directory,"sites.json");JsonObject fixture=new JsonParser().parse(content).getAsJsonObject();JsonObject nativeMission=fixture.getAsJsonArray("missions").get(0).getAsJsonObject();JsonArray prerequisites=new JsonArray();prerequisites.add("supply_run");nativeMission.getAsJsonObject("prerequisites").add("contracts_completed",prerequisites);
        JsonObject anchors=fixture.getAsJsonObject("anchors");anchors.add("hold_point",new JsonParser().parse("{\"dungeon\":\"native_v17\",\"kind\":\"checkpoint\",\"local_pos\":[5,0,6]}"));anchors.add("escort_end",new JsonParser().parse("{\"dungeon\":\"native_v17\",\"kind\":\"checkpoint\",\"local_pos\":[6,0,16]}"));
        JsonArray stages=nativeMission.getAsJsonArray("stages");for(JsonElement element:stages)if(element.getAsJsonObject().get("id").getAsString().equals("fight"))element.getAsJsonObject().addProperty("next","hold");
        stages.add(new JsonParser().parse("{\"id\":\"hold\",\"text\":\"Hold\",\"objective\":{\"type\":\"defend_point\",\"anchor\":\"hold_point\",\"seconds\":2,\"profiles\":[{\"key\":\"mob_moss_sentry\",\"count\":1}]},\"on_fail\":{\"kind\":\"platform_lost\",\"effect\":\"reset\",\"retry_from\":\"hold\"},\"next\":\"escort\"}"));
        stages.add(new JsonParser().parse("{\"id\":\"escort\",\"text\":\"Escort\",\"objective\":{\"type\":\"escort_actor\",\"actor\":\"archive_courier\",\"to\":\"escort_end\",\"max_seconds\":30},\"on_fail\":{\"kind\":\"actor_downed\",\"effect\":\"reset\",\"retry_from\":\"escort\"},\"next\":\"skiff\"}"));
        stages.add(new JsonParser().parse("{\"id\":\"skiff\",\"text\":\"Protect\",\"objective\":{\"type\":\"protect_actor\",\"actor\":\"pilot_skiff\",\"seconds\":2},\"on_fail\":{\"kind\":\"actor_downed\",\"effect\":\"reset\",\"retry_from\":\"skiff\"},\"next\":\"supplies\"}"));write(definitions,fixture.toString());
        write(sites,"{\"schema\":1,\"placements\":{\"native_v17\":{\"dimension\":0,\"origin\":[740,80,740]}},\"givers\":{\"native_keeper\":{\"uuid\":\""+giver.getUniqueID()+"\",\"dimension\":0,\"pos\":[740,80,740]}},\"marker_blocks\":{\"first\":{\"dimension\":0,\"pos\":[741,79,744]},\"second\":{\"dimension\":0,\"pos\":[742,79,744]}}}");
        JsonObject binding=new JsonParser().parse(new String(Files.readAllBytes(sites.toPath()),StandardCharsets.UTF_8)).getAsJsonObject();binding.add("actor_spawns",new JsonParser().parse("{\"gate_native_validation/escort\":{\"dimension\":0,\"pos\":[746,80,750]},\"gate_native_validation/skiff\":{\"dimension\":0,\"pos\":[751,80,750]}}"));write(sites,binding.toString());
        check(GateRuntime.load(definitions,sites).startsWith("Loaded"),"Synthetic measured route did not load");PlayerState.data(p).removeTag("Gates");PlayerState.data(p).removeTag("mission");PlayerState.data(p).removeTag("completedContracts");p.setPosition(740.5,80,740.5);
        check(!GateRuntime.accept(p,"gate_native_validation",giver),"Missing completed-contract history was inferred from level/count");PlayerState.data(p).setString("starterOrder","flint");clearInventory();p.inventory.mainInventory.set(0,new ItemStack(Items.WHEAT,16));check(Missions.accept(p,"supply_run")&&Missions.claim(p),"Actual flat contract could not settle prerequisite history");
        check(PlayerState.data(p).getCompoundTag("completedContracts").getCompoundTag("claims").getString("supply_run").startsWith("mission/"+p.getUniqueID()+"/"),"Completed history did not bind the actual claimed receipt");
        gate.giver=giver;gate.cancelInteract=true;p.interactOn(giver,EnumHand.MAIN_HAND);check(!((Map<?,?>)field(GateRuntime.class,"SESSIONS").get(null)).containsKey(p.getUniqueID()),"Canceled native EntityInteract opened a gate dialogue");gate.cancelInteract=false;
        p.interactOn(giver,EnumHand.MAIN_HAND);String accept=token();check(GateRuntime.choose(p,accept,"accept")&&!GateRuntime.choose(p,accept,"accept"),"Actual Residents bridge accept nonce failed or replayed");check(!Missions.accept(p,"field_patrol"),"Flat contract bypassed active gate exclusivity");
        moveToArrival();String branch=token();check(!GateRuntime.choose(p,branch,"unoffered")&&GateRuntime.choose(p,branch,"read")&&!GateRuntime.choose(p,branch,"read"),"Branch nonce accepted authority not offered or replayed");
        click(1,false);check(s().getInteger("mask")==0&&s().getString("stage").equals("markers"),"Wrong order did not reset checkpoint");click(0,false);check(s().getInteger("mask")==1,"First native marker not observed");
        click(1,true);check(s().getInteger("mask")==1,"Final canceled marker advanced");click(1,false);step();
        EntityLivingBase first=(EntityLivingBase)GateRuntime.actor(p,0),second=(EntityLivingBase)GateRuntime.actor(p,1);check(first!=null&&second!=null&&GateRuntime.owned(p,first),"Registered native Encounters group absent");cleanup.add(first);cleanup.add(second);
        float ownedHealth=first.getHealth();first.hurtResistantTime=0;check(!first.attackEntityFrom(DamageSource.causePlayerDamage(outsider),1000)&&first.getHealth()==ownedHealth,"Unrelated native player bypassed actor owner/run authority");
        if(first instanceof EntityLiving){((EntityLiving)first).setAttackTarget(outsider);check(((EntityLiving)first).getAttackTarget()==null,"Owned actor targeted an unrelated player");((EntityLiving)first).setAttackTarget(p);}
        gate.death=first;float health=first.getHealth();first.hurtResistantTime=0;first.attackEntityFrom(DamageSource.causePlayerDamage(p),1000);first.setHealth(health);GateEvents.flush();Missions.flushDeaths(p.getServer());
        check(s().getInteger("killed")==0,"Canceled native death credited");gate.death=null;kill(first);check(s().getInteger("killed")==1,"Accepted native death not credited once");
        reconnect();check(!s().hasUniqueId("actor0")&&!s().hasUniqueId("actor1")&&s().getInteger("killed")==1,"Real logout/reload lost checkpoint kills or retained UUID pair");step();
        check(GateRuntime.actor(p,0)==null&&GateRuntime.actor(p,1)!=null,"Resume respawned a credited opponent");EntityLivingBase remaining=(EntityLivingBase)GateRuntime.actor(p,1);cleanup.add(remaining);kill(remaining);step();
        check(s().getString("stage").equals("hold"),"Actual group deaths did not reach defense");timedStages();p.setPosition(740.5,80,740.5);supplies();
        for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i).isEmpty())p.inventory.mainInventory.set(i,new ItemStack(Blocks.STONE,64));
        int xp=PlayerState.data(p).getInteger("xp"),merit=PlayerState.data(p).getInteger("merit"),pending=RewardLedger.get(p).pendingCount(p.getUniqueID());
        check(GateRuntime.claim(p,giver)&&PlayerState.data(p).getInteger("xp")==xp+100&&PlayerState.data(p).getInteger("merit")==merit+3,"First claim did not settle exact nonitem rewards");
        check(RewardLedger.get(p).pendingCount(p.getUniqueID())==pending+2&&PlayerState.data(p).getBoolean("expedition_native_entry"),"Full-inventory first reward lost durable outbox/unlock");
        check(!GateRuntime.claim(p,giver)&&PlayerState.data(p).getInteger("xp")==xp+100,"Claim replay duplicated XP");check(!GateRuntime.accept(p,"gate_native_validation",giver),"Repeat cooldown bypassed");
        reconnect();check(PlayerState.data(p).getInteger("xp")==xp+100&&s().getCompoundTag("cleared").getBoolean("gate_native_validation"),"Paid first reward/clear was not durable after actual player reload");
        check(!GateRuntime.claim(p,giver)&&PlayerState.data(p).getInteger("xp")==xp+100,"Reload replay duplicated paid first reward");
        repeatRoute();
        // Exact placement identity survives player reload and refuses old active runs.
        world.getWorldInfo().setWorldTotalTime(Adventures.now(p)+6001);p.setPosition(740.5,80,740.5);check(GateRuntime.accept(p,"gate_native_validation",giver),"Identity fixture could not start active route");
        String contentIdentity=s().getString("content");long activeRun=s().getLong("run");reconnect();
        JsonObject changed=new JsonParser().parse(new String(Files.readAllBytes(sites.toPath()),StandardCharsets.UTF_8)).getAsJsonObject();changed.getAsJsonObject("actor_spawns").getAsJsonObject("gate_native_validation/escort").getAsJsonArray("pos").set(0,new JsonPrimitive(747));write(sites,changed.toString());
        check(GateRuntime.load(definitions,sites).startsWith("Loaded"),"Changed measured sites did not reload");NBTTagCompound oldRun=s().copy();step();
        check(!GateRuntime.readable(p)&&s().getString("content").equals(contentIdentity)&&s().getLong("run")==activeRun&&s().equals(oldRun),"Changed sites adopted/mutated the old active run");
        check(!GateRuntime.accept(p,"gate_native_validation",giver)&&!GateRuntime.claim(p,giver),"Mismatched content identity allowed claim or acceptance");
        // Restore exact definitions before the separate future-schema nonmutation check.
        changed.getAsJsonObject("actor_spawns").getAsJsonObject("gate_native_validation/escort").getAsJsonArray("pos").set(0,new JsonPrimitive(746));write(sites,changed.toString());check(GateRuntime.load(definitions,sites).startsWith("Loaded")&&GateRuntime.readable(p),"Exact original sites failed to recover compatible saved route");
        NBTTagCompound future=s();future.setInteger("schema",99);future.setString("futureOpaque","keep");NBTTagCompound before=future.copy();GateRuntime.tick(p);GateRuntime.suspend(p);
        check(future.equals(before),"Future player schema was modified");
    }
    private static void write(File file,String text)throws IOException{Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8));}
    private void restore(){
        try{if(GateRuntime.readable(p))GateRuntime.suspend(p);for(Entity actor:cleanup){actor.setDead();actor.world.removeEntity(actor);}field(GateRuntime.class,"book").set(null,previousBook);
            p.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",original);
            for(int i=0;i<inventory.size();i++)p.inventory.mainInventory.set(i,inventory.get(i));p.setPositionAndRotation(originalPosition.x,originalPosition.y,originalPosition.z,yaw,pitch);Adventures.save(p);server.getPlayerList().playerLoggedOut(p);
        }catch(Exception e){throw new IllegalStateException("Gate native fixture cleanup failed",e);}
    }
    private static final class Learner extends EntityPlayerMP {
        Learner(MinecraftServer server,WorldServer world,UUID uuid){super(server,world,new GameProfile(uuid,"Gate0313"),new PlayerInteractionManager(world));}
        @Override public void sendMessage(ITextComponent message){}
        @Override public void sendStatusMessage(ITextComponent message,boolean bar){}
    }
    private static final class Quiet extends NetHandlerPlayServer {
        Quiet(MinecraftServer server,EntityPlayerMP player){super(server,network(),player);}
        private static NetworkManager network(){NetworkManager network=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(network);return network;}
        @Override public void sendPacket(Packet<?> packet){}
    }
    public static final class Gate {
        BlockPos block;EntityLivingBase death;Entity giver,escort;PlayerInteractEvent.EntityInteract escortEvent;boolean cancelInteract;
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void observeEscort(PlayerInteractEvent.EntityInteract e){if(e.getTarget()==escort)escortEvent=e;}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void interact(PlayerInteractEvent.EntityInteract e){if(cancelInteract&&e.getTarget()==giver)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void click(PlayerInteractEvent.RightClickBlock e){if(block!=null&&block.equals(e.getPos()))e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void death(LivingDeathEvent e){if(e.getEntityLiving()==death)e.setCanceled(true);}
    }
}
