package village.vale;

import net.minecraft.entity.Entity;
import village.vale.bridge.MoveCost;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

public final class Summons {
    public static final String[] KEYS={"reedling","cairnling","lantern"};
    public static final int[] LEVELS={3,7,11};
    private static final java.util.Set<java.util.UUID> STARTING=new java.util.HashSet<>();
    private Summons(){}
    public static boolean activate(EntityPlayerMP p,int kind){
        if(kind<0||kind>=3||!p.isEntityAlive()||p.isSpectator()||STARTING.contains(p.getUniqueID()))return false;
        NBTTagCompound s=PlayerState.data(p);long now=Adventures.now(p);
        if(s.getLong("summonEnd")>now){dismiss(p);return Adventures.tell(p,"Companion dismissed.",true);}
        if(Combat.fullSet(p)==null||PlayerState.level(p)<LEVELS[kind])return Adventures.tell(p,"This companion needs a matching issued set and level "+LEVELS[kind]+".");
        if(now<s.getLong("summonReady"))return Adventures.tell(p,"Companion cooldown is 45s.");
        MoveCost.Ticket payment=MoveCost.reserve(p,35);if(payment==null)return Adventures.tell(p,"Summoning needs 35 chakra units.");
        long oldReady=s.getLong("summonReady");s.setLong("summonReady",now+900);STARTING.add(p.getUniqueID());
        try{
            if(!payment.valid()||PlayerState.data(p)!=s||Combat.fullSet(p)==null)return false;
            if(!spawn(p,kind,1200,payment))return Adventures.tell(p,"Need clear loaded space beside you.");
            payment.commit();try{Adventures.gesture(p);}catch(RuntimeException|LinkageError cosmeticFailure){}return true;
        }finally{if(payment.pending()&&PlayerState.data(p)==s&&s.getLong("summonReady")==now+900)s.setLong("summonReady",oldReady);STARTING.remove(p.getUniqueID());payment.close();}
    }
    public static boolean decoy(EntityPlayerMP p){
        if(PlayerState.data(p).getLong("summonEnd")>Adventures.now(p))return false;return spawn(p,3,100);
    }
    static boolean decoy(EntityPlayerMP p,MoveCost.Ticket payment){if(PlayerState.data(p).getLong("summonEnd")>Adventures.now(p))return false;return spawn(p,3,100,payment);}
    static boolean spawn(EntityPlayerMP p,int kind,int ticks){return spawn(p,kind,ticks,null);}
    static boolean spawn(EntityPlayerMP p,int kind,int ticks,MoveCost.Ticket payment){
        Vec3d side=new Vec3d(-p.getLookVec().z,0,p.getLookVec().x).normalize().scale(1.5);
        EchoCompanion entity=new EchoCompanion(p.world);entity.setPosition(p.posX+side.x,p.posY,p.posZ+side.z);
        NBTTagCompound s=PlayerState.data(p);long generation=s.getLong("summonGeneration")+1;
        entity.configure(p,kind,generation,Adventures.now(p)+ticks);
        if(!p.world.isBlockLoaded(entity.getPosition())||!p.world.getWorldBorder().contains(entity.getPosition())||!p.world.getCollisionBoxes(entity,entity.getEntityBoundingBox()).isEmpty()||p.world.containsAnyLiquid(entity.getEntityBoundingBox()))return false;
        if(payment!=null&&!payment.valid())return false;
        if(!p.world.spawnEntity(entity))return false;
        if(entity.isDead||payment!=null&&!payment.valid()){entity.setDead();entity.world.removeEntity(entity);return false;}
        if(payment!=null)payment.commit(); // admitted live owned entity before NBT/cosmetic callbacks
        s.setLong("summonGeneration",generation);s.setUniqueId("summonId",entity.getUniqueID());s.setLong("summonEnd",Adventures.now(p)+ticks);s.setLong("summonDrain",Adventures.now(p));s.setInteger("summonDimension",p.dimension);s.setLong("lastCombat",Adventures.now(p));return true;
    }
    public static void dismiss(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);if(s.hasUniqueId("summonId")){Entity e=((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId"));if(e instanceof EchoCompanion)e.setDead();}
        s.setLong("summonGeneration",s.getLong("summonGeneration")+1);s.removeTag("summonIdMost");s.removeTag("summonIdLeast");s.removeTag("summonId");s.setLong("summonEnd",0);s.removeTag("summonDrain");
        HudNetwork.dirty(p);
    }
    public static void tick(EntityPlayerMP p){
        NBTTagCompound s=PlayerState.data(p);if(s.getLong("summonEnd")==0&&!s.hasUniqueId("summonId"))return;
        Entity companion=s.hasUniqueId("summonId")?((net.minecraft.world.WorldServer)p.world).getEntityFromUuid(s.getUniqueId("summonId")):null;
        if(!(companion instanceof EchoCompanion)||!companion.isEntityAlive()){dismiss(p);return;}
        if(!p.isEntityAlive()||s.getInteger("summonDimension")!=p.dimension||Adventures.now(p)>=s.getLong("summonEnd")||Combat.fullSet(p)==null){dismiss(p);return;}
        long now=Adventures.now(p);
        if(!s.hasKey("summonDrain",4)||s.getLong("summonDrain")<0||s.getLong("summonDrain")>now+20)s.setLong("summonDrain",now);
        long due=s.getLong("summonDrain");if(now<due||MoveCost.busy(p))return;
        int drains=(int)Math.min(60,1+(now-due)/20);
        MoveCost.Ticket upkeep=MoveCost.reserve(p,drains);if(upkeep==null){if(!MoveCost.busy(p))dismiss(p);return;}
        try{if(PlayerState.data(p)!=s||s.getLong("summonEnd")<=now||companion.isDead)return;if(!upkeep.commit()){dismiss(p);return;}s.setLong("summonDrain",due+drains*20L);s.setLong("lastCombat",now);}finally{upkeep.close();}
    }
}
