package village.residents;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Bounded edits to existing owned actors only. No missing UUID replacement or forced chunk loads. */
public final class ResidentAuthoring {
    private ResidentAuthoring(){}
    public static EntityResident loaded(WorldServer world,ResidentRole role){
        ResidentsData data=ResidentsData.get(world);if(data.slots.isReadOnly())return null;
        UUID id=data.slots.uuid(role);Entity e=id==null?null:world.getEntityFromUuid(id);
        return e instanceof EntityResident&&e.isEntityAlive()&&((EntityResident)e).interactiveRole()==role?(EntityResident)e:null;
    }
    public static boolean move(WorldServer world,ResidentRole role,BlockPos feet){
        EntityResident actor=loaded(world,role);
        if(actor==null||feet.getY()<1||feet.getY()>253||Math.abs((long)feet.getX())>29999998||Math.abs((long)feet.getZ())>29999998)return false;
        if(!world.isAreaLoaded(feet.add(-1,-1,-1),feet.add(1,2,1))||!world.getBlockState(feet.down()).isFullCube())return false;
        AxisAlignedBB box=new AxisAlignedBB(feet.getX()+.2,feet.getY(),feet.getZ()+.2,feet.getX()+.8,feet.getY()+1.8,feet.getZ()+.8);
        if(!world.getCollisionBoxes(actor,box).isEmpty()||world.containsAnyLiquid(box)||!world.checkNoEntityCollision(box,actor))return false;
        ResidentsData data=ResidentsData.get(world);if(!data.slots.relocate(role,actor.getUniqueID(),world.provider.getDimension(),feet))return false;
        actor.setPosition(feet.getX()+.5,feet.getY(),feet.getZ()+.5);data.markDirty();return true;
    }
    public static boolean title(WorldServer world,ResidentRole role,String title){
        if(title==null||title.trim().isEmpty()||title.length()>64)return false;
        for(char c:title.toCharArray())if(c<32||c==127)return false;
        EntityResident actor=loaded(world,role);if(actor==null)return false;
        actor.setCustomNameTag(title);actor.setAlwaysRenderNameTag(true);return true;
    }
}
