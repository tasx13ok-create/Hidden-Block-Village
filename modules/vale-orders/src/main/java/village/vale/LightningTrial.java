package village.vale;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;

/** Short optional power-kit trial. Three run-bound opponents; no mandatory full academy. */
public final class LightningTrial {
    public static final String MISSION="lightning_trial", OWNER="valeLightningOwner", RUN="valeLightningRun";
    private LightningTrial(){}
    public static boolean canAccept(EntityPlayerMP p){return AcademyCampaign.eligible(p)&&AcademyCampaign.state(p).getInteger("stage")>=2&&LightningKit.readable(LightningKit.state(p));}
    public static boolean begin(EntityPlayerMP p){
        if(!canAccept(p))return Adventures.tell(p,"Choose your starter nature at Aki first; then try this optional power trial.");
        if(!MISSION.equals(PlayerState.data(p).getString("mission"))&&!Missions.accept(p,MISSION))return false;
        acquire(p);p.sendMessage(new TextComponentString("Aki: Equip the issued Lightning armor. Use /vale arts to activate Lightning Chakra Armor, Lightning Step, Lariat and Thunder Counter. Go to the far courtyard and defeat your three challengers. Claim to learn Guillotine Drop. Your original nature stays unchanged."));return true;
    }
    public static void acquire(EntityPlayerMP p){
        NBTTagCompound s=LightningKit.state(p);if(!LightningKit.readable(s))return;
        s.setBoolean("unlocked",true);Adventures.save(p);String receipt="lightning-kit/"+p.getUniqueID()+"/";
        for(String part:new String[]{"helmet","chestplate","leggings","boots","saber"})RewardLedger.get(p).issue(p,"storm_"+part,1,receipt+part);
        RewardLedger.get(p).issue(p,"technique_codex",1,receipt+"codex");HudNetwork.dirty(p);
    }
    private static boolean runMatches(EntityPlayerMP p,EntityLivingBase actor,long run){return actor instanceof FieldRogue&&actor.world==p.world&&p.dimension==0&&MISSION.equals(PlayerState.data(p).getString("mission"))&&PlayerState.data(p).getLong("missionRun")==run&&actor.getEntityData().hasUniqueId(OWNER)&&actor.getEntityData().getUniqueId(OWNER).equals(p.getUniqueID())&&actor.getEntityData().getLong(RUN)==run;}
    public static boolean matches(EntityPlayerMP p,EntityLivingBase actor){
        NBTTagCompound s=LightningKit.state(p);long run=PlayerState.data(p).getLong("missionRun");if(!LightningKit.readable(s)||s.getLong("trialRun")!=run||!runMatches(p,actor,run))return false;
        for(int i=0;i<3;i++){String key="trialActor"+i;if(s.hasUniqueId(key)&&s.getUniqueId(key).equals(actor.getUniqueID()))return true;}return false;
    }
    public static void claimed(EntityPlayerMP p){NBTTagCompound s=LightningKit.state(p);if(LightningKit.readable(s)){s.setBoolean("mastered",true);p.sendMessage(new TextComponentString("Lightning trial mastered: Guillotine Drop is now available in your technique codex and action bar."));HudNetwork.dirty(p);}}
    public static void tick(EntityPlayerMP p){
        if(!LightningKit.readable(LightningKit.state(p)))return;
        if(!MISSION.equals(PlayerState.data(p).getString("mission"))||!p.isEntityAlive()||p.dimension!=0){clear(p);return;}
        AcademyCourse course=AcademyCourse.get(p.getServer().getWorld(0));
        if(!course.active||!course.near(p,16,34,80)){clear(p);return;}
        if(!course.near(p,16,34,16)){if(p.ticksExisted%40==0)p.sendStatusMessage(new TextComponentString("Lightning Trial: far courtyard "+course.at(16,34)+" | "+Math.round(Math.sqrt(p.getDistanceSq(course.at(16,34))))+" blocks"),true);return;}
        prepare(p,course);
    }
    public static boolean prepare(EntityPlayerMP p,AcademyCourse course){
        if(!canAccept(p)||!MISSION.equals(PlayerState.data(p).getString("mission"))||p.world.getDifficulty()==EnumDifficulty.PEACEFUL||!p.world.isAreaLoaded(course.at(16,34),12))return false;
        NBTTagCompound s=LightningKit.state(p);long run=PlayerState.data(p).getLong("missionRun");
        if(s.getLong("trialRun")!=run){clear(p);s.setLong("trialRun",run);}
        int live=0,remaining=3-PlayerState.data(p).getInteger("missionProgress");
        for(int i=0;i<3;i++){
            String key="trialActor"+i;
            if(s.hasUniqueId(key)){Entity actor=p.getServerWorld().getEntityFromUuid(s.getUniqueId(key));if(actor instanceof EntityLivingBase&&actor.isEntityAlive()&&matches(p,(EntityLivingBase)actor))live++;else AcademyProgress.forgetActor(s,key);}
        }
        for(int i=0;i<3&&live<remaining;i++){
            if(!canAccept(p)||PlayerState.data(p).getLong("missionRun")!=run||s!=LightningKit.state(p)||!MISSION.equals(PlayerState.data(p).getString("mission")))return false;
            String key="trialActor"+i;if(s.hasUniqueId(key))continue;BlockPos at=course.at(7+i*7,36);
            FieldRogue actor=new FieldRogue(p.world);actor.configure(i);actor.setPosition(at.getX()+.5,at.getY(),at.getZ()+.5);actor.enablePersistence();actor.setHomePosAndDistance(at,14);
            actor.setCustomNameTag(new String[]{"Thunder Trial - Skirmisher","Thunder Trial - Binder","Thunder Trial - Sentinel"}[i]);actor.setAlwaysRenderNameTag(true);
            actor.getEntityData().setUniqueId(OWNER,p.getUniqueID());actor.getEntityData().setLong(RUN,run);
            if(!p.world.getCollisionBoxes(actor,actor.getEntityBoundingBox()).isEmpty()||!p.world.checkNoEntityCollision(actor.getEntityBoundingBox(),actor)||!p.world.spawnEntity(actor))return false;
            if(!actor.isEntityAlive()||!runMatches(p,actor,run)||!canAccept(p)||s!=LightningKit.state(p)){actor.setDead();actor.world.removeEntity(actor);return false;}
            s.setUniqueId(key,actor.getUniqueID());live++;
        }
        Adventures.save(p);return true;
    }
    public static void clear(EntityPlayerMP p){
        NBTTagCompound s=LightningKit.state(p);if(!LightningKit.readable(s))return;WorldServer world=p.getServer().getWorld(0);
        for(int i=0;i<3;i++){String key="trialActor"+i;if(s.hasUniqueId(key)){
            Entity actor=world.getEntityFromUuid(s.getUniqueId(key));
            if(actor!=null&&actor.getEntityData().hasUniqueId(OWNER)&&actor.getEntityData().getUniqueId(OWNER).equals(p.getUniqueID())){actor.setDead();world.removeEntity(actor);}
            AcademyProgress.forgetActor(s,key);
        }}
    }
    public static boolean actorValid(EntityLivingBase actor){
        if(!actor.getEntityData().hasUniqueId(OWNER))return true;
        EntityPlayerMP p=actor.getServer().getPlayerList().getPlayerByUUID(actor.getEntityData().getUniqueId(OWNER));
        if(p==null||p.dimension!=0||!p.isEntityAlive()||!matches(p,actor)||!LightningKit.readable(LightningKit.state(p)))return false;
        NBTTagCompound s=LightningKit.state(p);for(int i=0;i<3;i++){String key="trialActor"+i;if(s.hasUniqueId(key)&&s.getUniqueId(key).equals(actor.getUniqueID()))return true;}return false;
    }
}
