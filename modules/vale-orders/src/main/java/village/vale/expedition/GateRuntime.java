package village.vale.expedition;

import com.google.gson.*;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import village.vale.*;

/** Original gate runtime. This draft stays disabled until explicit measured sites exist. */
public final class GateRuntime {
    public static final String OWNER="valeGateOwner",RUN="valeGateRun",STAGE="valeGateStage",SLOT="valeGateSlot";
    private static GateBook book;private static long generation;
    private static final Map<UUID,Session> SESSIONS=new LinkedHashMap<>();
    private static final Set<UUID> BUSY=new HashSet<>();
    private static final Set<UUID> SETTLING=new HashSet<>();
    private static boolean skiffRegistered;
    private static final class Session {
        final UUID giver;final String resident,page,token,stage;final long at,run,generation;final int dimension;final net.minecraft.world.World world;final NBTTagCompound account;final double x,y,z;
        final Map<String,JsonObject> choices=new LinkedHashMap<>();final Map<String,String> branches=new LinkedHashMap<>();
        boolean abandon;
        Session(EntityPlayerMP p,Entity giver,String resident,String page){this.giver=giver.getUniqueID();this.resident=resident;this.page=page;token=UUID.randomUUID().toString();at=now(p);run=state(p).getLong("run");stage=state(p).getString("stage");generation=GateRuntime.generation;dimension=p.dimension;world=p.world;account=PlayerState.data(p);x=p.posX;y=p.posY;z=p.posZ;}
    }
    private GateRuntime(){}
    /** Root supplies a stable unused entity ID during preInit; no guessed ID. */
    public static void registerSkiff(Object modInstance,int entityId){
        if(skiffRegistered||entityId<0)throw new IllegalStateException("Invalid/repeated skiff registration");
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID,"expedition_skiff"),GateSkiff.class,"expedition_skiff",entityId,modInstance,64,3,true);skiffRegistered=true;
    }
    public static String load(File content,File sites){
        try{GateBook next=GateBook.load(content,sites);
            net.minecraft.server.MinecraftServer server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            if(server!=null&&book!=null)for(EntityPlayerMP player:new ArrayList<>(server.getPlayerList().getPlayers()))if(hasActive(player))suspend(player);
            book=next;generation++;SESSIONS.clear();return "Loaded "+next.missions.size()+" authored gates; disabled sites remain disabled.";}
        catch(Exception e){return "Gate definitions unchanged: "+e.getMessage();}
    }
    public static NBTTagCompound state(EntityPlayerMP p){
        NBTTagCompound root=PlayerState.data(p);
        if(!root.hasKey("Gates")){if(book==null)return new NBTTagCompound();NBTTagCompound fresh=new NBTTagCompound();fresh.setInteger("schema",1);fresh.setString("content",book.version);root.setTag("Gates",fresh);}
        NBTTagCompound state=root.getCompoundTag("Gates");
        // Only dormant schema1 accounts may adopt the next definitions. Active
        // runs, actors and pending rewards retain their exact old identity.
        if(book!=null&&state.getInteger("schema")==1&&!state.getString("content").equals(book.version)&&state.getString("mission").isEmpty()&&!state.hasKey("pendingMission")){
            boolean actors=false;for(int i=0;i<9;i++)actors|=state.hasUniqueId("actor"+i);
            if(!actors)state.setString("content",book.version);
        }
        return state;
    }
    public static boolean readable(EntityPlayerMP p){if(book==null)return false;NBTTagCompound s=state(p);return s.getInteger("schema")==GateRules.SCHEMA&&s.getString("content").equals(book.version);}
    private static boolean eligible(EntityPlayerMP p){return p!=null&&!p.world.isRemote&&p.isEntityAlive()&&!p.isSpectator()&&!p.capabilities.isCreativeMode&&readable(p);}
    private static long now(EntityPlayerMP p){return Adventures.now(p);}
    private static GateBook.Mission mission(EntityPlayerMP p){return book==null?null:book.missions.get(state(p).getString("mission"));}
    private static GateBook.Stage stage(EntityPlayerMP p){GateBook.Mission m=mission(p);return m==null?null:m.stages.get(state(p).getString("stage"));}
    private static boolean near(EntityPlayerMP p,GateBook.Point at,double radius){return at!=null&&p.dimension==at.dimension&&p.getDistanceSq(at.x+.5,at.y,at.z+.5)<=radius*radius;}
    private static String giverId(EntityPlayerMP p,Entity actor){
        if(!eligible(p)||actor==null||actor.world!=p.world||actor.isDead||actor instanceof EntityLivingBase&&!((EntityLivingBase)actor).isEntityAlive()||p.getDistanceSq(actor)>36||!p.canEntityBeSeen(actor))return null;
        for(Map.Entry<String,GateBook.Giver> entry:book.givers.entrySet())if(entry.getValue().uuid.equals(actor.getUniqueID())&&near(p,entry.getValue().point,8)&&actor.getDistanceSq(entry.getValue().point.x+.5,entry.getValue().point.y,entry.getValue().point.z+.5)<=16)return entry.getKey();
        return null;
    }
    private static void tell(EntityPlayerMP p,String text){p.sendMessage(new TextComponentString(text));}
    private static boolean deny(EntityPlayerMP p,String text){tell(p,text);return false;}
    /** Generic settled-contract hook: call before the existing Missions.claim save. */
    public static void contractCompleted(EntityPlayerMP p,String key,String receipt){
        if(!Missions.ALL.containsKey(key)||receipt==null||receipt.length()>160)return;
        NBTTagCompound root=PlayerState.data(p),history=root.getCompoundTag("completedContracts");
        if(root.hasKey("completedContracts")&&(!root.hasKey("completedContracts",10)||history.getInteger("schema")!=1))return;
        history.setInteger("schema",1);NBTTagCompound claims=history.getCompoundTag("claims");
        if(!claims.hasKey(key)&&claims.getSize()>=64)return;claims.setString(key,receipt);history.setTag("claims",claims);root.setTag("completedContracts",history);
    }
    /** Only called from an already validated resident interaction/nonce dispatcher. */
    public static boolean accept(EntityPlayerMP p,String key,Entity giver){
        if(!eligible(p)||BUSY.contains(p.getUniqueID()))return false;GateBook.Mission m=book.missions.get(key);String rid=giverId(p,giver);
        if(m==null||!m.giver.equals(rid))return false;if(!m.disabled.isEmpty())return deny(p,"Expedition unavailable: "+m.disabled);
        for(GateBook.Stage stage:m.stages.values())if(stage.type.equals("protect_actor")&&!skiffRegistered)return deny(p,"Expedition unavailable: owned skiff entity registration has not been installed.");
        NBTTagCompound s=state(p);if(!s.getString("mission").isEmpty())return m.id.equals(s.getString("mission"))?open(p,giver):deny(p,"Finish or abandon your current expedition first.");
        if(!PlayerState.data(p).getString("mission").isEmpty())return deny(p,"Finish your active field contract before starting a gate expedition.");
        if(PlayerState.level(p)<m.level)return deny(p,"This gate requires level "+m.level+".");
        NBTTagCompound history=PlayerState.data(p).getCompoundTag("completedContracts");
        for(String prerequisite:m.prerequisites)if(history.getInteger("schema")!=1||history.getCompoundTag("claims").getString(prerequisite).isEmpty())return deny(p,"Complete and claim "+prerequisite+" first. Older characters need a reviewed history migration.");
        if(now(p)<s.getCompoundTag("cooldowns").getLong(key)||s.hasKey("pendingMission"))return deny(p,"This gate is resting or its previous reward is still settling.");
        long run=s.getLong("run");if(run<0||run==Long.MAX_VALUE)return false;
        s.setLong("run",run+1);s.setString("mission",key);s.setBoolean("done",false);s.removeTag("branch");enter(p,m.start);return true;
    }
    public static boolean open(EntityPlayerMP p,Entity actor){
        String rid=giverId(p,actor);if(rid==null||!book.dialogues.containsKey(rid))return false;
        JsonObject dialogue=book.dialogues.get(rid);boolean cleared=false;for(GateBook.Mission m:book.missions.values())if(m.giver.equals(rid)&&state(p).getCompoundTag("cleared").getBoolean(m.id))cleared=true;
        return page(p,actor,rid,GateBook.id(dialogue,cleared?"after_clear_start":"start"));
    }
    private static boolean page(EntityPlayerMP p,Entity actor,String rid,String page){
        if(!rid.equals(giverId(p,actor)))return false;JsonObject chosen=null;
        for(JsonElement e:GateBook.array(book.dialogues.get(rid),"pages",32))if(GateBook.id(e.getAsJsonObject(),"id").equals(page))chosen=e.getAsJsonObject();if(chosen==null)return false;
        Session session=new Session(p,actor,rid,page);put(p,session);
        for(JsonElement line:GateBook.array(chosen,"lines",12))tell(p,line.getAsString());
        for(JsonElement e:GateBook.array(chosen,"choices",6)){JsonObject c=e.getAsJsonObject();String id=GateBook.id(c,"id");session.choices.put(id,c);button(p,GateBook.text(c,"label",64),session.token,id);}
        GateBook.Mission m=mission(p);GateBook.Stage stage=stage(p);
        if(m!=null&&m.giver.equals(rid)&&stage!=null&&stage.type.equals("choose_branch"))for(String id:stage.branches.keySet()){session.branches.put(id,stage.branches.get(id));button(p,stage.labels.get(id),session.token,id);}
        if(m!=null&&m.giver.equals(rid)){session.abandon=true;button(p,"Abandon this expedition",session.token,"_abandon_gate");}
        return true;
    }
    private static void put(EntityPlayerMP p,Session s){SESSIONS.remove(p.getUniqueID());while(SESSIONS.size()>=256)SESSIONS.remove(SESSIONS.keySet().iterator().next());SESSIONS.put(p.getUniqueID(),s);}
    private static void button(EntityPlayerMP p,String label,String token,String choice){TextComponentString line=new TextComponentString("["+label+"]");line.getStyle().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/valegatechoose "+token+" "+choice));p.sendMessage(line);}
    public static boolean choose(EntityPlayerMP p,String token,String choice){
        Session session=SESSIONS.get(p.getUniqueID());if(session==null||!session.token.equals(token)||!eligible(p)||BUSY.contains(p.getUniqueID()))return false;
        NBTTagCompound s=state(p);boolean context=session.world==p.world&&session.dimension==p.dimension&&session.account==PlayerState.data(p)&&session.generation==generation;
        // Dialogue can precede accepting run1, so its stable empty run0 is valid only for dialogue choices.
        boolean valid=session.run>0?GateRules.session(session.at,now(p),session.run,s.getLong("run"),session.stage,s.getString("stage"),context)
            :now(p)>=session.at&&now(p)-session.at<=200&&context&&s.getLong("run")==0&&session.stage.equals(s.getString("stage"));
        Entity giver=p.getServerWorld().getEntityFromUuid(session.giver);
        boolean spatial=session.resident.isEmpty()?p.getDistanceSq(session.x,session.y,session.z)<=36:session.resident.equals(giverId(p,giver));if(!valid||!spatial)return false;
        JsonObject selected=session.choices.get(choice);String branch=session.branches.get(choice);boolean abandon=session.abandon&&choice.equals("_abandon_gate");if(selected==null&&branch==null&&!abandon)return false;
        SESSIONS.remove(p.getUniqueID());BUSY.add(p.getUniqueID());
        try{
            if(abandon){clearActors(p);for(String key:new String[]{"mission","stage","branch","done","mask","killed","observed","started","lastObserved","sampleTick"})s.removeTag(key);Adventures.save(p);tell(p,"Expedition abandoned; no reward claimed.");return true;}
            if(branch!=null){GateBook.Stage current=stage(p);if(current==null||!current.type.equals("choose_branch")||!branch.equals(current.branches.get(choice)))return false;s.setString("branch",choice);enter(p,branch);return true;}
            String action=GateBook.text(selected,"action",32);
            if(action.equals("PAGE"))return page(p,giver,session.resident,GateBook.id(selected,"target"));
            // BUSY guards outer dispatch; these two methods perform no callback before their own mutation.
            BUSY.remove(p.getUniqueID());
            if(action.equals("ACCEPT_CONTRACT"))return accept(p,GateBook.id(selected,"target"),giver);
            return action.equals("CLAIM_CONTRACT")&&claim(p,giver);
        }finally{BUSY.remove(p.getUniqueID());}
    }
    private static void enter(EntityPlayerMP p,String next){
        clearActors(p);NBTTagCompound s=state(p);SESSIONS.remove(p.getUniqueID());s.setString("stage",next==null?"":next);s.setBoolean("done",next==null);
        for(String key:new String[]{"mask","killed","observed","started","lastObserved","sampleTick","actorSampleTick"})s.removeTag(key);
        Adventures.save(p);GateBook.Stage stage=stage(p);tell(p,stage==null?"Gate objective complete. Return to its keeper and claim.":stage.text);
        if(stage!=null&&stage.type.equals("choose_branch")){Session session=new Session(p,p,"","");put(p,session);for(String id:stage.branches.keySet()){session.branches.put(id,stage.branches.get(id));button(p,stage.labels.get(id),session.token,id);}}
    }
    private static void fail(EntityPlayerMP p,GateBook.Stage stage,String reason){tell(p,reason+" Retry the checkpoint.");enter(p,stage.retry);}
    public static boolean marker(EntityPlayerMP p,BlockPos pos){
        if(!eligible(p)||BUSY.contains(p.getUniqueID()))return false;GateBook.Mission m=mission(p);GateBook.Stage stage=stage(p);
        if(m==null||!m.disabled.isEmpty()||stage==null||!(stage.type.equals("interact_marker")||stage.type.equals("interact_sequence")))return false;
        if(p.getDistanceSqToCenter(pos)>25||!p.world.isBlockLoaded(pos))return false;
        RayTraceResult trace=p.world.rayTraceBlocks(p.getPositionEyes(1),new Vec3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5),false,true,false);
        if(trace!=null&&!pos.equals(trace.getBlockPos()))return false;
        if(p.world.isAirBlock(pos))return false;
        List<String> markers=stage.markers();int index=-1;for(int i=0;i<markers.size();i++){GateBook.Point point=book.markerBlocks.get(markers.get(i));if(point!=null&&point.dimension==p.dimension&&pos.equals(new BlockPos(point.x,point.y,point.z)))index=i;}
        if(index<0)return false;NBTTagCompound s=state(p);if(!s.hasKey("started"))s.setLong("started",now(p));
        int mask=GateRules.interact(s.getInteger("mask"),index,markers.size(),stage.objective.has("ordered")&&stage.objective.get("ordered").getAsBoolean());
        if(mask==-1){fail(p,stage,"Wrong marker order.");return false;}if(mask<0)return false;s.setInteger("mask",mask);
        if(GateRules.sequenceDone(mask,markers.size()))enter(p,stage.next);else Adventures.save(p);return true;
    }
    public static void tick(EntityPlayerMP p){
        settle(p);if(!eligible(p))return;GateBook.Mission m=mission(p);GateBook.Stage stage=stage(p);if(m==null||stage==null||!m.disabled.isEmpty())return;
        NBTTagCompound s=state(p);long now=now(p);int sampleDimension=s.getInteger("sampleDimension");
        double dx=p.posX-s.getDouble("sampleX"),dy=p.posY-s.getDouble("sampleY"),dz=p.posZ-s.getDouble("sampleZ");
        boolean walking=GateRules.walked(s.getLong("sampleTick"),now,sampleDimension,p.dimension,dx*dx+dy*dy+dz*dz,p.capabilities.isFlying,p.isRiding(),now<=s.getLong("teleportBlockedUntil"));
        if(stage.type.equals("reach_marker")){
            GateBook.Point point=book.anchors.get(stage.string("anchor"));
            if(point!=null&&point.dimension==p.dimension){double bx=s.getDouble("sampleX")-point.x-.5,by=s.getDouble("sampleY")-point.y,bz=s.getDouble("sampleZ")-point.z-.5;
                if(GateRules.crossed(bx*bx+by*by+bz*bz,p.getDistanceSq(point.x+.5,point.y,point.z+.5),stage.number("radius",3),walking)){s.setString("checkpoint",stage.string("anchor"));enter(p,stage.next);}}
        }else if(stage.type.equals("defeat_group")||stage.type.equals("defend_point")||stage.type.equals("escort_actor")||stage.type.equals("protect_actor"))actorStage(p,m,stage,walking);
        int limit=stage.number("time_limit_seconds",0);if(limit>0&&s.hasKey("started")&&GateRules.timedOut(s.getLong("started"),now,limit)&&stage==stage(p))fail(p,stage,"The objective timer expired.");
        s.setLong("sampleTick",now);s.setInteger("sampleDimension",p.dimension);s.setDouble("sampleX",p.posX);s.setDouble("sampleY",p.posY);s.setDouble("sampleZ",p.posZ);
    }
    private static void actorStage(EntityPlayerMP p,GateBook.Mission m,GateBook.Stage stage,boolean walking){
        NBTTagCompound s=state(p);GateBook.Point origin=stage.type.equals("escort_actor")||stage.type.equals("protect_actor")?book.actorSpawns.get(m.id+"/"+stage.id):book.anchors.get(stage.string("anchor"));
        if(!near(p,origin,32)){s.setInteger("observed",0);s.removeTag("lastObserved");return;}
        List<String> profiles=stage.profiles();boolean friendly=stage.type.equals("escort_actor")||stage.type.equals("protect_actor");int total=friendly?1:profiles.size();
        // Approach the measured actor start before beginning its timed stage.
        // Spawning at32 then failing the12-block guard caused retry/save spam.
        if(friendly&&!s.hasUniqueId("actor0")&&!near(p,origin,8))return;
        for(int slot=0;slot<total;slot++){
            if((s.getInteger("killed")&(1<<slot))!=0)continue;
            Entity actor=actor(p,slot);if(actor!=null&&!actor.isDead&&(!(actor instanceof EntityLivingBase)||((EntityLivingBase)actor).isEntityAlive())&&owned(p,actor))continue;
            if(s.hasUniqueId("actor"+slot)&&GateEvents.pendingDeath(s.getUniqueId("actor"+slot)))continue;
            if(actor!=null||s.hasUniqueId("actor"+slot)){if(actor!=null&&owned(p,actor)){actor.setDead();actor.world.removeEntity(actor);}forget(s,"actor"+slot);if(friendly){fail(p,stage,"Your protected actor was lost.");return;}}
            net.minecraft.world.World spawnWorld=p.world;int spawnDimension=p.dimension;NBTTagCompound account=PlayerState.data(p);long run=s.getLong("run");
            GateRules.SpawnStamp stamp=new GateRules.SpawnStamp(spawnWorld,spawnDimension,account,s,run,generation,m.id,stage.id);
            Entity created=friendly?(stage.type.equals("protect_actor")?(skiffRegistered?new GateSkiff(spawnWorld):null):new EntityVillager(spawnWorld)):EncounterBridge.create(spawnWorld,profiles.get(slot));
            if(created==null){tellOnce(p,"Encounter runtime unavailable; checkpoint kept.");return;}
            boolean published=false;
            try{
                if(!spawnContext(p,stamp,origin))return;
                int x=origin.x+(friendly?0:(slot%3-1)*2),z=origin.z+(friendly?0:(slot/3-1)*2);created.setPosition(x+.5,origin.y,z+.5);
                if(!safeSpawn(p,created,stage.type.equals("protect_actor")))return;
                if(created instanceof EntityLiving)((EntityLiving)created).enablePersistence();created.getEntityData().setInteger("valeGateActorSchema",1);created.getEntityData().setUniqueId(OWNER,p.getUniqueID());created.getEntityData().setLong(RUN,run);created.getEntityData().setString(STAGE,stage.id);created.getEntityData().setInteger(SLOT,slot);created.getEntityData().setBoolean("valeGateFriendly",friendly);
                if(!spawnWorld.spawnEntity(created)||created.isDead||created.world!=spawnWorld||!spawnContext(p,stamp,origin))return;
                s.setUniqueId("actor"+slot,created.getUniqueID());s.setInteger("actorDimension",spawnDimension);
                if(created instanceof EntityLiving&&!friendly)((EntityLiving)created).setAttackTarget(p);
                if(created.isDead||!spawnContext(p,stamp,origin))return;
                published=true;Adventures.save(p);
            }finally{
                if(!published){if(s.hasUniqueId("actor"+slot)&&s.getUniqueId("actor"+slot).equals(created.getUniqueID()))forget(s,"actor"+slot);created.setDead();created.world.removeEntity(created);if(created.world!=spawnWorld)spawnWorld.removeEntity(created);}
            }
        }
        if(!s.hasKey("started"))s.setLong("started",now(p));
        if(stage.type.equals("defeat_group")){if(s.getInteger("killed")==((1<<total)-1))enter(p,stage.next);return;}
        Entity protectedActor=friendly?actor(p,0):null;
        if(friendly&&(protectedActor==null||protectedActor.isDead||protectedActor instanceof EntityLivingBase&&!((EntityLivingBase)protectedActor).isEntityAlive())){fail(p,stage,"Your protected actor was downed.");return;}
        boolean holding=friendly?p.getDistanceSq(protectedActor)<=144:near(p,origin,6);
        if(!holding){fail(p,stage,"The objective was left unguarded.");return;}
        if(stage.type.equals("escort_actor")){
            EntityLiving escort=(EntityLiving)protectedActor;if(walking&&p.getDistanceSq(escort)>4&&p.getDistanceSq(escort)<=64)escort.getNavigator().tryMoveToEntityLiving(p,.9);else escort.getNavigator().clearPath();
            int observed=GateRules.observedTicks(s.getInteger("observed"),s.getLong("lastObserved"),now(p),walking);s.setInteger("observed",observed);s.setLong("lastObserved",now(p));
            GateBook.Point destination=book.anchors.get(stage.string("to"));
            if(observed>=20&&near(p,destination,4)&&escort.getDistanceSq(destination.x+.5,destination.y,destination.z+.5)<=9){s.setString("checkpoint",stage.string("to"));enter(p,stage.next);return;}
            if(GateRules.timedOut(s.getLong("started"),now(p),stage.number("max_seconds",120)))fail(p,stage,"The escort took too long.");
        }else{
            int observed=GateRules.observedTicks(s.getInteger("observed"),s.getLong("lastObserved"),now(p),holding);s.setInteger("observed",observed);s.setLong("lastObserved",now(p));
            if(observed>=stage.number("seconds",45)*20)enter(p,stage.next);
        }
    }
    private static boolean spawnContext(EntityPlayerMP p,GateRules.SpawnStamp stamp,GateBook.Point origin){
        NBTTagCompound s=state(p);return eligible(p)&&origin!=null&&stamp.valid(p.world,p.dimension,PlayerState.data(p),s,s.getLong("run"),generation,s.getString("mission"),s.getString("stage"),p.getDistanceSq(origin.x+.5,origin.y,origin.z+.5));
    }
    private static boolean safeSpawn(EntityPlayerMP p,Entity actor,boolean boat){
        BlockPos at=actor.getPosition();if(at.getY()<1||at.getY()>253||!p.world.isAreaLoaded(at,2)||!p.world.getWorldBorder().contains(at)||!p.world.getCollisionBoxes(actor,actor.getEntityBoundingBox()).isEmpty()||!p.world.checkNoEntityCollision(actor.getEntityBoundingBox(),actor))return false;
        if(boat)return p.world.getBlockState(at).getMaterial()==net.minecraft.block.material.Material.WATER||p.world.getBlockState(at.down()).getMaterial()==net.minecraft.block.material.Material.WATER;
        return p.world.getBlockState(at.down()).isSideSolid(p.world,at.down(),EnumFacing.UP)&&!p.world.containsAnyLiquid(actor.getEntityBoundingBox());
    }
    private static void tellOnce(EntityPlayerMP p,String text){NBTTagCompound s=state(p);if(now(p)>=s.getLong("noticeReady")){tell(p,text);s.setLong("noticeReady",now(p)+200);}}
    static Entity actor(EntityPlayerMP p,int slot){NBTTagCompound s=state(p);WorldServer world=p.getServer().getWorld(s.getInteger("actorDimension"));return world!=null&&s.hasUniqueId("actor"+slot)?world.getEntityFromUuid(s.getUniqueId("actor"+slot)):null;}
    private static boolean ownedIdentity(EntityPlayerMP p,Entity actor){NBTTagCompound a=actor.getEntityData(),s=state(p);int slot=a.getInteger(SLOT);return slot>=0&&slot<9&&a.getInteger("valeGateActorSchema")==1&&a.hasUniqueId(OWNER)&&a.getUniqueId(OWNER).equals(p.getUniqueID())&&a.getLong(RUN)==s.getLong("run")&&a.getString(STAGE).equals(s.getString("stage"))&&s.hasUniqueId("actor"+slot)&&s.getUniqueId("actor"+slot).equals(actor.getUniqueID());}
    public static boolean owned(EntityPlayerMP p,Entity actor){return actor.world==p.world&&ownedIdentity(p,actor);}
    /** Shared Missions.queueDeath hook: these have one objective/reward authority. */
    public static boolean managedActor(Entity actor){NBTTagCompound data=actor.getEntityData();return data.getInteger("valeGateActorSchema")==1&&data.hasUniqueId(OWNER)&&data.getLong(RUN)>0&&data.getInteger(SLOT)>=0&&data.getInteger(SLOT)<9;}
    public static boolean hasActive(EntityPlayerMP p){return readable(p)&&mission(p)!=null;}
    public static void show(EntityPlayerMP p){if(!readable(p))return;GateBook.Mission m=mission(p);GateBook.Stage stage=stage(p);if(m!=null)tell(p,m.title+" | "+(safelyDone(p)?"Return to its keeper to claim.":stage==null?"Checkpoint needs review.":stage.text));}
    private static boolean safelyDone(EntityPlayerMP p){return state(p).getBoolean("done");}
    static boolean skiffDamage(Entity actor,DamageSource source){
        if(actor.world.isRemote||actor.getServer()==null||!actor.getEntityData().hasUniqueId(OWNER))return false;
        EntityPlayerMP owner=actor.getServer().getPlayerList().getPlayerByUUID(actor.getEntityData().getUniqueId(OWNER));Entity attacker=source.getTrueSource();
        return owner!=null&&eligible(owner)&&owned(owner,actor)&&(attacker==null||attacker.getEntityData().getInteger("valeGateActorSchema")==1&&owned(owner,attacker));
    }
    static boolean skiffValid(Entity actor){if(actor.getServer()==null||!actor.getEntityData().hasUniqueId(OWNER))return false;EntityPlayerMP p=actor.getServer().getPlayerList().getPlayerByUUID(actor.getEntityData().getUniqueId(OWNER));return p!=null&&eligible(p)&&owned(p,actor);}
    static boolean kill(EntityPlayerMP p,EntityLivingBase actor,boolean canceled,boolean deathObserved){
        if(!eligible(p))return false;GateBook.Stage stage=stage(p);if(stage==null||!(stage.type.equals("defeat_group")||stage.type.equals("defend_point")))return false;
        NBTTagCompound s=state(p);int slot=actor.getEntityData().getInteger(SLOT),mask=s.getInteger("killed");
        if(!GateRules.validKill(slot,mask,stage.profiles().size(),canceled,actor.getHealth()<=0,deathObserved,owned(p,actor)))return false;
        s.setInteger("killed",mask|(1<<slot));forget(s,"actor"+slot);Adventures.save(p);return true;
    }
    public static void clearActors(EntityPlayerMP p){NBTTagCompound s=state(p);if(!readable(p))return;
        for(int i=0;i<9;i++){Entity actor=actor(p,i);if(actor!=null&&ownedIdentity(p,actor)){actor.setDead();actor.world.removeEntity(actor);}forget(s,"actor"+i);}}
    static void forget(NBTTagCompound s,String key){s.removeTag(key);s.removeTag(key+"Most");s.removeTag(key+"Least");}
    public static void suspend(EntityPlayerMP p){SESSIONS.remove(p.getUniqueID());if(!readable(p))return;clearActors(p);NBTTagCompound s=state(p);for(String key:new String[]{"observed","started","lastObserved","sampleTick"})s.removeTag(key);Adventures.save(p);}
    public static void teleported(EntityPlayerMP p){if(readable(p)){state(p).setLong("teleportBlockedUntil",now(p)+4);state(p).removeTag("sampleTick");}}
    public static boolean claim(EntityPlayerMP p,Entity giver){
        if(!eligible(p)||BUSY.contains(p.getUniqueID()))return false;GateBook.Mission m=mission(p);if(m==null||!m.disabled.isEmpty()||!m.giver.equals(giverId(p,giver)))return false;NBTTagCompound s=state(p);
        GateBook.Stage current=stage(p);if(current!=null&&current.type.equals("deliver_items")){
            int needed=current.number("count",4),available=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Items.BREAD&&!stack.hasTagCompound()&&stack.getMetadata()==0)available+=stack.getCount();
            if(available<needed)return deny(p,"Bring "+needed+" plain bread to the keeper.");
            for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Items.BREAD&&!stack.hasTagCompound()&&stack.getMetadata()==0){int used=Math.min(needed,stack.getCount());stack.shrink(used);needed-=used;if(needed==0)break;}
            enter(p,current.next);return true;
        }
        if(!s.getBoolean("done")||!GateRules.receipt(m.id,s.getLong("run")))return deny(p,"Finish the current gate objective first.");
        BUSY.add(p.getUniqueID());try{
            boolean first=!s.getCompoundTag("cleared").getBoolean(m.id);JsonObject reward=first?m.first:m.repeat;
            s.setString("pendingMission",m.id);s.setLong("pendingRun",s.getLong("run"));s.setBoolean("pendingFirst",first);
            NBTTagCompound cleared=s.getCompoundTag("cleared");cleared.setBoolean(m.id,true);s.setTag("cleared",cleared);NBTTagCompound cds=s.getCompoundTag("cooldowns");cds.setLong(m.id,now(p)+m.cooldown);s.setTag("cooldowns",cds);
            PlayerState.addXp(p,GateBook.integer(reward,"xp",0,500));PlayerState.data(p).setInteger("merit",Math.min(1000000,PlayerState.data(p).getInteger("merit")+GateBook.integer(reward,"merit",0,4)));
            if(first){for(JsonElement unlock:GateBook.array(reward,"unlocks",4))PlayerState.data(p).setBoolean(unlock.getAsString(),true);PlayerState.data(p).setBoolean(GateBook.id(reward,"lesson_preview"),true);}
            clearActors(p);s.removeTag("mission");s.removeTag("stage");s.removeTag("done");SESSIONS.remove(p.getUniqueID());Adventures.save(p);settle(p);return true;
        }finally{BUSY.remove(p.getUniqueID());}
    }
    private static void settle(EntityPlayerMP p){
        if(!readable(p)||SETTLING.contains(p.getUniqueID()))return;NBTTagCompound s=state(p);if(!s.hasKey("pendingMission"))return;GateBook.Mission m=book.missions.get(s.getString("pendingMission"));long run=s.getLong("pendingRun");if(m==null||!GateRules.receipt(m.id,run))return;
        SETTLING.add(p.getUniqueID());try{
        JsonObject reward=s.getBoolean("pendingFirst")?m.first:m.repeat;int index=0;
        for(JsonElement e:GateBook.array(reward,"ledger_items",4)){JsonObject item=e.getAsJsonObject();RewardLedger.get(p).issue(p,GateBook.id(item,"key"),GateBook.integer(item,"count",1,2),"gate/"+p.getUniqueID()+"/"+m.id+"/"+run+"/item/"+(index++));}
        s.removeTag("pendingMission");s.removeTag("pendingRun");s.removeTag("pendingFirst");Adventures.save(p);
        }finally{SETTLING.remove(p.getUniqueID());}
    }
    /** Fixed optional API only: Core keeps no compile dependency on Encounters. */
    private static final class EncounterBridge {
        static Entity create(net.minecraft.world.World world,String key){
            if(key==null||!key.matches("mob_[a-z0-9_]{1,60}"))return null;
            try{Class<?> type=Class.forName("village.encounters.Encounters");Method create=type.getMethod("create",net.minecraft.world.World.class,String.class);Object actor=create.invoke(null,world,key);return actor instanceof EntityLiving?(Entity)actor:null;}
            catch(ReflectiveOperationException|LinkageError unavailable){return null;}
        }
    }
}
