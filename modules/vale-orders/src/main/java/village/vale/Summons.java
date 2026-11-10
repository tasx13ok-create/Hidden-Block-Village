package village.vale;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

public final class Summons {
    public static final String[] KEYS={"reedling","cairnling","lantern"};
    public static final int[] LEVELS={3,7,11};
    private Summons(){}
    public static boolean activate(EntityPlayerMP p,int kind){
        if(kind<0||kind>=3||!p.isEntityAlive()||p.isSpectator())return false;
        NBTTagCompound s=PlayerState.data(p);if(s.getLong("summonEnd")>Adventures.now(p)){dismiss(p);return Adventures.tell(p,"Companion dismissed.",true);}
        if(Combat.fullSet(p)==null||PlayerState.level(p)<LEVELS[kind])return Adventures.tell(p,"This companion needs a matching issued set and level "+LEVELS[kind]+".");
        if(Adventures.now(p)<s.getLong("summonReady")||PlayerState.resolve(p)<35)return Adventures.tell(p,"Companion cooldown is 45s; summoning needs 35 resolve.");
        if(!spawn(p,kind,1200))return Adventures.tell(p,"Need clear loaded space beside you.");
        PlayerState.setResolve(p,PlayerState.resolve(p)-35);s.setLong("summonReady",Adventures.now(p)+900);Adventures.gesture(p);return true;
    }
    public static boolean decoy(EntityPlayerMP p){
        if(PlayerState.data(p).getLong("summonEnd")>Adventures.now(p))return false;return spawn(p,3,100);
    }
    static boolean spawn(EntityPlayerMP p,int kind,int ticks){
        Vec3d side=new Vec3d(-p.getLookVec().z,0,p.getLookVec().x).normalize().scale(1.5);
        EchoCompanion entity=new EchoCompanion(p.world);entity.setPosition(p.posX+side.x,p.posY,p.posZ+side.z);
        NBTTagCompound s=PlayerState.data(p);long generation=s.getLong("summonGeneration")+1;
        entity.configure(p,kind,generation,Adventures.now(p)+ticks);
        if(!p.world.isBlockLoaded(entity.getPosition())||!p.world.getWorldBorder().contains(entity.getPosition())||!p.world.getCollisionBoxes(entity,entity.getEntityBoundingBox()).isEmpty()||p.world.containsAnyLiquid(entity.getEntityBoundingBox()))return false;
        if(!p.world.spawnEntity(entity))return false;
        s.setLong("summonGeneration",generation);s.setUniqueId("summonId",entity.getUniqueID());s.setLong("summonEnd",Adventures.now(p)+ticks);s.setInteger("summonDimension",p.dimension);s.setLong("lastCombat",Adventures.now(p));return true;
    }
    public static void dismiss(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);if(s.hasUniqueId("summonId")){Entity e=((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId"));if(e instanceof EchoCompanion)e.setDead();}
        s.setLong("summonGeneration",s.getLong("summonGeneration")+1);s.removeTag("summonIdMost");s.removeTag("summonIdLeast");s.removeTag("summonId");s.setLong("summonEnd",0);
        HudNetwork.dirty(p);
    }
    public static void tick(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);if(s.getLong("summonEnd")==0&&!s.hasUniqueId("summonId"))return;
        Entity companion=s.hasUniqueId("summonId")?((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId")):null;
        if(!(companion instanceof EchoCompanion)||!companion.isEntityAlive()){dismiss(p);return;}
        if(!p.isEntityAlive()||s.getInteger("summonDimension")!=p.dimension||Adventures.now(p)>=s.getLong("summonEnd")||Combat.fullSet(p)==null||PlayerState.resolve(p)<1){dismiss(p);return;}
        PlayerState.setResolve(p,PlayerState.resolve(p)-1);s.setLong("lastCombat",Adventures.now(p));
    }
}
