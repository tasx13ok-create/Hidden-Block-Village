package village.vale;

import net.minecraft.nbt.NBTTagCompound;

public final class AcademyChecks {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        NBTTagCompound s=AcademyProgress.fresh();
        check(AcademyProgress.readable(s),"Fresh schema");
        check(!AcademyProgress.advance(s,1),"Cannot skip enrollment");
        check(AcademyProgress.advance(s,0),"Enrollment");
        check(!AcademyProgress.advance(s,0),"Cannot replay enrollment");
        check(AcademyProgress.advance(s,1),"Nature selection");
        check(!AcademyProgress.marker(s,1,true),"Ordered markers");
        check(!AcademyProgress.marker(s,0,false),"Teleport is not walking");
        for(int i=0;i<3;i++)check(AcademyProgress.marker(s,i,true),"Walk marker "+i);
        check(!AcademyProgress.marker(s,2,true),"No marker replay");
        s=s.copy();check(AcademyProgress.ready(s),"Route survives NBT copy");
        check(AcademyProgress.advance(s,2),"Route claim");
        check(!AcademyProgress.defense(s,3),"Only observed defense flags");
        for(int flag:new int[]{1,2,4}){check(AcademyProgress.defense(s,flag),"Defense "+flag);check(!AcademyProgress.defense(s,flag),"No duplicate defense");}
        check(AcademyProgress.ready(s),"All defenses");check(AcademyProgress.advance(s,3),"Defense claim");
        for(int i=0;i<3;i++)check(AcademyProgress.art(s),"Art "+i);
        check(!AcademyProgress.art(s),"Art capped");check(AcademyProgress.ready(s),"Art ready");check(AcademyProgress.advance(s,4),"Art claim");
        check(!AcademyProgress.ready(s),"Must craft a ration");s.setBoolean("craftedRation",true);check(AcademyProgress.ready(s),"Crafted ration");
        check(AcademyProgress.advance(s,5),"Basics complete");check(!AcademyProgress.advance(s,6),"Cannot replay completion");
        s.setInteger("schema",999);check(!AcademyProgress.advance(s,0)&&!AcademyProgress.readable(s),"Future player schema preserved");
        AcademyCourse course=new AcademyCourse();NBTTagCompound future=new NBTTagCompound();future.setInteger("schema",999);future.setString("future","preserve");course.readFromNBT(future);
        check(course.readOnly&&!course.active&&course.writeToNBT(new NBTTagCompound()).equals(future),"Future world schema preserved");
        for(String key:new String[]{"target","ambush0","ambush1"}){NBTTagCompound actor=new NBTTagCompound();actor.setUniqueId(key,java.util.UUID.randomUUID());actor.setInteger("stage",4);AcademyProgress.forgetActor(actor,key);check(!actor.hasUniqueId(key)&&actor.getSize()==1&&actor.getInteger("stage")==4,"UUID pair cleanup "+key);}
        System.out.println("Academy progress checks passed: "+assertions);
    }
}
